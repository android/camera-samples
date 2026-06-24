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
package com.android.camera2.hdrvideo

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.media.MediaCodecInfo
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.android.camera.core.camera2.BaseCamera2Controller
import com.android.camera.core.media.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "Camera2HdrVideoCtrl"
private val RECORDING_SIZE = Size(1920, 1080)
private const val RECORDING_FPS = 30

@Composable
fun rememberCamera2HdrVideoController(
    context: Context,
    onRangesScanned: (List<HdrDynamicRange>) -> Unit,
    onVideoCaptured: (File) -> Unit,
    onRecordingError: () -> Unit,
): Camera2HdrVideoController {
    val coroutineScope = rememberCoroutineScope()
    return remember(context, onRangesScanned, onVideoCaptured, onRecordingError) {
        Camera2HdrVideoController(context, onRangesScanned, onVideoCaptured, onRecordingError, coroutineScope)
    }
}

/**
 * Camera2 10-bit HDR video controller (back camera only). On top of [BaseCamera2Controller] it:
 * - scans `REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES` in [onCameraPrepared] (API 33+) and reports the
 *   advertised HDR profiles,
 * - tags every session output with the selected [android.hardware.camera2.params.DynamicRangeProfiles]
 *   via the base [configureOutput] hook, and
 * - records 10-bit HEVC (`HEVCProfileMain10*`) through [MediaRecorder].
 *
 * 10-bit recording is genuinely device-dependent: some cameras advertise a profile the encoder can't
 * actually honour, so [MediaRecorder.prepare] is wrapped and a failure cleanly aborts the take rather
 * than crashing. A fully robust pipeline would fall back to `MediaCodec` + a muxer; that is out of
 * scope for this sample.
 */
@Stable
class Camera2HdrVideoController(
    context: Context,
    private val onRangesScanned: (List<HdrDynamicRange>) -> Unit,
    private val onVideoCaptured: (File) -> Unit,
    private val onRecordingError: () -> Unit,
    private val coroutineScope: CoroutineScope,
) : BaseCamera2Controller(context, isFrontCamera = false) {
    private var isSupported = false
    private var selectedRange = HdrDynamicRange.SDR
    private var userPicked = false

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoFile: File? = null
    private var previewSurface: Surface? = null

    override val previewSize: Size = RECORDING_SIZE

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            isSupported = false
            onRangesScanned(listOf(HdrDynamicRange.SDR))
            return
        }
        scanProfiles(characteristics)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun scanProfiles(characteristics: CameraCharacteristics) {
        val supported =
            characteristics
                .get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
                ?.supportedProfiles
                ?: emptySet()
        val hdrRanges = HdrDynamicRange.entries.filter { it.isHdr && it.profile in supported }
        isSupported = hdrRanges.isNotEmpty()
        if (!userPicked) {
            selectedRange = hdrRanges.firstOrNull() ?: HdrDynamicRange.SDR
        }
        onRangesScanned(listOf(HdrDynamicRange.SDR) + hdrRanges)
    }

    override fun configureOutput(
        output: OutputConfiguration,
        surface: Surface,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Both the preview and the recorder output must carry the same profile or session config fails.
            output.setDynamicRangeProfile(selectedRange.profile)
        }
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        if (!isSupported) return // Screen shows UnsupportedView; nothing to bind.
        previewSurface = surface
        startPreviewSession(camera, surface)
    }

    override fun onCameraClosed() {
        releaseMediaRecorder()
    }

    /** Switch the dynamic-range profile, rebuilding the session (a full close/open is required). */
    fun updateRange(range: HdrDynamicRange) {
        if (range == selectedRange) return
        userPicked = true
        selectedRange = range
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
        // Back camera only, so the front-mirroring sign is always +1.
        val orientationHint = (sensorRotation - rotationDegrees + 360) % 360

        currentVideoFile = MediaStoreSaver.newVideoFile()

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
                setOutputFile(currentVideoFile!!.absolutePath)
                setVideoEncodingBitRate(20_000_000)
                setVideoFrameRate(RECORDING_FPS)
                setVideoSize(RECORDING_SIZE.width, RECORDING_SIZE.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
                if (selectedRange.isHdr && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // 10-bit HDR requires an HEVC Main10 profile that matches the camera's profile.
                    setVideoEncodingProfileLevel(
                        hevcHdrProfile(),
                        MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51,
                    )
                }
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44100)
                setOrientationHint(orientationHint)
                prepare()
            }
    }

    @SuppressLint("InlinedApi")
    private fun hevcHdrProfile(): Int =
        when (selectedRange) {
            HdrDynamicRange.HDR10 -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            HdrDynamicRange.HDR10_PLUS -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            else -> MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
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
                        abortRecording()
                    }
                }
            } catch (e: Exception) {
                // Most likely a device that advertises the profile but can't encode 10-bit HEVC.
                Log.e(TAG, "Exception starting HDR recording", e)
                abortRecording()
            }
        }
    }

    /** Cleans up a failed take and returns the UI to preview rather than leaving a phantom recording. */
    private fun abortRecording() {
        releaseMediaRecorder()
        currentVideoFile?.delete()
        currentVideoFile = null
        isRecording = false
        val surface = previewSurface
        val camera = cameraDevice
        if (surface != null && camera != null) startPreviewSession(camera, surface)
        onRecordingError()
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

        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            // MediaRecorder throws if stop is called immediately after start.
            Log.e(TAG, "RuntimeException stopping MediaRecorder", e)
            currentVideoFile?.delete()
            currentVideoFile = null
        }

        releaseMediaRecorder()

        val savedFile = currentVideoFile
        if (savedFile != null && savedFile.exists()) {
            MediaStoreSaver.scanFile(context, savedFile, "video/mp4")
            onVideoCaptured(savedFile)
        }
        currentVideoFile = null

        val surface = previewSurface
        val camera = cameraDevice
        if (surface != null && camera != null) startPreviewSession(camera, surface)
    }
}
