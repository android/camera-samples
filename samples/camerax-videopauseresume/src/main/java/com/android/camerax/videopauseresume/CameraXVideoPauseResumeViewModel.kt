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
package com.android.camerax.videopauseresume

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXVideoPauseResumeViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXVideoPauseResumeUiState>(CameraXVideoPauseResumeUiState.Initial)
        val uiState: StateFlow<CameraXVideoPauseResumeUiState> = _uiState.asStateFlow()

        private var isFrontCamera = false

        fun initialize() {
            if (_uiState.value is CameraXVideoPauseResumeUiState.Initial) {
                _uiState.value = CameraXVideoPauseResumeUiState.Previewing(isFrontCamera)
            }
        }

        fun swapCamera() {
            isFrontCamera = !isFrontCamera
            _uiState.value = CameraXVideoPauseResumeUiState.Previewing(isFrontCamera)
        }

        fun startRecording() {
            _uiState.value =
                CameraXVideoPauseResumeUiState.Recording(isFrontCamera, paused = false, elapsedNanos = 0L)
        }

        fun setPaused(paused: Boolean) {
            val state = _uiState.value
            if (state is CameraXVideoPauseResumeUiState.Recording) {
                _uiState.value = state.copy(paused = paused)
            }
        }

        fun setDuration(elapsedNanos: Long) {
            val state = _uiState.value
            if (state is CameraXVideoPauseResumeUiState.Recording) {
                _uiState.value = state.copy(elapsedNanos = elapsedNanos)
            }
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value = CameraXVideoPauseResumeUiState.VideoCaptured(uri, isFrontCamera)
        }

        fun resetToCamera() {
            _uiState.value = CameraXVideoPauseResumeUiState.Previewing(isFrontCamera)
        }

        fun resetError() {
            _uiState.value = CameraXVideoPauseResumeUiState.Previewing(isFrontCamera)
        }
    }
