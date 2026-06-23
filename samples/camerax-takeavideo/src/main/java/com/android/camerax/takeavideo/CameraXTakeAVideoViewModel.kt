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
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXTakeAVideoViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXTakeAVideoUiState>(CameraXTakeAVideoUiState.Initial)
        val uiState: StateFlow<CameraXTakeAVideoUiState> = _uiState.asStateFlow()

        private var isFrontCamera = false
        private var currentQuality = VideoQuality.HD

        fun initialize() {
            if (_uiState.value is CameraXTakeAVideoUiState.Initial) {
                _uiState.value =
                    CameraXTakeAVideoUiState.Previewing(isFrontCamera, currentQuality)
            }
        }

        fun swapCamera() {
            isFrontCamera = !isFrontCamera
            _uiState.value = CameraXTakeAVideoUiState.Previewing(isFrontCamera, currentQuality)
        }

        fun updateQuality(quality: VideoQuality) {
            currentQuality = quality
            if (_uiState.value is CameraXTakeAVideoUiState.Previewing) {
                _uiState.value = CameraXTakeAVideoUiState.Previewing(isFrontCamera, currentQuality)
            }
        }

        fun startRecording() {
            _uiState.value = CameraXTakeAVideoUiState.Recording(isFrontCamera, currentQuality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXTakeAVideoUiState.VideoCaptured(uri, isFrontCamera, currentQuality)
        }

        fun resetToCamera() {
            _uiState.value = CameraXTakeAVideoUiState.Previewing(isFrontCamera, currentQuality)
        }

        fun showError(message: String) {
            _uiState.value = CameraXTakeAVideoUiState.Error(message)
        }

        fun resetError() {
            _uiState.value = CameraXTakeAVideoUiState.Previewing(isFrontCamera, currentQuality)
        }
    }
