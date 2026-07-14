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
package com.android.camerax.slowmotion

import android.content.Context
import android.net.Uri
import android.util.Range
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

// Placeholder range carried until the controller reports the device's real high-speed frame rate.
private val PENDING_FRAME_RATE = Range(0, 0)

@HiltViewModel
class CameraXSlowMotionViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXSlowMotionUiState>(CameraXSlowMotionUiState.Initial)
        val uiState: StateFlow<CameraXSlowMotionUiState> = _uiState.asStateFlow()

        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        private val frameRate: Range<Int>
            get() =
                when (val state = _uiState.value) {
                    is CameraXSlowMotionUiState.Previewing -> state.frameRate
                    is CameraXSlowMotionUiState.Recording -> state.frameRate
                    is CameraXSlowMotionUiState.VideoCaptured -> state.frameRate
                    else -> PENDING_FRAME_RATE
                }

        fun initialize() {
            if (_uiState.value is CameraXSlowMotionUiState.Initial) {
                // Move into Previewing so the controller's content call site composes and runs its
                // high-speed capability scan, which then confirms the real frame rate or flips to
                // Unsupported.
                _uiState.value = CameraXSlowMotionUiState.Previewing(PENDING_FRAME_RATE)
            }
        }

        /** The controller bound a high-speed session; record the device's real frame-rate range. */
        fun onSessionConfigured(frameRate: Range<Int>) {
            if (_uiState.value is CameraXSlowMotionUiState.Previewing) {
                _uiState.value = CameraXSlowMotionUiState.Previewing(frameRate)
            }
        }

        fun setUnsupported() {
            _uiState.value = CameraXSlowMotionUiState.Unsupported
        }

        fun startRecording() {
            _uiState.value = CameraXSlowMotionUiState.Recording(frameRate)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value = CameraXSlowMotionUiState.VideoCaptured(uri, frameRate)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? CameraXSlowMotionUiState.VideoCaptured)?.let {
                MediaStoreSaver.deleteMedia(context, it.videoUri)
            }
            resetToCamera()
        }

        fun resetToCamera() {
            _uiState.value = CameraXSlowMotionUiState.Previewing(frameRate)
        }

        fun resetError() {
            _uiState.value = CameraXSlowMotionUiState.Previewing(frameRate)
        }
    }
