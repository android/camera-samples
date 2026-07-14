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
import android.graphics.Matrix
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
import android.view.Surface
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
 * captures `YUV_420_888` frames via an [ImageReader], converts each frame to a full-color ARGB
 * [Bitmap] on a background thread, applies the current [ProcessingMode], and publishes the result in
 * [frame] so Compose can render it directly. NORMAL shows the true-color preview and INVERT applies
 * a per-channel color inversion (matching camerax-effects' INVERT ColorMatrix); THRESHOLD/POSTERIZE
 * operate on the same decoded pixels. This mirrors the structure of [the Camera2 controllers]
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

    // The processed frame is in sensor-readout orientation (landscape); these turn it upright.
    @Volatile
    private var sensorOrientation = 90

    @Volatile
    private var displayRotationDegrees = 0

    private var isCameraOpeningOrOpen = false

    /** Reusable pixel buffer so each frame does not allocate a fresh `IntArray`. */
    private val pixels = IntArray(FRAME_WIDTH * FRAME_HEIGHT)

    fun setMode(newMode: ProcessingMode) {
        mode = newMode
    }

    /** Updates the device display rotation (a `Surface.ROTATION_*`) so frames stay upright. */
    fun setDisplayRotation(surfaceRotation: Int) {
        displayRotationDegrees =
            when (surfaceRotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
    }

    /** Resolves the back-facing camera id, or `null` if none is present. */
    private fun findBackCameraId(): String? =
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager
                .getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }

    fun openCamera() {
        // Serialize open/close on the background thread so neither blocks the main thread.
        backgroundHandler.post { openCameraLocked() }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraLocked() {
        if (cameraDevice != null || isCameraOpeningOrOpen) return

        try {
            val id = findBackCameraId() ?: return
            sensorOrientation =
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

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
                decodeFrame(image)
                val source =
                    Bitmap.createBitmap(
                        pixels,
                        FRAME_WIDTH,
                        FRAME_HEIGHT,
                        Bitmap.Config.ARGB_8888,
                    )
                frame = orientUpright(source)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process frame", e)
            } finally {
                image.close()
            }
        }

    /**
     * Decodes the `YUV_420_888` [image] to full-color ARGB into the reusable [pixels] buffer,
     * applying the current [ProcessingMode]. NORMAL is the true-color preview; INVERT applies the
     * per-channel inversion `255 - c` on each of R/G/B (the color-matrix INVERT from camerax-effects)
     * instead of inverting luma only; THRESHOLD and POSTERIZE operate on the decoded pixels.
     */
    private fun decodeFrame(image: android.media.Image) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val currentMode = mode

        var pixelIndex = 0
        for (row in 0 until FRAME_HEIGHT) {
            val yRow = row * yRowStride
            // Chroma is subsampled 2x2, so two luma rows/cols share one chroma sample.
            val uvRow = (row shr 1) * uvRowStride
            for (col in 0 until FRAME_WIDTH) {
                val y = yBuffer.get(yRow + col * yPixelStride).toInt() and 0xFF
                val uvCol = (col shr 1) * uvPixelStride
                val u = (uBuffer.get(uvRow + uvCol).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvRow + uvCol).toInt() and 0xFF) - 128

                // BT.601 full-range YUV -> RGB.
                var r = y + ((91881 * v) shr 16)
                var g = y - ((22554 * u + 46802 * v) shr 16)
                var b = y + ((116130 * u) shr 16)
                r =
                    if (r < 0) {
                        0
                    } else if (r > 255) {
                        255
                    } else {
                        r
                    }
                g =
                    if (g < 0) {
                        0
                    } else if (g > 255) {
                        255
                    } else {
                        g
                    }
                b =
                    if (b < 0) {
                        0
                    } else if (b > 255) {
                        255
                    } else {
                        b
                    }

                when (currentMode) {
                    ProcessingMode.NORMAL -> {}

                    ProcessingMode.INVERT -> {
                        r = 255 - r
                        g = 255 - g
                        b = 255 - b
                    }

                    ProcessingMode.THRESHOLD -> {
                        // Luma threshold rendered as black/white.
                        val on = if (y > 128) 255 else 0
                        r = on
                        g = on
                        b = on
                    }

                    ProcessingMode.POSTERIZE -> {
                        r = (r / 64) * 64
                        g = (g / 64) * 64
                        b = (b / 64) * 64
                    }
                }

                pixels[pixelIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    /** Rotates the landscape sensor frame so it is upright for the current device orientation. */
    private fun orientUpright(bitmap: Bitmap): Bitmap {
        val relativeRotation = (sensorOrientation - displayRotationDegrees + 360) % 360
        if (relativeRotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(relativeRotation.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun closeCamera() {
        backgroundHandler.post { closeCameraLocked() }
    }

    private fun closeCameraLocked() {
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
        // Post the final teardown and let the thread quit on its own; no main-thread join().
        backgroundHandler.post { closeCameraLocked() }
        backgroundThread.quitSafely()
    }
}
