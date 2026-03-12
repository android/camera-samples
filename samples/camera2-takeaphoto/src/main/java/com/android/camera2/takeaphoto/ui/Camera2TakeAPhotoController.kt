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
package com.android.camera2.takeaphoto.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.viewfinder.core.ScaleType
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.camera.viewfinder.core.camera2.Camera2TransformationInfo
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "Camera2TakeAPhotoController"

@Composable
fun rememberCamera2TakeAPhotoController(
    context: Context,
    isFrontCamera: Boolean,
    onPhotoCaptured: (Image, Int) -> Unit
): Camera2TakeAPhotoController {
    val coroutineScope = rememberCoroutineScope()
    return remember(context, isFrontCamera, onPhotoCaptured) {
        Camera2TakeAPhotoController(context, isFrontCamera, onPhotoCaptured, coroutineScope)
    }
}

@Stable
class Camera2TakeAPhotoController(
    private val context: Context,
    val isFrontCamera: Boolean,
    private val onPhotoCaptured: (Image, Int) -> Unit,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        const val PREVIEW_WIDTH = 1920
        const val PREVIEW_HEIGHT = 1080
    }

    var viewfinder: ViewfinderView? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null
    private var cameraId: String = ""
    private var isCameraOpeningOrOpen: Boolean = false

    fun closeCamera() {
        isCameraOpeningOrOpen = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        val currentViewfinder = viewfinder ?: return
        if (isCameraOpeningOrOpen) return
        isCameraOpeningOrOpen = true

        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val targetFacing = if (isFrontCamera) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }

                if (facing == targetFacing) {
                    cameraId = id
                    setupImageReader(characteristics)

                    cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            startPreviewSession(camera, currentViewfinder, characteristics)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            cameraDevice = null
                            isCameraOpeningOrOpen = false
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            cameraDevice = null
                            isCameraOpeningOrOpen = false
                        }
                    }, backgroundHandler)
                    return
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
            isCameraOpeningOrOpen = false
        }
    }

    private fun setupImageReader(characteristics: CameraCharacteristics) {
        imageReader =
            ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.JPEG, 1).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    image?.let {
                        val sOrientation =
                            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                        onPhotoCaptured(it, sOrientation)
                    }
                }, backgroundHandler)
            }
    }

    private fun startPreviewSession(
        camera: CameraDevice,
        viewfinder: ViewfinderView,
        characteristics: CameraCharacteristics
    ) {
        coroutineScope.launch {
            try {
                val request = ViewfinderSurfaceRequest(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                viewfinder.transformationInfo =
                    Camera2TransformationInfo.createFromCharacteristics(characteristics)
                viewfinder.scaleType = ScaleType.FILL_CENTER

                val session = viewfinder.requestSurfaceSessionAsync(request).await()
                val surface = session.surface

                val activeImageReader = imageReader ?: return@launch

                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }

                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        startRepeatingRequest()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure capture session")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val executor = Executor { command -> backgroundHandler.post(command) }
                    val outputConfigurations = listOf(
                        OutputConfiguration(surface),
                        OutputConfiguration(activeImageReader.surface)
                    )
                    val sessionConfiguration = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations,
                        executor,
                        stateCallback
                    )
                    camera.createCaptureSession(sessionConfiguration)
                } else {
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(
                        listOf(surface, activeImageReader.surface),
                        stateCallback,
                        null
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Exception starting preview", e)
            }
        }
    }

    private fun startRepeatingRequest() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return

            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            previewRequest = builder.build()
            session.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start repeating request", e)
        }
    }

    fun takePicture() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val session = captureSession ?: return

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)

            previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)?.let {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, it)
            }

            try {
                session.stopRepeating()
            } catch (e: Exception) {
                Log.w(TAG, "Exception while stopping repeating: ", e)
            }
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        resetFocusAndResumePreview()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to take picture", e)
        }
    }

    private fun resetFocusAndResumePreview() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            session.capture(builder.build(), null, backgroundHandler)

            previewRequest?.let {
                session.setRepeatingRequest(it, null, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to reset focus", e)
        }
    }

    fun focus(offset: Offset, viewWidth: Float, viewHeight: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorArraySize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

            val y0 = (offset.x / viewWidth) * sensorArraySize.height()
            val x0 = (offset.y / viewHeight) * sensorArraySize.width()
            val halfTouchWidth = 150
            val halfTouchHeight = 150

            val focusArea = Rect(
                (x0 - halfTouchWidth).toInt().coerceAtLeast(0),
                (y0 - halfTouchHeight).toInt().coerceAtLeast(0),
                (x0 + halfTouchWidth).toInt().coerceAtMost(sensorArraySize.width()),
                (y0 + halfTouchHeight).toInt().coerceAtMost(sensorArraySize.height())
            )

            try {
                session.stopRepeating()
            } catch (e: Exception) {
                Log.w(TAG, "Exception while stopping repeating: ", e)
            }

            val rectangle = MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rectangle))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rectangle))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), null, backgroundHandler)

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Focus error", e)
        }
    }
}