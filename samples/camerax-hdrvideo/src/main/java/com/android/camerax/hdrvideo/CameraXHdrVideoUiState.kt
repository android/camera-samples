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
package com.android.camerax.hdrvideo

import android.net.Uri
import androidx.annotation.StringRes
import androidx.camera.core.DynamicRange
import androidx.camera.video.Quality

/**
 * A 10-bit HDR dynamic range CameraX can record, mapped to its [DynamicRange] constant. The sample
 * only offers the entries a device actually advertises (see the controller's range scan); [SDR] is
 * always supported and acts as the non-HDR baseline.
 */
enum class HdrDynamicRange(
    @param:StringRes val label: Int,
    val range: DynamicRange,
) {
    SDR(R.string.hdrvideo_range_sdr, DynamicRange.SDR),
    HLG10(R.string.hdrvideo_range_hlg10, DynamicRange.HLG_10_BIT),
    HDR10(R.string.hdrvideo_range_hdr10, DynamicRange.HDR10_10_BIT),
    HDR10_PLUS(R.string.hdrvideo_range_hdr10plus, DynamicRange.HDR10_PLUS_10_BIT),
    DOLBY_VISION(R.string.hdrvideo_range_dolby, DynamicRange.DOLBY_VISION_10_BIT),
    ;

    val isHdr: Boolean get() = this != SDR
}

/** The recording qualities exposed through the settings overlay (falls back when unsupported). */
enum class VideoQuality(
    @param:StringRes val label: Int,
    val quality: Quality,
) {
    SD(R.string.hdrvideo_quality_sd, Quality.SD),
    HD(R.string.hdrvideo_quality_hd, Quality.HD),
    FHD(R.string.hdrvideo_quality_fhd, Quality.FHD),
    UHD(R.string.hdrvideo_quality_uhd, Quality.UHD),
}

sealed interface CameraXHdrVideoUiState {
    data object Initial : CameraXHdrVideoUiState

    /** No 10-bit HDR dynamic range is available on this device (e.g. emulators). */
    data object Unsupported : CameraXHdrVideoUiState

    data class Previewing(
        val isFrontCamera: Boolean,
        val selectedRange: HdrDynamicRange,
        val supportedRanges: List<HdrDynamicRange>,
        val quality: VideoQuality,
    ) : CameraXHdrVideoUiState

    data class Recording(
        val isFrontCamera: Boolean,
        val selectedRange: HdrDynamicRange,
        val supportedRanges: List<HdrDynamicRange>,
        val quality: VideoQuality,
    ) : CameraXHdrVideoUiState

    data class VideoCaptured(
        val videoUri: Uri,
        val isFrontCamera: Boolean,
        val selectedRange: HdrDynamicRange,
        val supportedRanges: List<HdrDynamicRange>,
        val quality: VideoQuality,
    ) : CameraXHdrVideoUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXHdrVideoUiState
}
