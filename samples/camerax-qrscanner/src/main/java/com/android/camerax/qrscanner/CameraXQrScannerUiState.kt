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
package com.android.camerax.qrscanner

import android.graphics.Rect
import androidx.compose.runtime.Immutable

/**
 * A single barcode detected by ML Kit on an analysis frame.
 *
 * @param value the decoded raw value, or an empty string if it could not be decoded.
 * @param bounds the detected bounding box in the analysis image's coordinate space, or `null` if
 * ML Kit could not provide one.
 */
data class DetectedBarcode(
    val value: String,
    val bounds: Rect?,
)

sealed interface CameraXQrScannerUiState {
    data object Initial : CameraXQrScannerUiState

    /**
     * Live scanning state.
     *
     * @param barcodes the barcodes detected on the most recent analysis frame.
     * @param sourceWidth width of the analysis frame the [barcodes] were detected in, in pixels.
     * @param sourceHeight height of the analysis frame the [barcodes] were detected in, in pixels.
     */
    @Immutable
    data class Scanning(
        val barcodes: List<DetectedBarcode>,
        val sourceWidth: Int,
        val sourceHeight: Int,
    ) : CameraXQrScannerUiState

    data class Error(
        val errorMessage: String?,
    ) : CameraXQrScannerUiState
}
