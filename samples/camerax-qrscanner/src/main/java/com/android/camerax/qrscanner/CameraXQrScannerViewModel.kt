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

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXQrScannerViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXQrScannerUiState>(CameraXQrScannerUiState.Initial)
        val uiState: StateFlow<CameraXQrScannerUiState> = _uiState.asStateFlow()

        fun initialize() {
            if (_uiState.value is CameraXQrScannerUiState.Initial) {
                _uiState.value = CameraXQrScannerUiState.Scanning(emptyList(), 0, 0)
            }
        }

        fun setBarcodes(
            barcodes: List<DetectedBarcode>,
            sourceWidth: Int,
            sourceHeight: Int,
        ) {
            _uiState.value = CameraXQrScannerUiState.Scanning(barcodes, sourceWidth, sourceHeight)
        }

        fun resetError() {
            _uiState.value = CameraXQrScannerUiState.Scanning(emptyList(), 0, 0)
        }
    }
