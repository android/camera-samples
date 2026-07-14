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
package com.android.camera2.hdrvideo

import android.hardware.camera2.params.DynamicRangeProfiles
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * A Camera2 10-bit dynamic-range profile this sample can record, mapped to its
 * [DynamicRangeProfiles] constant (a `Long` bitmask). These constants are inlined compile-time
 * values, so referencing them is safe below API 33; the controller still gates the whole feature on
 * API 33+. The sample only offers entries the camera actually advertises.
 */
enum class HdrDynamicRange(
    @param:StringRes val label: Int,
    val profile: Long,
) {
    SDR(R.string.hdrvideo_range_sdr, DynamicRangeProfiles.STANDARD),
    HLG10(R.string.hdrvideo_range_hlg10, DynamicRangeProfiles.HLG10),
    HDR10(R.string.hdrvideo_range_hdr10, DynamicRangeProfiles.HDR10),
    HDR10_PLUS(R.string.hdrvideo_range_hdr10plus, DynamicRangeProfiles.HDR10_PLUS),
    ;

    val isHdr: Boolean get() = this != SDR
}

sealed interface Camera2HdrVideoUiState {
    data object Initial : Camera2HdrVideoUiState

    /** API < 33, or the camera advertises no 10-bit HDR profile (e.g. emulators). */
    data object Unsupported : Camera2HdrVideoUiState

    @Immutable
    data class Previewing(
        val selectedRange: HdrDynamicRange,
        val supportedRanges: List<HdrDynamicRange>,
    ) : Camera2HdrVideoUiState

    @Immutable
    data class Recording(
        val selectedRange: HdrDynamicRange,
        val supportedRanges: List<HdrDynamicRange>,
    ) : Camera2HdrVideoUiState

    @Immutable
    data class VideoCaptured(
        val videoUri: Uri,
        val selectedRange: HdrDynamicRange,
        val supportedRanges: List<HdrDynamicRange>,
    ) : Camera2HdrVideoUiState

    data class Error(
        val errorMessage: String?,
    ) : Camera2HdrVideoUiState
}
