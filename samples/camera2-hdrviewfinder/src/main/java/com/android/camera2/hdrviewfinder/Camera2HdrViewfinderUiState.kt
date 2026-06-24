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
package com.android.camera2.hdrviewfinder

import androidx.annotation.StringRes

/**
 * The per-pixel transform applied to the live luma frame before it is rendered. This replaces the
 * RenderScript-based processing of the legacy HdrViewfinder sample with plain CPU math so the sample
 * keeps working on modern Android, where RenderScript has been removed.
 */
enum class ProcessingMode(
    @param:StringRes val label: Int,
) {
    /** Pass the luma value through unchanged (a grayscale viewfinder). */
    NORMAL(R.string.hdrviewfinder_mode_normal),

    /** Invert each luma value (`255 - luma`). */
    INVERT(R.string.hdrviewfinder_mode_invert),

    /** Binarize each luma value around the midpoint (`luma > 128 ? 255 : 0`). */
    THRESHOLD(R.string.hdrviewfinder_mode_threshold),

    /** Quantize each luma value into 64-step bands (`(luma / 64) * 64`). */
    POSTERIZE(R.string.hdrviewfinder_mode_posterize),
}

sealed interface Camera2HdrViewfinderUiState {
    data object Initial : Camera2HdrViewfinderUiState

    data class Previewing(
        val mode: ProcessingMode,
    ) : Camera2HdrViewfinderUiState

    data class Error(
        val errorMessage: String?,
    ) : Camera2HdrViewfinderUiState
}
