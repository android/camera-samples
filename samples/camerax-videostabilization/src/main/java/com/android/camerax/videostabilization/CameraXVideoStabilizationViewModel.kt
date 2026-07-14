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
class CameraXVideoStabilizationViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXVideoStabilizationUiState>(CameraXVideoStabilizationUiState.Initial)
        val uiState: StateFlow<CameraXVideoStabilizationUiState> = _uiState.asStateFlow()

        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // Stabilization/support/quality live in the UiState; read them back out for each transition.
        private val stabilization: StabilizationMode
            get() =
                when (val state = _uiState.value) {
                    is CameraXVideoStabilizationUiState.Previewing -> state.stabilization
                    is CameraXVideoStabilizationUiState.Recording -> state.stabilization
                    is CameraXVideoStabilizationUiState.VideoCaptured -> state.stabilization
                    else -> StabilizationMode.OFF
                }

        private val supported: Boolean
            get() =
                when (val state = _uiState.value) {
                    is CameraXVideoStabilizationUiState.Previewing -> state.supported
                    is CameraXVideoStabilizationUiState.Recording -> state.supported
                    is CameraXVideoStabilizationUiState.VideoCaptured -> state.supported
                    else -> false
                }

        private val quality: VideoQuality
            get() =
                when (val state = _uiState.value) {
                    is CameraXVideoStabilizationUiState.Previewing -> state.quality
                    is CameraXVideoStabilizationUiState.Recording -> state.quality
                    is CameraXVideoStabilizationUiState.VideoCaptured -> state.quality
                    else -> VideoQuality.HD
                }

        fun initialize() {
            if (_uiState.value is CameraXVideoStabilizationUiState.Initial) {
                _uiState.value = previewing()
            }
        }

        /** Controller reports `VideoCapabilities.isStabilizationSupported()` after binding. */
        fun onStabilizationSupported(isSupported: Boolean) {
            if (_uiState.value !is CameraXVideoStabilizationUiState.Recording) {
                _uiState.value =
                    CameraXVideoStabilizationUiState.Previewing(
                        if (isSupported) StabilizationMode.ON else StabilizationMode.OFF,
                        isSupported,
                        quality,
                    )
            }
        }

        fun updateStabilization(mode: StabilizationMode) {
            val state = _uiState.value
            if (state is CameraXVideoStabilizationUiState.Previewing) {
                _uiState.value = state.copy(stabilization = mode)
            }
        }

        fun updateQuality(newQuality: VideoQuality) {
            val state = _uiState.value
            if (state is CameraXVideoStabilizationUiState.Previewing) {
                _uiState.value = state.copy(quality = newQuality)
            }
        }

        fun startRecording() {
            _uiState.value =
                CameraXVideoStabilizationUiState.Recording(stabilization, supported, quality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXVideoStabilizationUiState.VideoCaptured(uri, stabilization, supported, quality)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? CameraXVideoStabilizationUiState.VideoCaptured)?.let {
                MediaStoreSaver.deleteMedia(context, it.videoUri)
            }
            resetToCamera()
        }

        fun resetToCamera() {
            _uiState.value = previewing()
        }

        fun resetError() {
            _uiState.value = previewing()
        }

        private fun previewing() = CameraXVideoStabilizationUiState.Previewing(stabilization, supported, quality)
    }
