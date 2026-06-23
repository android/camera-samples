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
package com.android.camera2.takeavideo

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class Camera2TakeAVideoViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2TakeAVideoUiState>(Camera2TakeAVideoUiState.Initial(CameraVideoConfig()))
        val uiState: StateFlow<Camera2TakeAVideoUiState> = _uiState.asStateFlow()

        private var isFrontCamera = false
        private var currentConfig: CameraVideoConfig = CameraVideoConfig()
        private var isOverlayVisible = false

        fun initialize() {
            if (_uiState.value is Camera2TakeAVideoUiState.Initial) {
                _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
            }
        }

        fun submitConfig(config: CameraVideoConfig) {
            currentConfig = config
            _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
        }

        fun updateConfig(update: (CameraVideoConfig) -> CameraVideoConfig) {
            currentConfig = update(currentConfig)
            if (_uiState.value is Camera2TakeAVideoUiState.Previewing) {
                _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
            }
        }

        fun toggleOverlay() {
            isOverlayVisible = !isOverlayVisible
            when (val state = _uiState.value) {
                is Camera2TakeAVideoUiState.Previewing -> {
                    _uiState.value = state.copy(isOverlayVisible = isOverlayVisible)
                }

                is Camera2TakeAVideoUiState.Recording -> {
                    _uiState.value = state.copy(isOverlayVisible = isOverlayVisible)
                }

                else -> {}
            }
        }

        fun swapCamera() {
            isFrontCamera = !isFrontCamera
            _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
        }

        fun startRecording() {
            _uiState.value = Camera2TakeAVideoUiState.Recording(isFrontCamera, currentConfig, 0L, isOverlayVisible)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value = Camera2TakeAVideoUiState.VideoCaptured(uri, isFrontCamera, currentConfig)
        }

        fun resetToCamera() {
            _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
        }

        fun showError(message: String) {
            _uiState.value = Camera2TakeAVideoUiState.Error(message)
        }

        fun resetError() {
            _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
        }
    }
