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
package com.android.camera2.takeavideo

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
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

private const val TAG = "Camera2TakeAVideoCtrl"

@Composable
fun rememberCamera2TakeAVideoController(
    context: Context,
    isFrontCamera: Boolean,
    config: CameraVideoConfig,
    onVideoCaptured: (Uri) -> Unit,
): Camera2TakeAVideoController {
    val coroutineScope = rememberCoroutineScope()
    val latestOnVideoCaptured by rememberUpdatedState(onVideoCaptured)
    return remember(context, isFrontCamera) {
        Camera2TakeAVideoController(
            context,
            isFrontCamera,
            config,
            onVideoCaptured = { latestOnVideoCaptured(it) },
            coroutineScope,
        )
    }
}

/**
 * Camera2 video controller. Shared open/close/focus/transform plumbing lives in
 * [BaseCamera2Controller]; this class adds the [MediaRecorder] recording flow.
 */
@Stable
class Camera2TakeAVideoController(
    context: Context,
    isFrontCamera: Boolean,
    var config: CameraVideoConfig,
    private val onVideoCaptured: (Uri) -> Unit,
    private val coroutineScope: CoroutineScope,
) : BaseCamera2Controller(context, isFrontCamera) {
    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var pendingVideo: MediaStoreSaver.PendingVideo? = null
    private var previewSurface: Surface? = null

    override val previewSize: Size get() = Size(config.size.width, config.size.height)

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        previewSurface = surface
        startPreviewSession(camera, surface)
    }

    override fun onCameraClosed() {
        releaseMediaRecorder()
    }

    /** Restarts the camera if the recording configuration changed. */
    fun updateConfig(newConfig: CameraVideoConfig) {
        if (config == newConfig) return
        config = newConfig
        if (cameraDevice != null) {
            closeCamera()
            openCamera()
        }
    }

    private fun startPreviewSession(
        camera: CameraDevice,
        surface: Surface,
    ) {
        previewRequestBuilder =
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
        createCaptureSession(camera, listOf(surface)) {
            startRepeatingRequest()
        }
    }

    private fun startRepeatingRequest() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return
            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
            )
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start repeating request", e)
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
        val sign = if (isFrontCamera) -1 else 1
        val orientationHint = (sensorRotation - rotationDegrees * sign + 360) % 360

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
                setVideoFrameRate(config.fps)
                setVideoSize(config.size.width, config.size.height)

                val videoEncoder =
                    when (config.videoCodec) {
                        0 -> MediaRecorder.VideoEncoder.HEVC

                        1 -> MediaRecorder.VideoEncoder.H264

                        2 -> 8

                        // MediaRecorder.VideoEncoder.AV1, inlined to avoid the min-API lint.
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
                    }

                createCaptureSession(camera, listOf(viewfinderSurface, recorderSurface)) { session ->
                    try {
                        val builder = previewRequestBuilder ?: return@createCaptureSession
                        builder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                        )
                        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                        isRecording = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start recording", e)
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
