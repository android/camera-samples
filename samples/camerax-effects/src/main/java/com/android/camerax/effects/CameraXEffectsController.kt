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
package com.android.camerax.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXEffects"

@Composable
fun rememberCameraXEffectsController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onFrame: (ImageBitmap) -> Unit,
): CameraXEffectsController {
    val latestOnFrame by rememberUpdatedState(onFrame)
    return remember(context, lifecycleOwner) {
        CameraXEffectsController(
            context,
            lifecycleOwner,
            onFrame = { frame -> latestOnFrame(frame) },
        )
    }
}

/**
 * Applies a live color filter to the camera. Each [ImageAnalysis] frame is decoded to a [Bitmap],
 * oriented upright, recolored through a [ColorMatrix] (grayscale / invert / sepia / etc.) on the GPU
 * via [ColorMatrixColorFilter], and emitted as the displayed [ImageBitmap]. Unlike an overlay, this
 * actually transforms the image, and switching effects is just a state change — there is no
 * persistent buffer to clear. Frames are processed one-at-a-time with
 * [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] so the pipeline never backs up.
 */
@Stable
class CameraXEffectsController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (ImageBitmap) -> Unit,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    @Volatile
    var effect: EffectMode = EffectMode.NONE

    @Volatile
    private var processing = false

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val imageAnalysis =
        ImageAnalysis
            .Builder()
            .setResolutionSelector(
                ResolutionSelector
                    .Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    ).build(),
            ).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, ::analyze) }

    private fun analyze(imageProxy: ImageProxy) {
        if (processing) {
            imageProxy.close()
            return
        }
        processing = true
        try {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val source = imageProxy.toBitmap()
            val upright =
                if (rotation == 0) {
                    source
                } else {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                }

            val output = Bitmap.createBitmap(upright.width, upright.height, Bitmap.Config.ARGB_8888)
            val paint =
                Paint().apply {
                    isFilterBitmap = true
                    colorMatrixFor(effect)?.let { colorFilter = ColorMatrixColorFilter(it) }
                }
            Canvas(output).drawBitmap(upright, 0f, 0f, paint)

            if (upright !== source) upright.recycle()
            source.recycle()
            onFrame(output.asImageBitmap())
        } catch (e: Exception) {
            Log.e(TAG, "Effect processing failed", e)
        } finally {
            imageProxy.close()
            processing = false
        }
    }

    fun openCamera() {
        providerScope.launch {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        providerScope.cancel()
        analysisExecutor.shutdown()
    }
}

/** Maps an [EffectMode] to its [ColorMatrix], or null for the unfiltered passthrough. */
private fun colorMatrixFor(mode: EffectMode): ColorMatrix? =
    when (mode) {
        EffectMode.NONE -> {
            null
        }

        EffectMode.GRAYSCALE -> {
            ColorMatrix().apply { setSaturation(0f) }
        }

        EffectMode.INVERT -> {
            ColorMatrix(
                floatArrayOf(
                    -1f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            )
        }

        EffectMode.SEPIA -> {
            ColorMatrix(
                floatArrayOf(
                    0.393f,
                    0.769f,
                    0.189f,
                    0f,
                    0f,
                    0.349f,
                    0.686f,
                    0.168f,
                    0f,
                    0f,
                    0.272f,
                    0.534f,
                    0.131f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                ),
            )
        }

        EffectMode.COOL -> {
            ColorMatrix().apply { setScale(0.85f, 1f, 1.2f, 1f) }
        }

        EffectMode.WARM -> {
            ColorMatrix().apply { setScale(1.2f, 1f, 0.85f, 1f) }
        }

        EffectMode.VIVID -> {
            ColorMatrix().apply { setSaturation(1.7f) }
        }
    }
