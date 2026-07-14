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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.android.camera.core.media.MediaStoreSaver
import com.android.camera.coreui.feedback.SaveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class Camera2TakeAVideoViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2TakeAVideoUiState>(Camera2TakeAVideoUiState.Initial(CameraVideoConfig()))
        val uiState: StateFlow<Camera2TakeAVideoUiState> = _uiState.asStateFlow()

        // One-shot save results surfaced to the UI as a toast (see ObserveSaveEvents). The recording
        // is already finalized to MediaStore by the controller; this just signals the toast.
        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // The active lens, config, and overlay visibility live in the UiState; read them back out for
        // each transition.
        private val isFrontCamera: Boolean
            get() =
                when (val state = _uiState.value) {
                    is Camera2TakeAVideoUiState.Previewing -> state.isFrontCamera
                    is Camera2TakeAVideoUiState.Recording -> state.isFrontCamera
                    is Camera2TakeAVideoUiState.VideoCaptured -> state.isFrontCamera
                    else -> false
                }

        private val currentConfig: CameraVideoConfig
            get() =
                when (val state = _uiState.value) {
                    is Camera2TakeAVideoUiState.Initial -> state.config
                    is Camera2TakeAVideoUiState.Previewing -> state.config
                    is Camera2TakeAVideoUiState.Recording -> state.config
                    is Camera2TakeAVideoUiState.VideoCaptured -> state.config
                    else -> CameraVideoConfig()
                }

        private val isOverlayVisible: Boolean
            get() =
                when (val state = _uiState.value) {
                    is Camera2TakeAVideoUiState.Previewing -> state.isOverlayVisible
                    is Camera2TakeAVideoUiState.Recording -> state.isOverlayVisible
                    else -> false
                }

        fun initialize() {
            if (_uiState.value is Camera2TakeAVideoUiState.Initial) {
                _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, currentConfig, isOverlayVisible)
            }
        }

        fun submitConfig(config: CameraVideoConfig) {
            _uiState.value = Camera2TakeAVideoUiState.Previewing(isFrontCamera, config, isOverlayVisible)
        }

        fun updateConfig(update: (CameraVideoConfig) -> CameraVideoConfig) {
            if (_uiState.value is Camera2TakeAVideoUiState.Previewing) {
                _uiState.value =
                    Camera2TakeAVideoUiState.Previewing(isFrontCamera, update(currentConfig), isOverlayVisible)
            }
        }

        fun toggleOverlay() {
            when (val state = _uiState.value) {
                is Camera2TakeAVideoUiState.Previewing -> {
                    _uiState.value = state.copy(isOverlayVisible = !state.isOverlayVisible)
                }

                is Camera2TakeAVideoUiState.Recording -> {
                    _uiState.value = state.copy(isOverlayVisible = !state.isOverlayVisible)
                }

                else -> {}
            }
        }

        fun swapCamera() {
            _uiState.value = Camera2TakeAVideoUiState.Previewing(!isFrontCamera, currentConfig, isOverlayVisible)
        }

        fun startRecording() {
            _uiState.value = Camera2TakeAVideoUiState.Recording(isFrontCamera, currentConfig, 0L, isOverlayVisible)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value = Camera2TakeAVideoUiState.VideoCaptured(uri, isFrontCamera, currentConfig)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? Camera2TakeAVideoUiState.VideoCaptured)?.let {
                MediaStoreSaver.deleteMedia(context, it.videoUri)
            }
            resetToCamera()
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
