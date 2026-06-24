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
package com.android.camerax.lowlightboost

sealed interface CameraXLowLightBoostUiState {
    data object Initial : CameraXLowLightBoostUiState

    /** The bound camera doesn't advertise low-light boost (e.g. emulators, most front cameras). */
    data object Unsupported : CameraXLowLightBoostUiState

    data class Previewing(
        val enabled: Boolean,
    ) : CameraXLowLightBoostUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXLowLightBoostUiState
}
