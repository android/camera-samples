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

sealed interface CameraXLuminosityUiState {
    data object Initial : CameraXLuminosityUiState

    /**
     * @param luma the average scene luminance of the latest analysis frame, normalized to `0f..1f`.
     * @param fps the smoothed rate at which analysis frames are arriving.
     */
    data class Analyzing(
        val luma: Float,
        val fps: Float,
    ) : CameraXLuminosityUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXLuminosityUiState
}
