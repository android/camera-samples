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
class CameraXFlipWhileRecordingViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXFlipWhileRecordingUiState>(CameraXFlipWhileRecordingUiState.Initial)
        val uiState: StateFlow<CameraXFlipWhileRecordingUiState> = _uiState.asStateFlow()

        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // The active lens and quality live in the UiState; read them back out for each transition.
        private val isFrontCamera: Boolean
            get() =
                when (val state = _uiState.value) {
                    is CameraXFlipWhileRecordingUiState.Previewing -> state.isFrontCamera
                    is CameraXFlipWhileRecordingUiState.Recording -> state.isFrontCamera
                    is CameraXFlipWhileRecordingUiState.VideoCaptured -> state.isFrontCamera
                    else -> false
                }

        private val quality: VideoQuality
            get() =
                when (val state = _uiState.value) {
                    is CameraXFlipWhileRecordingUiState.Previewing -> state.quality
                    is CameraXFlipWhileRecordingUiState.Recording -> state.quality
                    is CameraXFlipWhileRecordingUiState.VideoCaptured -> state.quality
                    else -> VideoQuality.HD
                }

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
            _uiState.value =
                when (val state = _uiState.value) {
                    is CameraXFlipWhileRecordingUiState.Previewing -> state.copy(isFrontCamera = !state.isFrontCamera)
                    is CameraXFlipWhileRecordingUiState.Recording -> state.copy(isFrontCamera = !state.isFrontCamera)
                    else -> _uiState.value
                }
        }

        fun updateQuality(newQuality: VideoQuality) {
            val state = _uiState.value
            if (state is CameraXFlipWhileRecordingUiState.Previewing) {
                _uiState.value = state.copy(quality = newQuality)
            }
        }

        fun startRecording() {
            _uiState.value = CameraXFlipWhileRecordingUiState.Recording(isFrontCamera, quality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXFlipWhileRecordingUiState.VideoCaptured(uri, isFrontCamera, quality)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? CameraXFlipWhileRecordingUiState.VideoCaptured)?.let {
                MediaStoreSaver.deleteMedia(context, it.videoUri)
            }
            resetToCamera()
        }

        fun resetToCamera() {
            _uiState.value = CameraXFlipWhileRecordingUiState.Previewing(isFrontCamera, quality)
        }

        fun resetError() {
            _uiState.value = CameraXFlipWhileRecordingUiState.Previewing(isFrontCamera, quality)
        }
    }
