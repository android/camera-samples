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
package com.android.camerax.imagelabeling

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "ImageLabelingController"

@Composable
fun rememberCameraXImageLabelingController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onLabels: (List<DetectedLabel>) -> Unit,
): CameraXImageLabelingController =
    remember(context, lifecycleOwner, onLabels) {
        CameraXImageLabelingController(context, lifecycleOwner, onLabels)
    }

@Stable
class CameraXImageLabelingController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onLabels: (List<DetectedLabel>) -> Unit,
) {
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val labeler =
        ImageLabeling.getClient(
            ImageLabelerOptions
                .Builder()
                .setConfidenceThreshold(0.5f)
                .build(),
        )

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                surfaceRequest = request
            }
        }

    private val imageAnalysis =
        ImageAnalysis
            .Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(cameraExecutor, ::analyze)
            }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage =
            InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        labeler
            .process(inputImage)
            .addOnSuccessListener { labels ->
                onLabels(labels.map { DetectedLabel(it.text, it.confidence) })
            }.addOnFailureListener { exc ->
                Log.e(TAG, "Image labeling failed", exc)
            }.addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val cameraSelector =
                CameraSelector
                    .Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        labeler.close()
        cameraExecutor.shutdown()
    }
}
