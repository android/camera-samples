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
package com.android.camerax.takeaphoto

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.camera.core.media.MediaStoreSaver
import com.android.camera.coreui.feedback.SaveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraXTakeAPhotoViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXTakeAPhotoUiState>(CameraXTakeAPhotoUiState.Initial)
        val uiState: StateFlow<CameraXTakeAPhotoUiState> = _uiState.asStateFlow()

        // One-shot save results surfaced to the UI as a toast (see ObserveSaveEvents).
        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // Guards against a double-tap on Done saving the same photo twice.
        private var isSaving = false

        // The active lens lives in the UiState; read it back out when a transition needs it.
        private val isFrontCamera: Boolean
            get() =
                when (val state = _uiState.value) {
                    is CameraXTakeAPhotoUiState.Capturing -> state.isFrontCamera
                    is CameraXTakeAPhotoUiState.PhotoCaptured -> state.isFrontCamera
                    else -> false
                }

        fun initialize() {
            if (_uiState.value is CameraXTakeAPhotoUiState.Initial) {
                _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
            }
        }

        fun swapCamera() {
            _uiState.value = CameraXTakeAPhotoUiState.Capturing(!isFrontCamera)
        }

        fun resetToCamera() {
            _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
        }

        fun processImage(bitmap: Bitmap) {
            // Ignore rapid-fire captures: only the active camera view can produce a photo, so a
            // double-tap can't overwrite an already-captured photo or spawn extra work.
            if (_uiState.value !is CameraXTakeAPhotoUiState.Capturing) {
                bitmap.recycle()
                return
            }
            // The controller already decoded/oriented the bitmap and closed the source proxy.
            _uiState.value = CameraXTakeAPhotoUiState.PhotoCaptured(bitmap, isFrontCamera)
        }

        /**
         * Persists the captured photo to the gallery, emits a [SaveEvent] for the toast, then runs
         * [onSaved] (typically navigating back). A re-entrancy guard prevents a double-tap on Done
         * from saving twice.
         */
        fun saveAndFinish(onSaved: () -> Unit) {
            val bitmap = (_uiState.value as? CameraXTakeAPhotoUiState.PhotoCaptured)?.photoBitmap
            if (bitmap == null) {
                onSaved()
                return
            }
            if (isSaving) return
            isSaving = true
            viewModelScope.launch {
                val uri = MediaStoreSaver.saveBitmap(context, bitmap)
                _events.send(if (uri != null) SaveEvent.Saved else SaveEvent.Failed)
                isSaving = false
                onSaved()
            }
        }

        fun resetError() {
            _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
        }
    }
