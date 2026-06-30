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
package com.android.camerax.media3effects

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter

/**
 * The selectable Media3 GPU effects this sample applies to the live CameraX preview through the
 * CameraX-Media3 bridge. Each entry maps to the list of [Effect]s handed to
 * [androidx.camera.media3.effect.Media3Effect.setEffects]; an empty list is the unfiltered
 * passthrough.
 */
@Immutable
enum class Media3EffectMode(
    @param:StringRes val label: Int,
    val effects: List<Effect>,
) {
    NONE(R.string.media3effects_mode_none, emptyList()),
    GRAYSCALE(R.string.media3effects_mode_grayscale, listOf(RgbFilter.createGrayscaleFilter())),
    INVERT(R.string.media3effects_mode_invert, listOf(RgbFilter.createInvertedFilter())),
    BRIGHT(R.string.media3effects_mode_bright, listOf(Brightness(0.35f))),
    CONTRAST(R.string.media3effects_mode_contrast, listOf(Contrast(0.6f))),
    WARM(
        R.string.media3effects_mode_warm,
        listOf(
            RgbAdjustment
                .Builder()
                .setRedScale(1.25f)
                .setBlueScale(0.8f)
                .build(),
        ),
    ),
    COOL(
        R.string.media3effects_mode_cool,
        listOf(
            RgbAdjustment
                .Builder()
                .setRedScale(0.8f)
                .setBlueScale(1.25f)
                .build(),
        ),
    ),
}

sealed interface CameraXMedia3EffectsUiState {
    data object Initial : CameraXMedia3EffectsUiState

    data class Previewing(
        val effect: Media3EffectMode,
    ) : CameraXMedia3EffectsUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXMedia3EffectsUiState
}
