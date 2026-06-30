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
class CameraXTakeAVideoViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXTakeAVideoUiState>(CameraXTakeAVideoUiState.Initial)
        val uiState: StateFlow<CameraXTakeAVideoUiState> = _uiState.asStateFlow()

        // One-shot save results surfaced to the UI as a toast (see ObserveSaveEvents). CameraX has
        // already finalized the recording to MediaStore; this just signals the toast.
        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // The active camera config lives in the UiState itself; these read the current values back
        // out so each transition can carry them forward.
        private val isFrontCamera: Boolean
            get() =
                when (val state = _uiState.value) {
                    is CameraXTakeAVideoUiState.Previewing -> state.isFrontCamera
                    is CameraXTakeAVideoUiState.Recording -> state.isFrontCamera
                    is CameraXTakeAVideoUiState.VideoCaptured -> state.isFrontCamera
                    else -> false
                }

        private val currentQuality: VideoQuality
            get() =
                when (val state = _uiState.value) {
                    is CameraXTakeAVideoUiState.Previewing -> state.quality
                    is CameraXTakeAVideoUiState.Recording -> state.quality
                    is CameraXTakeAVideoUiState.VideoCaptured -> state.quality
                    else -> VideoQuality.HD
                }

        fun initialize() {
            if (_uiState.value is CameraXTakeAVideoUiState.Initial) {
                _uiState.value =
                    CameraXTakeAVideoUiState.Previewing(isFrontCamera, currentQuality)
            }
        }

        fun swapCamera() {
            _uiState.value = CameraXTakeAVideoUiState.Previewing(!isFrontCamera, currentQuality)
        }

        fun updateQuality(quality: VideoQuality) {
            if (_uiState.value is CameraXTakeAVideoUiState.Previewing) {
                _uiState.value = CameraXTakeAVideoUiState.Previewing(isFrontCamera, quality)
            }
        }

        fun startRecording() {
            _uiState.value = CameraXTakeAVideoUiState.Recording(isFrontCamera, currentQuality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXTakeAVideoUiState.VideoCaptured(uri, isFrontCamera, currentQuality)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? CameraXTakeAVideoUiState.VideoCaptured)?.let {
                MediaStoreSaver.deleteMedia(context, it.videoUri)
            }
            resetToCamera()
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
