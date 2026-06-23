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

import androidx.camera.core.ImageProxy
import androidx.camera.extensions.ExtensionMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.camera.core.image.toBitmap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Human-readable label for an [ExtensionMode] integer constant. */
fun extensionModeLabel(mode: Int): String =
    when (mode) {
        ExtensionMode.AUTO -> "Auto"
        ExtensionMode.BOKEH -> "Bokeh"
        ExtensionMode.HDR -> "HDR"
        ExtensionMode.NIGHT -> "Night"
        ExtensionMode.FACE_RETOUCH -> "Face Retouch"
        else -> "None"
    }

@HiltViewModel
class CameraXExtensionsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXExtensionsUiState>(CameraXExtensionsUiState.Initial)
        val uiState: StateFlow<CameraXExtensionsUiState> = _uiState.asStateFlow()

        private var currentMode: Int = ExtensionMode.NONE

        // Until the ExtensionsManager reports what the device supports, only NONE is selectable.
        private var availableModes: List<Int> = listOf(ExtensionMode.NONE)

        fun initialize() {
            if (_uiState.value is CameraXExtensionsUiState.Initial) {
                _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
            }
        }

        /** Called once the [androidx.camera.extensions.ExtensionsManager] has resolved availability. */
        fun setAvailableModes(modes: List<Int>) {
            availableModes = modes
            if (currentMode !in modes) {
                currentMode = ExtensionMode.NONE
            }
            _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
        }

        fun setExtension(mode: Int) {
            currentMode = mode
            _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
        }

        fun processImage(image: ImageProxy) {
            viewModelScope.launch {
                try {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            // toBitmap() rotates and always closes the image.
                            image.toBitmap()
                        }
                    _uiState.value = CameraXExtensionsUiState.PhotoCaptured(bitmap)
                } catch (e: Exception) {
                    _uiState.value =
                        CameraXExtensionsUiState.Error("Error processing image: ${e.message}")
                }
            }
        }

        fun resetToCamera() {
            _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
        }

        fun resetError() {
            _uiState.value = CameraXExtensionsUiState.Previewing(currentMode, availableModes)
        }
    }
