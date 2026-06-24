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
package com.android.camerax.greenscreen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXGreenScreen"

// Confidence below this is treated as background (fully transparent); above the upper bound is solid
// foreground. Between them the alpha ramps with confidence, which feathers the cut-out edge.
private const val BACKGROUND_CUTOFF = 0.1f
private const val FOREGROUND_CUTOFF = 0.95f

@Composable
fun rememberCameraXGreenScreenController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onPersonOverlay: (ImageBitmap) -> Unit,
    onUnsupported: () -> Unit,
): CameraXGreenScreenController =
    remember(context, lifecycleOwner, onPersonOverlay, onUnsupported) {
        CameraXGreenScreenController(context, lifecycleOwner, onPersonOverlay, onUnsupported)
    }

/**
 * A concurrent-camera "green screen": the back camera streams the live background through a
 * [Preview], while the front camera feeds an [ImageAnalysis] that ML Kit Selfie Segmentation turns
 * into a cut-out of the person. Both cameras are bound at once via
 * [ConcurrentCamera][androidx.camera.core.ConcurrentCamera] (gated on
 * [ProcessCameraProvider.getAvailableConcurrentCameraInfos]); the screen draws the [backSurfaceRequest]
 * underneath and the segmented [onPersonOverlay] bitmap (transparent everywhere but the subject) on
 * top. Segmentation runs on a single background executor with
 * [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] and a one-in-flight guard.
 */
@Stable
class CameraXGreenScreenController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onPersonOverlay: (ImageBitmap) -> Unit,
    private val onUnsupported: () -> Unit,
) {
    var backSurfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    @Volatile
    private var processing = false

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val segmenter =
        Segmentation.getClient(
            SelfieSegmenterOptions
                .Builder()
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .build(),
        )

    private val backPreview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> backSurfaceRequest = request }
        }

    private val frontAnalysis =
        ImageAnalysis
            .Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, ::analyze) }

    fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            if (provider.availableConcurrentCameraInfos.isEmpty()) {
                onUnsupported()
                return@addListener
            }

            try {
                val backConfig =
                    SingleCameraConfig(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        UseCaseGroup.Builder().addUseCase(backPreview).build(),
                        lifecycleOwner,
                    )
                val frontConfig =
                    SingleCameraConfig(
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        UseCaseGroup.Builder().addUseCase(frontAnalysis).build(),
                        lifecycleOwner,
                    )
                provider.unbindAll()
                provider.bindToLifecycle(listOf(backConfig, frontConfig))
            } catch (e: Exception) {
                Log.e(TAG, "Concurrent camera binding failed", e)
                onUnsupported()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(imageProxy: ImageProxy) {
        if (processing) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        processing = true
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        segmenter
            .process(inputImage)
            .addOnSuccessListener { mask ->
                try {
                    onPersonOverlay(cutOutPerson(imageProxy, mask, rotation).asImageBitmap())
                } catch (e: Exception) {
                    Log.e(TAG, "Compositing failed", e)
                }
            }.addOnFailureListener { e -> Log.e(TAG, "Segmentation failed", e) }
            .addOnCompleteListener {
                imageProxy.close()
                processing = false
            }
    }

    /**
     * Builds a front-camera cut-out: person pixels stay, everything else becomes transparent so the
     * back-camera preview shows through.
     *
     * ML Kit may hand back the mask either in the raw buffer orientation or in the upright
     * orientation it inferred from [rotation]. We detect which by comparing sizes, align the camera
     * frame to the mask's space so the two line up pixel-for-pixel (no stretching, no misaligned
     * cut-out), then apply exactly one rotation so the result is always upright — and mirror it for a
     * natural selfie view.
     */
    private fun cutOutPerson(
        imageProxy: ImageProxy,
        mask: SegmentationMask,
        rotation: Int,
    ): Bitmap {
        val maskWidth = mask.width
        val maskHeight = mask.height

        val camera = imageProxy.toBitmap()
        val maskIsUpright = camera.width != maskWidth || camera.height != maskHeight

        // Put the camera frame into the mask's coordinate space before compositing.
        val aligned =
            if (!maskIsUpright) {
                camera
            } else {
                val toUpright = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(camera, 0, 0, camera.width, camera.height, toUpright, true)
            }
        val source =
            if (aligned.width == maskWidth && aligned.height == maskHeight) {
                aligned
            } else {
                Bitmap.createScaledBitmap(aligned, maskWidth, maskHeight, true)
            }

        val cameraPixels = IntArray(maskWidth * maskHeight)
        source.getPixels(cameraPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        val confidences = mask.buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        val out = IntArray(maskWidth * maskHeight)
        for (i in out.indices) {
            val confidence = confidences.get(i)
            out[i] =
                when {
                    confidence < BACKGROUND_CUTOFF -> {
                        0
                    }

                    confidence >= FOREGROUND_CUTOFF -> {
                        cameraPixels[i]
                    }

                    else -> {
                        val alpha = (confidence * 255f).toInt().coerceIn(0, 255)
                        (cameraPixels[i] and 0x00FFFFFF) or (alpha shl 24)
                    }
                }
        }
        if (source !== aligned) source.recycle()
        if (aligned !== camera) aligned.recycle()
        camera.recycle()

        val person = Bitmap.createBitmap(out, maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        // If we composited in the raw buffer space, rotate the result upright now; always mirror.
        val displayMatrix =
            Matrix().apply {
                if (!maskIsUpright) postRotate(rotation.toFloat())
                postScale(-1f, 1f)
            }
        val display = Bitmap.createBitmap(person, 0, 0, maskWidth, maskHeight, displayMatrix, false)
        if (display !== person) person.recycle()
        return display
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        segmenter.close()
        analysisExecutor.shutdown()
    }
}
