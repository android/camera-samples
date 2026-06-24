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
package com.android.camerax.videostabilization

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXVideoStabilizationViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXVideoStabilizationUiState>(CameraXVideoStabilizationUiState.Initial)
        val uiState: StateFlow<CameraXVideoStabilizationUiState> = _uiState.asStateFlow()

        private var stabilization = StabilizationMode.OFF
        private var supported = false
        private var quality = VideoQuality.HD

        fun initialize() {
            if (_uiState.value is CameraXVideoStabilizationUiState.Initial) {
                _uiState.value = previewing()
            }
        }

        /** Controller reports `VideoCapabilities.isStabilizationSupported()` after binding. */
        fun onStabilizationSupported(isSupported: Boolean) {
            supported = isSupported
            stabilization = if (isSupported) StabilizationMode.ON else StabilizationMode.OFF
            if (_uiState.value !is CameraXVideoStabilizationUiState.Recording) {
                _uiState.value = previewing()
            }
        }

        fun updateStabilization(mode: StabilizationMode) {
            stabilization = mode
            if (_uiState.value is CameraXVideoStabilizationUiState.Previewing) {
                _uiState.value = previewing()
            }
        }

        fun updateQuality(newQuality: VideoQuality) {
            quality = newQuality
            if (_uiState.value is CameraXVideoStabilizationUiState.Previewing) {
                _uiState.value = previewing()
            }
        }

        fun startRecording() {
            _uiState.value =
                CameraXVideoStabilizationUiState.Recording(stabilization, supported, quality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXVideoStabilizationUiState.VideoCaptured(uri, stabilization, supported, quality)
        }

        fun resetToCamera() {
            _uiState.value = previewing()
        }

        fun resetError() {
            _uiState.value = previewing()
        }

        private fun previewing() = CameraXVideoStabilizationUiState.Previewing(stabilization, supported, quality)
    }
