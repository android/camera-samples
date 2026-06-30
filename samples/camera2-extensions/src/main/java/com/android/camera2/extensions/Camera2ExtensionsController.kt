/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.camera2.extensions

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraExtensionSession
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.android.camera.core.camera2.BaseCamera2Controller
import com.android.camera2.extensions.Camera2ExtensionsViewModel.Companion.NO_EXTENSION
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "Camera2ExtensionsController"
private const val PREVIEW_WIDTH = 1920
private const val PREVIEW_HEIGHT = 1080

@Composable
fun rememberCamera2ExtensionsController(
    context: Context,
    onUnsupported: () -> Unit,
    onSupportedExtensions: (List<Int>) -> Unit,
    onPhotoCaptured: (Image, Int) -> Unit,
): Camera2ExtensionsController {
    val latestOnUnsupported by rememberUpdatedState(onUnsupported)
    val latestOnSupportedExtensions by rememberUpdatedState(onSupportedExtensions)
    val latestOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)
    return remember(context) {
        Camera2ExtensionsController(
            context,
            onUnsupported = { latestOnUnsupported() },
            onSupportedExtensions = { latestOnSupportedExtensions(it) },
            onPhotoCaptured = { image, orientation -> latestOnPhotoCaptured(image, orientation) },
        )
    }
}

/**
 * Camera2 vendor-extension controller. The shared open/close/transform/focus plumbing lives in
 * [BaseCamera2Controller]; this class swaps the normal capture session for a framework
 * [CameraExtensionSession] (API 31+) so live preview and still JPEG capture run through the selected
 * vendor extension (HDR, Night, Bokeh, Face Retouch, Auto). Back camera only.
 */
@Stable
class Camera2ExtensionsController(
    context: Context,
    private val onUnsupported: () -> Unit,
    private val onSupportedExtensions: (List<Int>) -> Unit,
    private val onPhotoCaptured: (Image, Int) -> Unit,
) : BaseCamera2Controller(context, isFrontCamera = false) {
    /** Executor for extension-session creation and capture callbacks. */
    private val extensionExecutor: Executor = Executors.newSingleThreadExecutor()

    private var imageReader: ImageReader? = null
    private var extensionSession: CameraExtensionSession? = null
    private var sensorOrientation: Int = 90

    private var isSupported: Boolean = false
    private var supportedExtensions: List<Int> = emptyList()

    /** Currently selected extension; defaults to the first supported one once known. */
    private var currentExtension: Int = NO_EXTENSION

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            isSupported = false
            return
        }
        prepareExtensions()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun prepareExtensions() {
        try {
            val extChars = cameraManager.getCameraExtensionCharacteristics(cameraId)
            val extensions = extChars.supportedExtensions
            if (extensions.isEmpty()) {
                isSupported = false
                return
            }

            isSupported = true
            supportedExtensions = extensions
            if (currentExtension == NO_EXTENSION || currentExtension !in extensions) {
                currentExtension = extensions.first()
            }
            onSupportedExtensions(extensions)

            val stillSize =
                extChars
                    .getExtensionSupportedSizes(currentExtension, ImageFormat.JPEG)
                    .maxByOrNull { it.width.toLong() * it.height }
                    ?: return

            imageReader =
                ImageReader
                    .newInstance(stillSize.width, stillSize.height, ImageFormat.JPEG, 1)
                    .apply {
                        setOnImageAvailableListener({ reader ->
                            reader.acquireLatestImage()?.let { image ->
                                onPhotoCaptured(image, sensorOrientation)
                            }
                        }, backgroundHandler)
                    }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to query extension characteristics", e)
            isSupported = false
        }
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        if (!isSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onUnsupported()
            return
        }
        createExtensionSession(camera, surface)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createExtensionSession(
        camera: CameraDevice,
        previewSurface: Surface,
    ) {
        val reader = imageReader ?: return
        val configuration =
            ExtensionSessionConfiguration(
                currentExtension,
                listOf(
                    OutputConfiguration(previewSurface),
                    OutputConfiguration(reader.surface),
                ),
                extensionExecutor,
                object : CameraExtensionSession.StateCallback() {
                    override fun onConfigured(session: CameraExtensionSession) {
                        if (cameraDevice == null) return
                        extensionSession = session
                        startPreview(previewSurface)
                    }

                    override fun onConfigureFailed(session: CameraExtensionSession) {
                        Log.e(TAG, "Failed to configure extension session")
                    }

                    override fun onClosed(session: CameraExtensionSession) {
                        if (extensionSession === session) extensionSession = null
                    }
                },
            )
        camera.createExtensionSession(configuration)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun startPreview(previewSurface: Surface) {
        try {
            val device = cameraDevice ?: return
            val session = extensionSession ?: return
            val builder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                }
            previewRequestBuilder = builder
            session.setRepeatingRequest(
                builder.build(),
                extensionExecutor,
                object : CameraExtensionSession.ExtensionCaptureCallback() {},
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start extension preview", e)
        }
    }

    /** Capture a still JPEG through the active extension session. */
    fun capturePhoto() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        capturePhotoApi31()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun capturePhotoApi31() {
        try {
            val device = cameraDevice ?: return
            val reader = imageReader ?: return
            val session = extensionSession ?: return

            val builder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                }
            session.capture(
                builder.build(),
                extensionExecutor,
                object : CameraExtensionSession.ExtensionCaptureCallback() {},
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to capture extension still", e)
        }
    }

    /** Switch the active extension, rebuilding the extension session if the camera is open. */
    fun setExtension(extension: Int) {
        if (extension == currentExtension) return
        currentExtension = extension
        if (cameraDevice != null) {
            closeCamera()
            openCamera()
        }
    }

    override fun onCameraClosed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                extensionSession?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Exception while closing extension session", e)
            }
        }
        extensionSession = null
        imageReader?.close()
        imageReader = null
    }

    override fun release() {
        super.release()
        (extensionExecutor as? ExecutorService)?.shutdown()
    }
}
