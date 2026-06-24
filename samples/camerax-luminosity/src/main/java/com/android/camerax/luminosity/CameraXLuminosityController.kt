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
package com.android.camerax.luminosity

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXLuminosity"

@Composable
fun rememberCameraXLuminosityController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onLuma: (luma: Float, fps: Float) -> Unit,
): CameraXLuminosityController =
    remember(context, lifecycleOwner, onLuma) {
        CameraXLuminosityController(context, lifecycleOwner, onLuma)
    }

/**
 * Binds a [Preview] alongside an [ImageAnalysis] use case and reports the average scene luminance of
 * every analysis frame. The analyzer reads the Y (luma) plane of the `YUV_420_888` frame and
 * averages it — the canonical "getting started with `ImageAnalysis`" computation, with no ML model
 * involved. [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] drops stale frames so the analysis never
 * backs up behind the camera.
 */
@Stable
class CameraXLuminosityController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onLuma: (luma: Float, fps: Float) -> Unit,
) {
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var lastFrameTimestamp = 0L
    private var smoothedFps = 0f

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

    private val imageAnalysis =
        ImageAnalysis
            .Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, ::analyze) }

    private fun analyze(imageProxy: ImageProxy) {
        try {
            // Plane 0 of a YUV_420_888 image is the full-resolution luma (Y) channel; its average is
            // a direct measure of how bright the scene is.
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var sum = 0L
            for (b in bytes) {
                sum += b.toInt() and 0xFF
            }
            val luma = if (bytes.isEmpty()) 0f else (sum.toFloat() / bytes.size) / 255f

            val now = System.nanoTime()
            if (lastFrameTimestamp != 0L) {
                val deltaNanos = (now - lastFrameTimestamp).coerceAtLeast(1L)
                val instantFps = 1_000_000_000f / deltaNanos
                smoothedFps =
                    if (smoothedFps == 0f) instantFps else smoothedFps * 0.9f + instantFps * 0.1f
            }
            lastFrameTimestamp = now

            onLuma(luma.coerceIn(0f, 1f), smoothedFps)
        } catch (e: Exception) {
            Log.e(TAG, "Luminosity analysis failed", e)
        } finally {
            imageProxy.close()
        }
    }

    fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        analysisExecutor.shutdown()
    }
}
