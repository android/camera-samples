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
package com.android.camerax.ultrahdr

import android.content.ContentValues
import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXUltraHdr"

@Composable
fun rememberCameraXUltraHdrController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onPhotoCaptured: (Uri) -> Unit,
    onUnsupported: () -> Unit,
): CameraXUltraHdrController =
    remember(context, lifecycleOwner, onPhotoCaptured, onUnsupported) {
        CameraXUltraHdrController(context, lifecycleOwner, onPhotoCaptured, onUnsupported)
    }

/**
 * Captures gain-map Ultra HDR JPEGs. After resolving the back camera it checks
 * [ImageCapture.getImageCaptureCapabilities]; if [ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR] is not
 * advertised it reports [onUnsupported] instead of binding. Capture goes straight to MediaStore via
 * [ImageCapture.OutputFileOptions] so CameraX writes the JPEG_R (base image + gain map) untouched,
 * and the saved [Uri] is handed back for the viewer to decode (decoding-and-recompressing would
 * discard the gain map).
 */
@Stable
class CameraXUltraHdrController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onPhotoCaptured: (Uri) -> Unit,
    private val onUnsupported: () -> Unit,
) {
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

    fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                val cameraInfo = selector.filter(provider.availableCameraInfos).firstOrNull()
                if (cameraInfo == null) {
                    onUnsupported()
                    return@addListener
                }
                val capabilities = ImageCapture.getImageCaptureCapabilities(cameraInfo)
                if (!capabilities.supportedOutputFormats
                        .contains(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                ) {
                    onUnsupported()
                    return@addListener
                }

                val capture =
                    ImageCapture
                        .Builder()
                        .setOutputFormat(ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                imageCapture = capture

                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                onUnsupported()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateTargetRotation(rotation: Int) {
        imageCapture?.targetRotation = rotation
        preview.targetRotation = rotation
    }

    fun takePicture() {
        val capture = imageCapture ?: return
        val name = "ULTRAHDR_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US).format(Date())}"
        val contentValues =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }
        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { onPhotoCaptured(it) }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Ultra HDR capture failed: ${exception.message}", exception)
                }
            },
        )
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        cameraExecutor.shutdown()
    }
}
