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
package com.android.camera2.hdrviewfinder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.concurrent.Executor

private const val TAG = "Camera2HdrViewfinderCtrl"

private const val FRAME_WIDTH = 640
private const val FRAME_HEIGHT = 480

@Composable
fun rememberCamera2HdrViewfinderController(context: Context): Camera2HdrViewfinderController =
    remember(context) { Camera2HdrViewfinderController(context) }

/**
 * Standalone Camera2 controller for the HDR viewfinder sample.
 *
 * Unlike the other Camera2 samples, this controller does not render into a `ViewfinderView`. It
 * captures `YUV_420_888` frames via an [ImageReader], processes the luma (Y) plane on a background
 * thread according to the current [ProcessingMode], and publishes the result as a [Bitmap] in
 * [frame] so Compose can render it directly. This mirrors the structure of [the Camera2 controllers]
 * (background [HandlerThread], [CameraManager], open by `LENS_FACING_BACK`) but keeps the
 * frame-processing pipeline self-contained.
 */
@Stable
class Camera2HdrViewfinderController(
    private val context: Context,
) {
    /** The latest processed frame; Compose recomposes whenever this changes. */
    var frame: Bitmap? by mutableStateOf(null)
        private set

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val backgroundThread = HandlerThread("HdrViewfinderBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    @Volatile
    private var mode: ProcessingMode = ProcessingMode.NORMAL

    private var isCameraOpeningOrOpen = false

    /** Reusable pixel buffer so each frame does not allocate a fresh `IntArray`. */
    private val pixels = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

    fun setMode(newMode: ProcessingMode) {
        mode = newMode
    }

    /** Resolves the back-facing camera id, or `null` if none is present. */
    private fun findBackCameraId(): String? =
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager
                .getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        if (cameraDevice != null || isCameraOpeningOrOpen) return

        try {
            val id = findBackCameraId() ?: return

            imageReader =
                ImageReader
                    .newInstance(FRAME_WIDTH, FRAME_HEIGHT, ImageFormat.YUV_420_888, 2)
                    .apply { setOnImageAvailableListener(onImageAvailable, backgroundHandler) }

            isCameraOpeningOrOpen = true

            cameraManager.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        isCameraOpeningOrOpen = false
                        cameraDevice = camera
                        startPreviewSession(camera)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        isCameraOpeningOrOpen = false
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(
                        camera: CameraDevice,
                        error: Int,
                    ) {
                        isCameraOpeningOrOpen = false
                        camera.close()
                        cameraDevice = null
                    }
                },
                backgroundHandler,
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun startPreviewSession(camera: CameraDevice) {
        val readerSurface = imageReader?.surface ?: return

        try {
            val requestBuilder =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(readerSurface)
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                    )
                }

            val stateCallback =
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                requestBuilder.build(),
                                null,
                                backgroundHandler,
                            )
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start repeating request", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure capture session")
                    }
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val executor = Executor { command -> backgroundHandler.post(command) }
                val sessionConfiguration =
                    SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(OutputConfiguration(readerSurface)),
                        executor,
                        stateCallback,
                    )
                camera.createCaptureSession(sessionConfiguration)
            } else {
                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(readerSurface), stateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start preview session", e)
        }
    }

    private val onImageAvailable =
        ImageReader.OnImageAvailableListener { reader ->
            val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener
            try {
                val yPlane = image.planes[0]
                val buffer = yPlane.buffer
                val rowStride = yPlane.rowStride
                // pixelStride is 1 for the Y plane in YUV_420_888, but read it for correctness.
                val pixelStride = yPlane.pixelStride
                val currentMode = mode

                var pixelIndex = 0
                for (row in 0 until FRAME_HEIGHT) {
                    // The buffer may be padded: each row occupies rowStride bytes even though only
                    // the first FRAME_WIDTH * pixelStride carry luma samples.
                    var columnOffset = row * rowStride
                    for (col in 0 until FRAME_WIDTH) {
                        val luma = buffer.get(columnOffset).toInt() and 0xFF
                        val value =
                            when (currentMode) {
                                ProcessingMode.NORMAL -> luma
                                ProcessingMode.INVERT -> 255 - luma
                                ProcessingMode.THRESHOLD -> if (luma > 128) 255 else 0
                                ProcessingMode.POSTERIZE -> (luma / 64) * 64
                            }
                        pixels[pixelIndex++] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
                        columnOffset += pixelStride
                    }
                }

                val bitmap =
                    Bitmap.createBitmap(
                        pixels,
                        FRAME_WIDTH,
                        FRAME_HEIGHT,
                        Bitmap.Config.ARGB_8888,
                    )
                frame = bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process frame", e)
            } finally {
                image.close()
            }
        }

    fun closeCamera() {
        isCameraOpeningOrOpen = false
        try {
            captureSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Exception closing capture session", e)
        }
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    fun release() {
        closeCamera()
        backgroundThread.quitSafely()
        try {
            backgroundThread.join(1000)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for background thread to finish", e)
        }
    }
}
