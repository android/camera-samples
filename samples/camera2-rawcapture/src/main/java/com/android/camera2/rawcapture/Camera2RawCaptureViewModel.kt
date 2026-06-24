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
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class Camera2RawCaptureViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2RawCaptureUiState>(Camera2RawCaptureUiState.Initial)
        val uiState: StateFlow<Camera2RawCaptureUiState> = _uiState.asStateFlow()

        fun initialize() {
            if (_uiState.value is Camera2RawCaptureUiState.Initial) {
                _uiState.value = Camera2RawCaptureUiState.Previewing
            }
        }

        fun setUnsupported() {
            _uiState.value = Camera2RawCaptureUiState.Unsupported
        }

        fun onDngSaved(
            uri: Uri,
            rotationDegrees: Int,
        ) {
            _uiState.value = Camera2RawCaptureUiState.Editing(uri, rotationDegrees)
        }

        fun backToCamera() {
            _uiState.value = Camera2RawCaptureUiState.Previewing
        }

        fun showError(message: String) {
            _uiState.value = Camera2RawCaptureUiState.Error(message)
        }

        fun resetError() {
            _uiState.value = Camera2RawCaptureUiState.Previewing
        }
    }
