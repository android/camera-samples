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
package com.android.camerax.takeavideo

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
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
import androidx.compose.ui.geometry.Offset
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXVideoController"

@Composable
fun rememberCameraXTakeAVideoController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isFrontCamera: Boolean,
    quality: VideoQuality,
    onVideoCaptured: (Uri) -> Unit,
): CameraXTakeAVideoController {
    val latestOnVideoCaptured by rememberUpdatedState(onVideoCaptured)
    return remember(context, lifecycleOwner, isFrontCamera) {
        CameraXTakeAVideoController(
            context,
            lifecycleOwner,
            isFrontCamera,
            quality,
            onVideoCaptured = { uri -> latestOnVideoCaptured(uri) },
        )
    }
}

@Stable
class CameraXTakeAVideoController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val isFrontCamera: Boolean,
    quality: VideoQuality,
    private val onVideoCaptured: (Uri) -> Unit,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var currentQuality: VideoQuality = quality

    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var videoCapture: VideoCapture<Recorder> = buildVideoCapture(quality)

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                surfaceRequest = request
            }
        }

    private fun buildVideoCapture(quality: VideoQuality): VideoCapture<Recorder> {
        val recorder =
            Recorder
                .Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        quality.quality,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    ),
                ).build()
        return VideoCapture.withOutput(recorder)
    }

    fun openCamera() {
        providerScope.launch {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider
            bindUseCases(provider)
        }
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        val lensFacing =
            if (isFrontCamera) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

        val cameraSelector =
            CameraSelector
                .Builder()
                .requireLensFacing(lensFacing)
                .build()

        try {
            provider.unbindAll()
            val camera =
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture,
                )
            cameraControl = camera.cameraControl
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    fun updateTargetRotation(rotation: Int) {
        videoCapture.targetRotation = rotation
        preview.targetRotation = rotation
    }

    /** Rebinds the camera with a [VideoCapture] configured for the new [quality]. */
    fun updateQuality(quality: VideoQuality) {
        if (quality == currentQuality) return
        currentQuality = quality
        videoCapture = buildVideoCapture(quality)
        cameraProvider?.let { bindUseCases(it) }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context) {
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
                .Builder(
                    appContext.contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                ).setContentValues(contentValues)
                .build()

        activeRecording =
            videoCapture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(appContext)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (!event.hasError()) {
                            onVideoCaptured(event.outputResults.outputUri)
                        } else {
                            Log.e(TAG, "Video recording failed: ${event.error}")
                        }
                        activeRecording = null
                    }
                }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun focus(
        surfaceCoords: Offset,
        width: Float,
        height: Float,
    ) {
        val factory = SurfaceOrientedMeteringPointFactory(width, height)
        val point = factory.createPoint(surfaceCoords.x, surfaceCoords.y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        cameraControl?.startFocusAndMetering(action)
    }

    fun closeCamera() {
        stopRecording()
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        providerScope.cancel()
        cameraExecutor.shutdown()
    }
}
