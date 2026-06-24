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
package com.android.camera.core.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.viewfinder.core.TransformationInfo
import androidx.camera.viewfinder.core.ViewfinderSurfaceSession
import androidx.camera.viewfinder.core.camera2.Camera2TransformationInfo
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.Executor

private const val TAG = "BaseCamera2Controller"

/**
 * Captures the Camera2 plumbing shared by every Camera2 sample: a background [HandlerThread],
 * camera discovery/open by lens facing, the viewfinder transformation math, tap-to-focus, and
 * cross-API-level capture-session creation.
 *
 * Subclasses implement [onCameraOpened] to build their preview (and any extra targets such as an
 * `ImageReader` or `MediaRecorder` surface), and may override [onCameraPrepared] / [onCameraClosed]
 * to allocate and release their own resources.
 */
@Stable
abstract class BaseCamera2Controller(
    protected val context: Context,
    val isFrontCamera: Boolean,
) {
    /** The view the camera renders into. Set by the preview composable before [openCamera]. */
    var viewfinder: ViewfinderView? = null

    protected val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    protected val backgroundHandler = Handler(backgroundThread.looper)

    protected var cameraDevice: CameraDevice? = null
    protected var captureSession: CameraCaptureSession? = null
    protected var previewRequestBuilder: CaptureRequest.Builder? = null
    protected var surfaceSession: ViewfinderSurfaceSession? = null

    protected var cameraId: String = ""
    protected var currentCharacteristics: CameraCharacteristics? = null
    protected var currentDisplayRotation: Int = Surface.ROTATION_0

    private var isCameraOpeningOrOpen: Boolean = false

    /** Resolves the camera id matching the requested lens facing, or `null` if none. */
    private fun findCameraId(): String? {
        val targetFacing =
            if (isFrontCamera) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager
                .getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == targetFacing
        }
    }

    /** Updates the viewfinder transform so the preview is oriented for the current rotation. */
    fun updateTransformationInfo(displayRotation: Int) {
        currentDisplayRotation = displayRotation
        val characteristics = currentCharacteristics ?: return
        val currentViewfinder = viewfinder ?: return

        val sensorRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val lensFacing =
            characteristics.get(CameraCharacteristics.LENS_FACING)
                ?: CameraCharacteristics.LENS_FACING_BACK
        val sign = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1

        val rotationDegrees = displayRotation.toRotationDegrees()
        val relativeRotation = (sensorRotation - rotationDegrees * sign + 360) % 360

        val baseInfo = Camera2TransformationInfo.createFromCharacteristics(characteristics)
        currentViewfinder.transformationInfo =
            TransformationInfo(
                sourceRotation = relativeRotation,
                isSourceMirroredHorizontally = baseInfo.isSourceMirroredHorizontally,
                isSourceMirroredVertically = baseInfo.isSourceMirroredVertically,
                cropRectLeft = baseInfo.cropRectLeft,
                cropRectTop = baseInfo.cropRectTop,
                cropRectRight = baseInfo.cropRectRight,
                cropRectBottom = baseInfo.cropRectBottom,
            )
    }

    fun openCamera() {
        // Serialize open/close on the camera background thread so neither ever blocks the main
        // thread (which would jank the enter/exit animation).
        backgroundHandler.post { openCameraLocked() }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraLocked() {
        if (cameraDevice != null || isCameraOpeningOrOpen) return
        val currentViewfinder = viewfinder ?: return

        try {
            val id = findCameraId() ?: return
            cameraId = id
            val characteristics = cameraManager.getCameraCharacteristics(id)
            currentCharacteristics = characteristics
            onCameraPrepared(characteristics)
            isCameraOpeningOrOpen = true

            cameraManager.openCamera(
                id,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        isCameraOpeningOrOpen = false
                        cameraDevice = camera
                        updateTransformationInfo(currentDisplayRotation)
                        onCameraOpened(camera, currentViewfinder)
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

    /** Allocate per-session resources (e.g. an `ImageReader`) before the device is opened. */
    protected open fun onCameraPrepared(characteristics: CameraCharacteristics) {}

    /** Build and start the preview session once the [camera] device is open. */
    protected abstract fun onCameraOpened(
        camera: CameraDevice,
        viewfinder: ViewfinderView,
    )

    /** Release per-session resources allocated in [onCameraPrepared]. */
    protected open fun onCameraClosed() {}

    open fun closeCamera() {
        // Tear down on the camera background thread — closing the device/session on the main thread
        // can block long enough to freeze the screen's exit animation.
        backgroundHandler.post { closeCameraLocked() }
    }

    private fun closeCameraLocked() {
        isCameraOpeningOrOpen = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        surfaceSession?.close()
        surfaceSession = null
        onCameraClosed()
    }

    open fun release() {
        // Post the final teardown, then let the thread drain its queue and quit. We deliberately do
        // NOT join() — blocking the main thread for up to a second is what froze the exit animation.
        backgroundHandler.post { closeCameraLocked() }
        backgroundThread.quitSafely()
    }

    /**
     * Creates a capture session targeting [surfaces], handling the API 28+ [SessionConfiguration]
     * path and the legacy fallback. [onConfigured] runs on the background handler.
     */
    protected fun createCaptureSession(
        camera: CameraDevice,
        surfaces: List<Surface>,
        onConfigured: (CameraCaptureSession) -> Unit,
    ) {
        val stateCallback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    onConfigured(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure capture session")
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val executor = Executor { command -> backgroundHandler.post(command) }
            val outputConfigurations = surfaces.map { OutputConfiguration(it) }
            val sessionConfiguration =
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputConfigurations,
                    executor,
                    stateCallback,
                )
            camera.createCaptureSession(sessionConfiguration)
        } else {
            @Suppress("DEPRECATION")
            camera.createCaptureSession(surfaces, stateCallback, null)
        }
    }

    /** Tap-to-focus: meters and triggers AF/AE at the tapped point. */
    fun focus(
        offset: Offset,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return

        try {
            val characteristics = currentCharacteristics ?: cameraManager.getCameraCharacteristics(cameraId)
            val sensorArraySize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

            val y0 = (offset.x / viewWidth) * sensorArraySize.height()
            val x0 = (offset.y / viewHeight) * sensorArraySize.width()
            val halfTouchWidth = 150
            val halfTouchHeight = 150

            val focusArea =
                Rect(
                    (x0 - halfTouchWidth).toInt().coerceAtLeast(0),
                    (y0 - halfTouchHeight).toInt().coerceAtLeast(0),
                    (x0 + halfTouchWidth).toInt().coerceAtMost(sensorArraySize.width()),
                    (y0 + halfTouchHeight).toInt().coerceAtMost(sensorArraySize.height()),
                )

            try {
                session.stopRepeating()
            } catch (e: CameraAccessException) {
                Log.w(TAG, "Exception while stopping repeating", e)
            }

            val rectangle = MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rectangle))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rectangle))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), null, backgroundHandler)

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Focus error", e)
        }
    }

    protected companion object {
        fun Int.toRotationDegrees(): Int =
            when (this) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
    }
}
