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

import androidx.annotation.StringRes
import androidx.camera.core.featuregroup.GroupableFeature

/** A CameraX groupable feature the user can query/apply, mapped to its [GroupableFeature]. */
enum class FeatureToggle(
    @param:StringRes val label: Int,
    val feature: GroupableFeature,
) {
    HDR(R.string.featurecombination_feature_hdr, GroupableFeature.HDR_HLG10),
    FPS60(R.string.featurecombination_feature_fps60, GroupableFeature.FPS_60),
    STABILIZATION(R.string.featurecombination_feature_stab, GroupableFeature.PREVIEW_STABILIZATION),
    ULTRA_HDR(R.string.featurecombination_feature_ultrahdr, GroupableFeature.IMAGE_ULTRA_HDR),
}

/** One precomputed support-matrix row: a label, the queried feature set, and the device verdict. */
data class MatrixRow(
    @param:StringRes val label: Int,
    val features: List<FeatureToggle>,
    val supported: Boolean,
)

sealed interface CameraXFeatureCombinationUiState {
    data object Initial : CameraXFeatureCombinationUiState

    /** Camera bound; the support matrix is being queried off the main thread. */
    data class Loading(
        val isFrontCamera: Boolean,
    ) : CameraXFeatureCombinationUiState

    data class Ready(
        val isFrontCamera: Boolean,
        val matrix: List<MatrixRow>,
        val selected: Set<FeatureToggle>,
        val currentlySupported: Boolean,
        val applied: Boolean,
    ) : CameraXFeatureCombinationUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXFeatureCombinationUiState
}
