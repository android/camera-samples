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
package com.android.camerax.extensions

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.camera.extensions.ExtensionMode
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

/** Human-readable label resource for an [ExtensionMode] integer constant. */
@StringRes
fun extensionModeLabel(mode: Int): Int =
    when (mode) {
        ExtensionMode.AUTO -> R.string.extensions_mode_auto
        ExtensionMode.BOKEH -> R.string.extensions_mode_bokeh
        ExtensionMode.HDR -> R.string.extensions_mode_hdr
        ExtensionMode.NIGHT -> R.string.extensions_mode_night
        ExtensionMode.FACE_RETOUCH -> R.string.extensions_mode_face_retouch
        else -> R.string.extensions_mode_none
    }

@HiltViewModel
class CameraXExtensionsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXExtensionsUiState>(CameraXExtensionsUiState.Initial)
        val uiState: StateFlow<CameraXExtensionsUiState> = _uiState.asStateFlow()

        // One-shot save results surfaced to the UI as a toast (see ObserveSaveEvents).
        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // Guards against a double-tap on Done saving the same photo twice.
        private var isSaving = false

        // The selected mode and what the device supports live in the UiState; read them back out
        // when a transition needs them. Until the ExtensionsManager reports availability, only NONE
        // is selectable.
        private val currentMode: Int
            get() =
                when (val state = _uiState.value) {
                    is CameraXExtensionsUiState.Previewing -> state.currentMode
                    is CameraXExtensionsUiState.PhotoCaptured -> state.currentMode
                    else -> ExtensionMode.NONE
                }

        private val availableModes: List<Int>
            get() =
                when (val state = _uiState.value) {
                    is CameraXExtensionsUiState.Previewing -> state.availableModes
                    is CameraXExtensionsUiState.PhotoCaptured -> state.availableModes
                    else -> listOf(ExtensionMode.NONE)
                }

        fun initialize() {
            if (_uiState.value is CameraXExtensionsUiState.Initial) {
                _uiState.value =
                    CameraXExtensionsUiState.Previewing(ExtensionMode.NONE, listOf(ExtensionMode.NONE))
            }
        }

        /** Called once the [androidx.camera.extensions.ExtensionsManager] has resolved availability. */
        fun setAvailableModes(modes: List<Int>) {
            val mode = if (currentMode in modes) currentMode else ExtensionMode.NONE
            _uiState.value = CameraXExtensionsUiState.Previewing(mode, modes)
        }

        fun setExtension(mode: Int) {
            _uiState.value = CameraXExtensionsUiState.Previewing(mode, availableModes)
        }

        fun processImage(bitmap: Bitmap) {
            // Ignore rapid-fire captures: only the active preview can produce a photo, so a
            // double-tap can't overwrite an already-captured photo or spawn extra work.
            if (_uiState.value !is CameraXExtensionsUiState.Previewing) {
                bitmap.recycle()
                return
            }
            // The controller already decoded/oriented the bitmap and closed the source proxy.
            _uiState.value =
                CameraXExtensionsUiState.PhotoCaptured(bitmap, currentMode, availableModes)
        }

        /**
         * Persists the captured photo to the gallery, emits a [SaveEvent] for the toast, then runs
         * [onSaved] (typically navigating back). A re-entrancy guard prevents a double-tap on Done
         * from saving twice.
         */
        fun saveAndFinish(onSaved: () -> Unit) {
            val bitmap = (_uiState.value as? CameraXExtensionsUiState.PhotoCaptured)?.photoBitmap
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

        fun resetToCamera() {
            _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
        }

        fun resetError() {
            _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
        }
    }
