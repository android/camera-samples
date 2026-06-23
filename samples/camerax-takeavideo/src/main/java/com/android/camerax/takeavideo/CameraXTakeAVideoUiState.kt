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
package com.android.camerax.takeavideo

import android.net.Uri
import androidx.camera.video.Quality

/**
 * The recording qualities a sample exposes through the settings overlay. Each maps to a CameraX
 * [Quality] constant; a `QualitySelector` falls back to a lower supported quality when a device
 * cannot honor the requested one.
 */
enum class VideoQuality(
    val label: String,
    val quality: Quality,
) {
    SD("SD (480p)", Quality.SD),
    HD("HD (720p)", Quality.HD),
    FHD("FHD (1080p)", Quality.FHD),
    UHD("UHD (2160p)", Quality.UHD),
}

sealed interface CameraXTakeAVideoUiState {
    data object Initial : CameraXTakeAVideoUiState

    data class Previewing(
        val isFrontCamera: Boolean,
        val quality: VideoQuality,
    ) : CameraXTakeAVideoUiState

    data class Recording(
        val isFrontCamera: Boolean,
        val quality: VideoQuality,
    ) : CameraXTakeAVideoUiState

    data class VideoCaptured(
        val videoUri: Uri,
        val isFrontCamera: Boolean,
        val quality: VideoQuality,
    ) : CameraXTakeAVideoUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXTakeAVideoUiState
}
