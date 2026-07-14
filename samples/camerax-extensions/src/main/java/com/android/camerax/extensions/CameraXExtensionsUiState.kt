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
package com.android.camerax.extensions

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * UI state for the CameraX vendor-extensions sample. The mode values are the integer constants from
 * [androidx.camera.extensions.ExtensionMode] (NONE/AUTO/BOKEH/HDR/NIGHT/FACE_RETOUCH).
 */
sealed interface CameraXExtensionsUiState {
    data object Initial : CameraXExtensionsUiState

    @Immutable
    data class Previewing(
        val currentMode: Int,
        val availableModes: List<Int>,
    ) : CameraXExtensionsUiState

    @Immutable
    data class PhotoCaptured(
        val photoBitmap: Bitmap,
        val currentMode: Int,
        val availableModes: List<Int>,
    ) : CameraXExtensionsUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXExtensionsUiState
}
