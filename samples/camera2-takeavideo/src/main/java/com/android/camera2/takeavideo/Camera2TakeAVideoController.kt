/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.camera2.takeavideo

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
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.viewfinder.core.ScaleType
import androidx.camera.viewfinder.core.TransformationInfo
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.camera.viewfinder.core.ViewfinderSurfaceSession
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "Camera2TakeAVideoCtrl"

@Composable
fun rememberCamera2TakeAVideoController(
    context: Context,
    isFrontCamera: Boolean,
    config: CameraVideoConfig,
    onVideoCaptured: (File) -> Unit
): Camera2TakeAVideoController {
    val coroutineScope = rememberCoroutineScope()
    return remember(context, isFrontCamera, config, onVideoCaptured) {
        Camera2TakeAVideoController(context, isFrontCamera, config, onVideoCaptured, coroutineScope)
    }
}

@Stable
class Camera2TakeAVideoController(
    private val context: Context,
    val isFrontCamera: Boolean,
    var config: CameraVideoConfig,
    private val onVideoCaptured: (File) -> Unit,
    private val coroutineScope: CoroutineScope
) {
    var viewfinder: ViewfinderView? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var cameraId: String = ""
    private var isCameraOpeningOrOpen: Boolean = false
    private var surfaceSession: ViewfinderSurfaceSession? = null

    private var currentCharacteristics: CameraCharacteristics? = null
    private var currentDisplayRotation: Int = Surface.ROTATION_0

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoFile: File? = null

    fun updateTransformationInfo(displayRotation: Int) {
        currentDisplayRotation = displayRotation
        val characteristics = currentCharacteristics ?: return
        val currentViewfinder = viewfinder ?: return

        val sensorRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            ?: CameraCharacteristics.LENS_FACING_BACK
        val sign = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1

        val rotationDegrees = when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        // Calculate the relative rotation considering the framework's automatic display rotation for SurfaceView
        val relativeRotation = (sensorRotation - rotationDegrees * sign + 360) % 360

        val baseInfo = Camera2TransformationInfo.createFromCharacteristics(characteristics)

        currentViewfinder.transformationInfo = TransformationInfo(
            sourceRotation = relativeRotation,
            isSourceMirroredHorizontally = baseInfo.isSourceMirroredHorizontally,
            isSourceMirroredVertically = baseInfo.isSourceMirroredVertically,
            cropRectLeft = baseInfo.cropRectLeft,
            cropRectTop = baseInfo.cropRectTop,
            cropRectRight = baseInfo.cropRectRight,
            cropRectBottom = baseInfo.cropRectBottom
        )
    }

    fun closeCamera() {
        isCameraOpeningOrOpen = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        surfaceSession?.close()
        surfaceSession = null
        releaseMediaRecorder()
    }

    fun updateConfig(newConfig: CameraVideoConfig) {
        if (config == newConfig) return
        config = newConfig
        // If camera is open, restart it to apply the new config (e.g., resolution, fps)
        if (cameraDevice != null) {
            closeCamera()
            openCamera()
        }
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

    @SuppressLint("MissingPermission")
    fun openCamera() {
        if (cameraDevice != null || isCameraOpeningOrOpen) return
        val currentViewfinder = viewfinder ?: return

        // Prefer config camera ID if it matches facing direction, else fallback to searching.
        try {
            var targetCameraId = config.cameraId
            if (targetCameraId.isEmpty()) {
                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    val targetFacing = if (isFrontCamera) {
                        CameraCharacteristics.LENS_FACING_FRONT
                    } else {
                        CameraCharacteristics.LENS_FACING_BACK
                    }

                    if (facing == targetFacing) {
                        targetCameraId = id
                        break
                    }
                }
            }

            if (targetCameraId.isNotEmpty()) {
                cameraId = targetCameraId
                currentCharacteristics = cameraManager.getCameraCharacteristics(targetCameraId)
                isCameraOpeningOrOpen = true

                cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        isCameraOpeningOrOpen = false
                        cameraDevice = camera
                        updateTransformationInfo(currentDisplayRotation)
                        startPreviewSession(camera, currentViewfinder)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        isCameraOpeningOrOpen = false
                        camera.close()
                        cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        isCameraOpeningOrOpen = false
                        camera.close()
                        cameraDevice = null
                    }
                }, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun startPreviewSession(
        camera: CameraDevice,
        viewfinder: ViewfinderView
    ) {
        coroutineScope.launch {
            try {
                val request = ViewfinderSurfaceRequest(config.size.width, config.size.height)
                updateTransformationInfo(currentDisplayRotation)
                viewfinder.scaleType = ScaleType.FILL_CENTER

                surfaceSession?.close()
                val session = viewfinder.requestSurfaceSessionAsync(request).await()
                surfaceSession = session
                val surface = session.surface

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
                    val outputConfigurations = listOf(OutputConfiguration(surface))
                    val sessionConfiguration = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations,
                        executor,
                        stateCallback
                    )
                    camera.createCaptureSession(sessionConfiguration)
                } else {
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(surface), stateCallback, null)
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
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start repeating request", e)
        }
    }

    @SuppressLint("InlinedApi")
    private fun setupMediaRecorder() {
        val sensorRotation = currentCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val rotationDegrees = when (currentDisplayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sign = if (isFrontCamera) -1 else 1
        val orientationHint = (sensorRotation - rotationDegrees * sign + 360) % 360

        val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
        val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
        val sampleDir = File(dcimDir, "camera-samples")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
        }
        currentVideoFile = File(sampleDir, "VID_${sdf.format(Date())}.mp4")

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(currentVideoFile!!.absolutePath)
            setVideoEncodingBitRate(10_000_000)
            setVideoFrameRate(config.fps)
            setVideoSize(config.size.width, config.size.height)
            
            val videoEncoder = when(config.videoCodec) {
                0 -> MediaRecorder.VideoEncoder.HEVC
                1 -> MediaRecorder.VideoEncoder.H264
                2 -> 8 // MediaRecorder.VideoEncoder.AV1 literal to avoid minimum API warning simply
                else -> MediaRecorder.VideoEncoder.H264
            }
            setVideoEncoder(videoEncoder)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(16)
            setAudioSamplingRate(44100)
            setOrientationHint(orientationHint)
            prepare()
        }
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Exception releasing media recorder", e)
        }
        mediaRecorder = null
    }

    fun startRecording() {
        if (isRecording) return
        val camera = cameraDevice ?: return
        val viewfinderSurface = surfaceSession?.surface ?: return

        coroutineScope.launch {
            try {
                captureSession?.close()
                captureSession = null

                setupMediaRecorder()
                val recorderSurface = mediaRecorder?.surface ?: return@launch

                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(viewfinderSurface)
                        addTarget(recorderSurface)
                    }

                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return
                        captureSession = session
                        try {
                            val builder = previewRequestBuilder ?: return
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            
                            mediaRecorder?.start()
                            isRecording = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start recording", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure record session")
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val executor = Executor { command -> backgroundHandler.post(command) }
                    val outputConfigurations = listOf(
                        OutputConfiguration(viewfinderSurface),
                        OutputConfiguration(recorderSurface)
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
                        listOf(viewfinderSurface, recorderSurface),
                        stateCallback,
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting recording", e)
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping repeating", e)
        }

        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            // MediaRecorder throws if stop is called immediately after start
            Log.e(TAG, "RuntimeException stopping MediaRecorder", e)
            currentVideoFile?.delete()
            currentVideoFile = null
        }

        releaseMediaRecorder()

        val savedFile = currentVideoFile
        if (savedFile != null && savedFile.exists()) {
            onVideoCaptured(savedFile)
        }

        currentVideoFile = null

        // Restart preview session
        viewfinder?.let {
            cameraDevice?.let { camera ->
                startPreviewSession(camera, it)
            }
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
