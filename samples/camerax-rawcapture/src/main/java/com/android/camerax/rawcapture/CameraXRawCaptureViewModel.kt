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
package com.android.camerax.rawcapture

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
class CameraXRawCaptureViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXRawCaptureUiState>(CameraXRawCaptureUiState.Initial)
        val uiState: StateFlow<CameraXRawCaptureUiState> = _uiState.asStateFlow()

        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        fun initialize() {
            if (_uiState.value is CameraXRawCaptureUiState.Initial) {
                _uiState.value = CameraXRawCaptureUiState.Previewing
            }
        }

        fun setUnsupported() {
            _uiState.value = CameraXRawCaptureUiState.Unsupported
        }

        fun captured(
            dngUri: Uri,
            jpegUri: Uri,
        ) {
            _uiState.value = CameraXRawCaptureUiState.Captured(dngUri, jpegUri)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards both just-saved files from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? CameraXRawCaptureUiState.Captured)?.let {
                MediaStoreSaver.deleteMedia(context, it.dngUri)
                MediaStoreSaver.deleteMedia(context, it.jpegUri)
            }
            resetToCamera()
        }

        fun resetToCamera() {
            _uiState.value = CameraXRawCaptureUiState.Previewing
        }

        fun resetError() {
            _uiState.value = CameraXRawCaptureUiState.Previewing
        }
    }
