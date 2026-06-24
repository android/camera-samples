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
package com.android.camerax.featurecombination

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXFeatureComboCtrl"

/** A labelled feature combination to probe in the support matrix. */
private data class Combo(
    @param:StringRes val labelRes: Int,
    val features: List<FeatureToggle>,
)

/** Individual features first, then the interesting combinations. */
private val MATRIX_COMBOS =
    listOf(
        Combo(R.string.featurecombination_feature_hdr, listOf(FeatureToggle.HDR)),
        Combo(R.string.featurecombination_feature_fps60, listOf(FeatureToggle.FPS60)),
        Combo(R.string.featurecombination_feature_stab, listOf(FeatureToggle.STABILIZATION)),
        Combo(R.string.featurecombination_feature_ultrahdr, listOf(FeatureToggle.ULTRA_HDR)),
        Combo(R.string.featurecombination_matrix_hdr_fps, listOf(FeatureToggle.HDR, FeatureToggle.FPS60)),
        Combo(R.string.featurecombination_matrix_hdr_stab, listOf(FeatureToggle.HDR, FeatureToggle.STABILIZATION)),
        Combo(
            R.string.featurecombination_matrix_hdr_fps_stab,
            listOf(FeatureToggle.HDR, FeatureToggle.FPS60, FeatureToggle.STABILIZATION),
        ),
        Combo(R.string.featurecombination_matrix_all, FeatureToggle.entries.toList()),
    )

@Composable
fun rememberCameraXFeatureCombinationController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isFrontCamera: Boolean,
    onMatrixComputed: (isFront: Boolean, matrix: List<MatrixRow>) -> Unit,
): CameraXFeatureCombinationController =
    remember(context, isFrontCamera, onMatrixComputed) {
        CameraXFeatureCombinationController(context, lifecycleOwner, isFrontCamera, onMatrixComputed)
    }

/**
 * Demonstrates the CameraX feature-combination query. Before binding, it asks the camera —
 * via [CameraInfo.isSessionConfigSupported] on a [SessionConfig] tagged with a
 * [androidx.camera.core.featuregroup.GroupableFeature] group — which combinations of HDR / 60fps /
 * preview-stabilization / Ultra-HDR are supported together, and can [bindWith] a chosen combination to
 * prove it. The `camera-featurecombinationquery` (+ play-services) dependency is the offline provider
 * that backs this query before the camera opens; the call surface is just `isSessionConfigSupported`.
 */
@Stable
class CameraXFeatureCombinationController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val isFrontCamera: Boolean,
    private val onMatrixComputed: (isFront: Boolean, matrix: List<MatrixRow>) -> Unit,
) {
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // The query SessionConfigs are built from these; ImageCapture is needed for IMAGE_ULTRA_HDR.
    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
    private val videoCapture =
        VideoCapture.withOutput(
            Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build(),
        )
    private val imageCapture = ImageCapture.Builder().build()

    private fun selector(): CameraSelector =
        CameraSelector
            .Builder()
            .requireLensFacing(
                if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK,
            ).build()

    /**
     * Builds the query/bind [SessionConfig] from ONLY the use cases the requested features need:
     * HDR / 60fps / stabilization apply to video (Preview + VideoCapture), Ultra HDR applies to stills
     * (Preview + ImageCapture). Including every use case in every query over-constrains it — e.g. asking
     * for "HDR" alongside an unused ImageCapture stream reports unsupported even on devices that do
     * support HDR video, which is why the matrix looked all-✗.
     */
    private fun buildSessionConfig(features: List<FeatureToggle>): SessionConfig {
        val useCases =
            buildList<UseCase> {
                add(preview)
                if (features.any { it != FeatureToggle.ULTRA_HDR }) add(videoCapture)
                if (FeatureToggle.ULTRA_HDR in features) add(imageCapture)
            }
        val builder = SessionConfig.Builder(*useCases.toTypedArray())
        if (features.isNotEmpty()) {
            builder.setRequiredFeatureGroup(*features.map { it.feature }.toTypedArray())
        }
        return builder.build()
    }

    fun openCamera() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val info =
                try {
                    provider.getCameraInfo(selector())
                } catch (e: Exception) {
                    Log.e(TAG, "getCameraInfo failed", e)
                    null
                }
            cameraInfo = info
            bindBase(provider) // show the live preview immediately
            if (info == null) {
                onMatrixComputed(isFrontCamera, emptyList())
            } else {
                computeMatrix(info)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Queries every combination off the main thread, then reports the matrix back on the main thread. */
    private fun computeMatrix(info: CameraInfo) {
        cameraExecutor.execute {
            val rows =
                MATRIX_COMBOS.map { combo ->
                    val supported =
                        try {
                            info.isSessionConfigSupported(buildSessionConfig(combo.features))
                        } catch (e: Exception) {
                            false
                        }
                    MatrixRow(combo.labelRes, combo.features, supported)
                }
            ContextCompat.getMainExecutor(context).execute {
                onMatrixComputed(isFrontCamera, rows)
            }
        }
    }

    /** Synchronous support check for the live interactive selection (a single, cheap query). */
    fun isSupported(selection: Set<FeatureToggle>): Boolean {
        if (selection.isEmpty()) return true
        val info = cameraInfo ?: return false
        return try {
            info.isSessionConfigSupported(buildSessionConfig(selection.toList()))
        } catch (e: Exception) {
            false
        }
    }

    /** Apply: actually bind the camera with the selected feature group to prove the combo works. */
    fun bindWith(selection: Set<FeatureToggle>) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()
            val camera =
                provider.bindToLifecycle(lifecycleOwner, selector(), buildSessionConfig(selection.toList()))
            cameraControl = camera.cameraControl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind selected feature group; falling back to preview", e)
            bindBase(provider)
        }
    }

    private fun bindBase(provider: ProcessCameraProvider) {
        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, selector(), preview)
            cameraControl = camera.cameraControl
        } catch (e: Exception) {
            Log.e(TAG, "Base preview binding failed", e)
        }
    }

    fun updateTargetRotation(rotation: Int) {
        preview.targetRotation = rotation
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
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        cameraExecutor.shutdown()
    }
}
