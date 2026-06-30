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
package com.android.camera2.extensions

import android.content.Context
import android.media.Image
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.camera.core.image.toBitmap
import com.android.camera.core.media.MediaStoreSaver
import com.android.camera.coreui.feedback.SaveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class Camera2ExtensionsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2ExtensionsUiState>(Camera2ExtensionsUiState.Initial)
        val uiState: StateFlow<Camera2ExtensionsUiState> = _uiState.asStateFlow()

        // One-shot save results surfaced to the UI as a toast (see ObserveSaveEvents).
        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // Guards against a double-tap on Done saving the same photo twice.
        private var isSaving = false

        // The selected extension and what the device supports live in the UiState; read them back
        // out when a transition needs them.
        private val currentExtension: Int
            get() =
                when (val state = _uiState.value) {
                    is Camera2ExtensionsUiState.Previewing -> state.currentExtension
                    is Camera2ExtensionsUiState.PhotoCaptured -> state.currentExtension
                    else -> NO_EXTENSION
                }

        private val supportedExtensions: List<Int>
            get() =
                when (val state = _uiState.value) {
                    is Camera2ExtensionsUiState.Previewing -> state.supportedExtensions
                    is Camera2ExtensionsUiState.PhotoCaptured -> state.supportedExtensions
                    else -> emptyList()
                }

        fun initialize() {
            if (_uiState.value is Camera2ExtensionsUiState.Initial) {
                _uiState.value =
                    Camera2ExtensionsUiState.Previewing(NO_EXTENSION, emptyList())
            }
        }

        /** Called by the controller once it has queried the device's supported extensions. */
        fun setSupported(list: List<Int>) {
            val extension = list.firstOrNull() ?: NO_EXTENSION
            _uiState.value = Camera2ExtensionsUiState.Previewing(extension, list)
        }

        fun setExtension(extension: Int) {
            _uiState.value =
                Camera2ExtensionsUiState.Previewing(extension, supportedExtensions)
        }

        fun processImage(
            image: Image,
            sensorOrientation: Int,
        ) {
            // Ignore rapid-fire captures: only the active preview can produce a photo, so a
            // double-tap can't spawn extra decode work or overwrite an already-captured photo.
            if (_uiState.value !is Camera2ExtensionsUiState.Previewing) {
                image.close()
                return
            }
            val mode = currentExtension
            val modes = supportedExtensions
            viewModelScope.launch {
                try {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            // toBitmap() rotates and always closes the image.
                            image.toBitmap(sensorOrientation)
                        }
                    _uiState.value =
                        Camera2ExtensionsUiState.PhotoCaptured(bitmap, mode, modes)
                } catch (e: Exception) {
                    _uiState.value =
                        Camera2ExtensionsUiState.Error("Error processing image: ${e.message}")
                }
            }
        }

        /**
         * Persists the captured photo to the gallery, emits a [SaveEvent] for the toast, then runs
         * [onSaved] (typically navigating back). A re-entrancy guard prevents a double-tap on Done
         * from saving twice.
         */
        fun saveAndFinish(onSaved: () -> Unit) {
            val bitmap = (_uiState.value as? Camera2ExtensionsUiState.PhotoCaptured)?.photoBitmap
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

        fun markUnsupported() {
            _uiState.value = Camera2ExtensionsUiState.Unsupported
        }

        fun resetToCamera() {
            _uiState.value =
                Camera2ExtensionsUiState.Previewing(currentExtension, supportedExtensions)
        }

        fun resetError() {
            _uiState.value =
                Camera2ExtensionsUiState.Previewing(currentExtension, supportedExtensions)
        }

        companion object {
            /** Sentinel for "no extension selected"; never collides with a real extension id. */
            const val NO_EXTENSION = -1
        }
    }
