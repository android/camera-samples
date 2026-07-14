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
package com.android.camerax.rawcapture

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXRawCapture"

@Composable
fun rememberCameraXRawCaptureController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onCaptured: (dngUri: Uri, jpegUri: Uri) -> Unit,
    onUnsupported: () -> Unit,
): CameraXRawCaptureController {
    val latestOnCaptured by rememberUpdatedState(onCaptured)
    val latestOnUnsupported by rememberUpdatedState(onUnsupported)
    return remember(context, lifecycleOwner) {
        CameraXRawCaptureController(
            context,
            lifecycleOwner,
            onCaptured = { dng, jpeg -> latestOnCaptured(dng, jpeg) },
            onUnsupported = { latestOnUnsupported() },
        )
    }
}

/**
 * Captures a RAW (DNG) frame and a companion JPEG in one shot with CameraX. After resolving the back
 * camera it checks [ImageCapture.getImageCaptureCapabilities]; if the camera does not advertise
 * [ImageCapture.OUTPUT_FORMAT_RAW_JPEG] it reports [onUnsupported] instead of binding. The
 * [ImageCapture] is built with that output format, and capture uses the two-[OutputFileOptions]
 * `takePicture` overload — its callback fires twice, once for the DNG (`image/x-adobe-dng`) and once
 * for the JPEG (`image/jpeg`), both written to `DCIM/Camera` via MediaStore. Once both URIs arrive
 * they are emitted together through [onCaptured].
 */
@Stable
class CameraXRawCaptureController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onCaptured: (dngUri: Uri, jpegUri: Uri) -> Unit,
    private val onUnsupported: () -> Unit,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // The dual-format callback fires twice; hold each URI until its partner arrives.
    private var pendingDngUri: Uri? = null
    private var pendingJpegUri: Uri? = null

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

                val caps = ImageCapture.getImageCaptureCapabilities(cameraInfo)
                if (!caps.supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)) {
                    onUnsupported()
                    return@launch
                }

                val capture =
                    ImageCapture
                        .Builder()
                        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
                        .build()
                imageCapture = capture

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                onUnsupported()
            }
        }
    }

    fun updateTargetRotation(rotation: Int) {
        imageCapture?.targetRotation = rotation
        preview.targetRotation = rotation
    }

    fun takePicture() {
        val capture = imageCapture ?: return
        pendingDngUri = null
        pendingJpegUri = null

        val timestamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US).format(Date())
        val dngOptions = buildOutputOptions("RAW_$timestamp.dng", "image/x-adobe-dng")
        val jpegOptions = buildOutputOptions("IMG_$timestamp.jpg", "image/jpeg")

        capture.takePicture(
            dngOptions,
            jpegOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri ?: return
                    // The callback fires once per format; route by the saved image format.
                    if (outputFileResults.imageFormat == ImageFormat.JPEG) {
                        pendingJpegUri = uri
                    } else {
                        pendingDngUri = uri
                    }
                    val dng = pendingDngUri
                    val jpeg = pendingJpegUri
                    if (dng != null && jpeg != null) {
                        pendingDngUri = null
                        pendingJpegUri = null
                        onCaptured(dng, jpeg)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "RAW/JPEG capture failed: ${exception.message}", exception)
                }
            },
        )
    }

    private fun buildOutputOptions(
        displayName: String,
        mimeType: String,
    ): ImageCapture.OutputFileOptions {
        val contentValues =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }
        return ImageCapture.OutputFileOptions
            .Builder(
                appContext.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues,
            ).build()
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        providerScope.cancel()
        cameraExecutor.shutdown()
    }
}
