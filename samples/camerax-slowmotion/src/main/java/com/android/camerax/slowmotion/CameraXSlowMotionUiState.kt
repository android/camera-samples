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
package com.android.camerax.slowmotion

import android.net.Uri
import android.util.Range
import androidx.compose.runtime.Immutable

sealed interface CameraXSlowMotionUiState {
    data object Initial : CameraXSlowMotionUiState

    /**
     * The device's camera does not advertise a high-speed (slow-motion) capture session, so there is
     * nothing for this sample to record (e.g. most emulators).
     */
    data object Unsupported : CameraXSlowMotionUiState

    @Immutable
    data class Previewing(
        val frameRate: Range<Int>,
    ) : CameraXSlowMotionUiState

    @Immutable
    data class Recording(
        val frameRate: Range<Int>,
    ) : CameraXSlowMotionUiState

    @Immutable
    data class VideoCaptured(
        val videoUri: Uri,
        val frameRate: Range<Int>,
    ) : CameraXSlowMotionUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXSlowMotionUiState
}
