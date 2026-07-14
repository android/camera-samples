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

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap

/** The live color filters this sample applies to the camera, cycled in order. */
enum class EffectMode(
    @param:StringRes val label: Int,
) {
    NONE(R.string.effects_mode_none),
    GRAYSCALE(R.string.effects_mode_grayscale),
    INVERT(R.string.effects_mode_invert),
    SEPIA(R.string.effects_mode_sepia),
    COOL(R.string.effects_mode_cool),
    WARM(R.string.effects_mode_warm),
    VIVID(R.string.effects_mode_vivid),
}

sealed interface CameraXEffectsUiState {
    data object Initial : CameraXEffectsUiState

    /** [frame] is the latest filtered camera frame; null until the first one is processed. */
    data class Previewing(
        val effect: EffectMode,
        val frame: ImageBitmap?,
    ) : CameraXEffectsUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXEffectsUiState
}
