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
package com.android.camera2.hdrviewfinder

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class Camera2HdrViewfinderViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2HdrViewfinderUiState>(Camera2HdrViewfinderUiState.Initial)
        val uiState: StateFlow<Camera2HdrViewfinderUiState> = _uiState.asStateFlow()

        private var currentMode: ProcessingMode = ProcessingMode.NORMAL

        fun initialize() {
            if (_uiState.value is Camera2HdrViewfinderUiState.Initial) {
                _uiState.value = Camera2HdrViewfinderUiState.Previewing(currentMode)
            }
        }

        fun setMode(mode: ProcessingMode) {
            currentMode = mode
            if (_uiState.value is Camera2HdrViewfinderUiState.Previewing) {
                _uiState.value = Camera2HdrViewfinderUiState.Previewing(currentMode)
            }
        }

        fun showError(message: String) {
            _uiState.value = Camera2HdrViewfinderUiState.Error(message)
        }

        fun resetError() {
            _uiState.value = Camera2HdrViewfinderUiState.Previewing(currentMode)
        }
    }
