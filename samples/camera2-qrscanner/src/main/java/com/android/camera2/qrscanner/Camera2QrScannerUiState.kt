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
package com.android.camera2.qrscanner

import android.graphics.Rect
import androidx.compose.runtime.Immutable

/** A single barcode/QR detection: its decoded [value] and bounding box in source-frame pixels. */
data class DetectedBarcode(
    val value: String,
    val bounds: Rect?,
)

sealed interface Camera2QrScannerUiState {
    data object Initial : Camera2QrScannerUiState

    @Immutable
    data class Scanning(
        val barcodes: List<DetectedBarcode>,
        val sourceWidth: Int,
        val sourceHeight: Int,
    ) : Camera2QrScannerUiState

    data class Error(
        val errorMessage: String?,
    ) : Camera2QrScannerUiState
}
