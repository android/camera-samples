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
package com.android.camerax.slowmotion

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.HighSpeedVideoSessionConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "CameraXSlowMotion"

@Composable
fun rememberCameraXSlowMotionController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onSessionConfigured: (Range<Int>) -> Unit,
    onUnsupported: () -> Unit,
    onVideoCaptured: (Uri) -> Unit,
): CameraXSlowMotionController {
    val latestOnSessionConfigured by rememberUpdatedState(onSessionConfigured)
    val latestOnUnsupported by rememberUpdatedState(onUnsupported)
    val latestOnVideoCaptured by rememberUpdatedState(onVideoCaptured)
    return remember(context, lifecycleOwner) {
        CameraXSlowMotionController(
            context,
            lifecycleOwner,
            onSessionConfigured = { range -> latestOnSessionConfigured(range) },
            onUnsupported = { latestOnUnsupported() },
            onVideoCaptured = { uri -> latestOnVideoCaptured(uri) },
        )
    }
}

/**
 * Records high-speed (slow-motion) video with the CameraX Recorder. Before binding it asks the back
 * camera for its high-speed capabilities via [Recorder.getHighSpeedVideoCapabilities]; if the device
 * advertises none (typical on emulators) it reports [onUnsupported]. Otherwise it builds a
 * [VideoCapture] from the highest supported quality, picks the highest high-speed frame-rate range
 * the camera offers for the preview + video combination, and binds a [HighSpeedVideoSessionConfig]
 * with `isSlowMotionEnabled = true` so the capture runs at the high frame rate while the encoder
 * writes a normal-rate clip — playing back in slow motion. The saved [Uri] is emitted via
 * [onVideoCaptured].
 */
@Stable
class CameraXSlowMotionController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onSessionConfigured: (Range<Int>) -> Unit,
    private val onUnsupported: () -> Unit,
    private val onVideoCaptured: (Uri) -> Unit,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

    private val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    fun openCamera() {
        providerScope.launch {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider
            try {
                val cameraInfo = cameraSelector.filter(provider.availableCameraInfos).firstOrNull()
                if (cameraInfo == null) {
                    onUnsupported()
                    return@launch
                }

                // High-speed capabilities gate the whole sample.
                val highSpeedCaps = Recorder.getHighSpeedVideoCapabilities(cameraInfo)
                if (highSpeedCaps == null) {
                    onUnsupported()
                    return@launch
                }

                val quality =
                    highSpeedCaps.getSupportedQualities(DynamicRange.SDR).firstOrNull()
                if (quality == null) {
                    onUnsupported()
                    return@launch
                }

                val recorder =
                    Recorder
                        .Builder()
                        .setQualitySelector(QualitySelector.from(quality))
                        .build()
                val capture = VideoCapture.withOutput(recorder)
                videoCapture = capture

                // Probe the high-speed frame-rate ranges the camera offers for this use-case combo
                // and pick the highest (e.g. 120/240 fps) so the slow-motion effect is strongest.
                val probeConfig = HighSpeedVideoSessionConfig(capture, preview)
                val frameRateRange =
                    cameraInfo
                        .getSupportedFrameRateRanges(probeConfig)
                        .maxByOrNull { it.upper }
                if (frameRateRange == null) {
                    onUnsupported()
                    return@launch
                }

                val sessionConfig =
                    HighSpeedVideoSessionConfig(
                        capture,
                        preview,
                        frameRateRange = frameRateRange,
                        isSlowMotionEnabled = true,
                    )
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, sessionConfig)
                onSessionConfigured(frameRateRange)
            } catch (e: Exception) {
                Log.e(TAG, "High-speed session binding failed", e)
                onUnsupported()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context) {
        val capture = videoCapture ?: return
        if (activeRecording != null) return

        val name =
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
        val contentValues =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        val outputOptions =
            MediaStoreOutputOptions
                .Builder(appContext.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

        // High-speed sessions do not support audio capture, so record video only.
        activeRecording =
            capture.output
                .prepareRecording(context, outputOptions)
                .start(ContextCompat.getMainExecutor(appContext)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (!event.hasError()) {
                            onVideoCaptured(event.outputResults.outputUri)
                        } else {
                            Log.e(TAG, "Slow-motion recording failed: ${event.error}")
                        }
                        activeRecording = null
                    }
                }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun closeCamera() {
        stopRecording()
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        providerScope.cancel()
    }
}
