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
package com.android.camera2.rawcapture

import android.net.Uri

sealed interface Camera2RawCaptureUiState {
    data object Initial : Camera2RawCaptureUiState

    data object Previewing : Camera2RawCaptureUiState

    /**
     * Reviewing/editing a just-captured DNG. [dngUri] is the saved file and [rotationDegrees] is the
     * sensor orientation needed to display the decoded RAW upright.
     */
    data class Editing(
        val dngUri: Uri,
        val rotationDegrees: Int,
    ) : Camera2RawCaptureUiState

    /** Shown when the camera does not advertise the RAW capability. */
    data object Unsupported : Camera2RawCaptureUiState

    data class Error(
        val errorMessage: String?,
    ) : Camera2RawCaptureUiState
}
