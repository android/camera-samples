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
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXFlipWhileRecordingViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXFlipWhileRecordingUiState>(CameraXFlipWhileRecordingUiState.Initial)
        val uiState: StateFlow<CameraXFlipWhileRecordingUiState> = _uiState.asStateFlow()

        private var isFrontCamera = false
        private var quality = VideoQuality.HD

        fun initialize() {
            if (_uiState.value is CameraXFlipWhileRecordingUiState.Initial) {
                _uiState.value = CameraXFlipWhileRecordingUiState.Previewing(isFrontCamera, quality)
            }
        }

        /**
         * Flip the lens label. Unlike a normal video sample this updates the current state in place
         * (including while [Recording]) so the recording is never interrupted — the controller does the
         * actual rebind.
         */
        fun swapCamera() {
            isFrontCamera = !isFrontCamera
            _uiState.value =
                when (val state = _uiState.value) {
                    is CameraXFlipWhileRecordingUiState.Previewing -> state.copy(isFrontCamera = isFrontCamera)
                    is CameraXFlipWhileRecordingUiState.Recording -> state.copy(isFrontCamera = isFrontCamera)
                    else -> _uiState.value
                }
        }

        fun updateQuality(newQuality: VideoQuality) {
            quality = newQuality
            if (_uiState.value is CameraXFlipWhileRecordingUiState.Previewing) {
                _uiState.value = CameraXFlipWhileRecordingUiState.Previewing(isFrontCamera, quality)
            }
        }

        fun startRecording() {
            _uiState.value = CameraXFlipWhileRecordingUiState.Recording(isFrontCamera, quality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXFlipWhileRecordingUiState.VideoCaptured(uri, isFrontCamera, quality)
        }

        fun resetToCamera() {
            _uiState.value = CameraXFlipWhileRecordingUiState.Previewing(isFrontCamera, quality)
        }

        fun resetError() {
            _uiState.value = CameraXFlipWhileRecordingUiState.Previewing(isFrontCamera, quality)
        }
    }
