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
class Camera2TakeAVideoViewModel @Inject constructor() : ViewModel() {

    private val _uiState =
        MutableStateFlow<Camera2TakeAVideoUiState>(Camera2TakeAVideoUiState.Initial)
    val uiState: StateFlow<Camera2TakeAVideoUiState> = _uiState.asStateFlow()

    private var isFrontCamera = false
    private var currentConfig: CameraVideoConfig? = null

    fun initialize() {
        if (_uiState.value is Camera2TakeAVideoUiState.Initial) {
            _uiState.value = Camera2TakeAVideoUiState.Setup
        }
    }

    fun submitConfig(config: CameraVideoConfig) {
        currentConfig = config
        _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, config)
    }

    fun swapCamera() {
        isFrontCamera = !isFrontCamera
        val config = currentConfig ?: return
        _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, config)
    }

    fun startRecording() {
        val config = currentConfig ?: return
        _uiState.value = Camera2TakeAVideoUiState.Recording(isFrontCamera, config)
    }

    fun videoCaptured(uri: Uri) {
        val config = currentConfig ?: return
        _uiState.value = Camera2TakeAVideoUiState.VideoCaptured(uri, isFrontCamera, config)
    }

    fun resetToCamera() {
        val config = currentConfig ?: return
        _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, config)
    }

    fun showError(message: String) {
        _uiState.value = Camera2TakeAVideoUiState.Error(message)
    }

    fun resetError() {
        val config = currentConfig
        if (config != null) {
            _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, config)
        } else {
            _uiState.value = Camera2TakeAVideoUiState.Setup
        }
    }
}
