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
package com.android.camera2.zoomandtorch

sealed interface Camera2ZoomAndTorchUiState {
    data object Initial : Camera2ZoomAndTorchUiState

    data class Previewing(
        val isFrontCamera: Boolean,
        val zoomRatio: Float,
        val minZoom: Float,
        val maxZoom: Float,
        val torchOn: Boolean,
        val hasFlash: Boolean,
    ) : Camera2ZoomAndTorchUiState

    data class Error(
        val errorMessage: String?,
    ) : Camera2ZoomAndTorchUiState
}
