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
package com.android.camera2.manualcontrols

sealed interface Camera2ManualControlsUiState {
    data object Initial : Camera2ManualControlsUiState

    data class Previewing(
        val manualEnabled: Boolean,
        val iso: Int,
        val isoRange: Pair<Int, Int>,
        val exposureNs: Long,
        val exposureRangeNs: Pair<Long, Long>,
        val focusDistance: Float,
        val minFocusDistance: Float,
    ) : Camera2ManualControlsUiState

    data object Unsupported : Camera2ManualControlsUiState

    data class Error(
        val errorMessage: String?,
    ) : Camera2ManualControlsUiState
}
