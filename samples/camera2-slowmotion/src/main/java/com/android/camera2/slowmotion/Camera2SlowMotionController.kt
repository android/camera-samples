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
package com.android.camera2.slowmotion

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.android.camera.core.camera2.BaseCamera2Controller
import com.android.camera.core.media.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

private const val TAG = "Camera2SlowMotionCtrl"

/** Preferred high-speed capture size; falls back to the largest the device offers. */
private val PREFERRED_SIZE = Size(1280, 720)

@Composable
fun rememberCamera2SlowMotionController(
    context: Context,
    onUnsupported: () -> Unit,
    onVideoCaptured: (Uri) -> Unit,
): Camera2SlowMotionController {
    val coroutineScope = rememberCoroutineScope()
    val latestOnUnsupported by rememberUpdatedState(onUnsupported)
    val latestOnVideoCaptured by rememberUpdatedState(onVideoCaptured)
    return remember(context) {
        Camera2SlowMotionController(
            context,
            onUnsupported = { latestOnUnsupported() },
            onVideoCaptured = { latestOnVideoCaptured(it) },
            coroutineScope,
        )
    }
}

/**
 * Camera2 high-speed ("slow motion") video controller. Reuses [BaseCamera2Controller] for the
 * background thread, camera open/close, transform, and tap-to-focus, but replaces the normal
 * capture-session path with a [CameraConstrainedHighSpeedCaptureSession] so the device can run at a
 * fixed high frame rate (e.g. 120/240 fps) that [MediaRecorder] then plays back as slow motion.
 *
 * Back camera only; high-speed recording is not available on emulators, in which case
 * [onUnsupported] fires and the screen shows the unsupported state.
 */
@Stable
class Camera2SlowMotionController(
    context: Context,
    private val onUnsupported: () -> Unit,
    private val onVideoCaptured: (Uri) -> Unit,
    private val coroutineScope: CoroutineScope,
) : BaseCamera2Controller(context, isFrontCamera = false) {
    private var isSupported = true
    private var highSpeedSize: Size = PREFERRED_SIZE
    private var highSpeedFpsRange: Range<Int> = Range(120, 120)

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var pendingVideo: MediaStoreSaver.PendingVideo? = null
    private var previewSurface: Surface? = null

    override val previewSize: Size get() = highSpeedSize

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (map == null) {
            isSupported = false
            return
        }

        val sizes = map.highSpeedVideoSizes
        if (sizes.isEmpty()) {
            isSupported = false
            return
        }

        val selection = selectHighSpeedConfig(map, sizes)
        if (selection == null) {
            isSupported = false
            return
        }

        isSupported = true
        highSpeedSize = selection.first
        highSpeedFpsRange = selection.second
        Log.d(TAG, "High-speed config: size=$highSpeedSize fps=$highSpeedFpsRange")
    }

    /**
     * Picks the highest fixed high-speed fps range (where `upper == lower`, e.g. 120/240) together
     * with a size that supports it. Prefers [PREFERRED_SIZE] when available, otherwise the largest
     * size the chosen range supports.
     */
    private fun selectHighSpeedConfig(
        map: StreamConfigurationMap,
        sizes: Array<Size>,
    ): Pair<Size, Range<Int>>? {
        val fixedRanges =
            map.highSpeedVideoFpsRanges
                .filter { it.upper == it.lower }
                .sortedByDescending { it.upper }
        if (fixedRanges.isEmpty()) return null

        for (range in fixedRanges) {
            val supportedSizes =
                sizes.filter { size ->
                    map.getHighSpeedVideoFpsRangesFor(size).any { it == range }
                }
            if (supportedSizes.isEmpty()) continue

            val chosenSize =
                supportedSizes.firstOrNull { it == PREFERRED_SIZE }
                    ?: supportedSizes.maxByOrNull { it.width.toLong() * it.height }
                    ?: continue
            return chosenSize to range
        }
        return null
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        if (!isSupported) {
            onUnsupported()
            return
        }
        previewSurface = surface
        startPreviewSession(camera, surface)
    }

    override fun onCameraClosed() {
        releaseMediaRecorder()
    }

    private fun startPreviewSession(
        camera: CameraDevice,
        surface: Surface,
    ) {
        previewRequestBuilder =
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, highSpeedFpsRange)
            }
        createHighSpeedSession(camera, listOf(surface)) { session ->
            startHighSpeedRepeating(session)
        }
    }

    /**
     * Creates a constrained high-speed capture session targeting [surfaces], handling the API 28+
     * [SessionConfiguration] path and the legacy fallback. [onConfigured] runs on the background
     * handler.
     */
    private fun createHighSpeedSession(
        camera: CameraDevice,
        surfaces: List<Surface>,
        onConfigured: (CameraConstrainedHighSpeedCaptureSession) -> Unit,
    ) {
        val stateCallback =
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session
                    onConfigured(session as CameraConstrainedHighSpeedCaptureSession)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure high-speed session")
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val executor = Executor { command -> backgroundHandler.post(command) }
            val outputConfigurations = surfaces.map { OutputConfiguration(it) }
            val sessionConfiguration =
                SessionConfiguration(
                    SessionConfiguration.SESSION_HIGH_SPEED,
                    outputConfigurations,
                    executor,
                    stateCallback,
                )
            camera.createCaptureSession(sessionConfiguration)
        } else {
            @Suppress("DEPRECATION")
            camera.createConstrainedHighSpeedCaptureSession(surfaces, stateCallback, backgroundHandler)
        }
    }

    private fun startHighSpeedRepeating(session: CameraConstrainedHighSpeedCaptureSession) {
        try {
            val builder = previewRequestBuilder ?: return
            val requestList = session.createHighSpeedRequestList(builder.build())
            session.setRepeatingBurst(requestList, null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start high-speed repeating burst", e)
        }
    }

    @SuppressLint("InlinedApi")
    private fun setupMediaRecorder() {
        val sensorRotation =
            currentCharacteristics?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val rotationDegrees =
            when (currentDisplayRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        // Back camera only, so the front-camera mirroring sign is always +1.
        val orientationHint = (sensorRotation - rotationDegrees + 360) % 360

        val fps = highSpeedFpsRange.upper

        val pending = MediaStoreSaver.newPendingVideo(context) ?: return
        pendingVideo = pending

        mediaRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                if (pending.usesFileDescriptor) {
                    setOutputFile(pending.fileDescriptor)
                } else {
                    setOutputFile(pending.legacyFilePath)
                }
                setVideoEncodingBitRate(10_000_000)
                setVideoFrameRate(fps)
                // Capturing at the high rate while keeping the playback frame rate lower yields the
                // slow-motion effect.
                setCaptureRate(fps.toDouble())
                setVideoSize(highSpeedSize.width, highSpeedSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
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
        if (isRecording || !isSupported) return
        val camera = cameraDevice ?: return
        val viewfinderSurface = previewSurface ?: return

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
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, highSpeedFpsRange)
                    }

                createHighSpeedSession(camera, listOf(viewfinderSurface, recorderSurface)) { session ->
                    try {
                        val builder = previewRequestBuilder ?: return@createHighSpeedSession
                        val requestList = session.createHighSpeedRequestList(builder.build())
                        session.setRepeatingBurst(requestList, null, backgroundHandler)
                        mediaRecorder?.start()
                        isRecording = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start high-speed recording", e)
                    }
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
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Exception stopping repeating", e)
        }

        var stopFailed = false
        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            // MediaRecorder throws if stop is called immediately after start.
            Log.e(TAG, "RuntimeException stopping MediaRecorder", e)
            stopFailed = true
            pendingVideo?.discard(context)
            pendingVideo = null
        }

        releaseMediaRecorder()

        if (!stopFailed) {
            val pv = pendingVideo
            if (pv != null) {
                pv.finalize(context)
                onVideoCaptured(pv.uri)
            }
            pendingVideo = null
        }

        // Restart the preview session.
        val surface = previewSurface
        val camera = cameraDevice
        if (surface != null && camera != null) startPreviewSession(camera, surface)
    }
}
