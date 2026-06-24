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
package com.android.camerax.flipwhilerecording

import android.net.Uri
import androidx.annotation.StringRes
import androidx.camera.video.Quality

enum class VideoQuality(
    @param:StringRes val label: Int,
    val quality: Quality,
) {
    SD(R.string.flipwhilerecording_quality_sd, Quality.SD),
    HD(R.string.flipwhilerecording_quality_hd, Quality.HD),
    FHD(R.string.flipwhilerecording_quality_fhd, Quality.FHD),
    UHD(R.string.flipwhilerecording_quality_uhd, Quality.UHD),
}

sealed interface CameraXFlipWhileRecordingUiState {
    data object Initial : CameraXFlipWhileRecordingUiState

    data class Previewing(
        val isFrontCamera: Boolean,
        val quality: VideoQuality,
    ) : CameraXFlipWhileRecordingUiState

    data class Recording(
        val isFrontCamera: Boolean,
        val quality: VideoQuality,
    ) : CameraXFlipWhileRecordingUiState

    data class VideoCaptured(
        val videoUri: Uri,
        val isFrontCamera: Boolean,
        val quality: VideoQuality,
    ) : CameraXFlipWhileRecordingUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXFlipWhileRecordingUiState
}
