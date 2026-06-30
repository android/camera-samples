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
package com.android.camera2.zoomandtorch

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class Camera2ZoomAndTorchViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2ZoomAndTorchUiState>(Camera2ZoomAndTorchUiState.Initial)
        val uiState: StateFlow<Camera2ZoomAndTorchUiState> = _uiState.asStateFlow()

        // The active lens lives in the Previewing UiState; read it back out for each transition.
        private val isFrontCamera: Boolean
            get() = (_uiState.value as? Camera2ZoomAndTorchUiState.Previewing)?.isFrontCamera ?: false

        fun initialize() {
            if (_uiState.value is Camera2ZoomAndTorchUiState.Initial) {
                _uiState.value = previewing(isFrontCamera)
            }
        }

        fun swapCamera() {
            // Reset zoom/torch to defaults; onCameraReady updates the ranges for the new lens.
            _uiState.value = previewing(!isFrontCamera)
        }

        fun onCameraReady(
            minZoom: Float,
            maxZoom: Float,
            hasFlash: Boolean,
        ) {
            _uiState.update { state ->
                val current = state as? Camera2ZoomAndTorchUiState.Previewing
                Camera2ZoomAndTorchUiState.Previewing(
                    isFrontCamera = isFrontCamera,
                    zoomRatio = (current?.zoomRatio ?: minZoom).coerceIn(minZoom, maxZoom),
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    torchOn = hasFlash && (current?.torchOn ?: false),
                    hasFlash = hasFlash,
                )
            }
        }

        fun setZoom(ratio: Float) {
            _uiState.update { state ->
                val current = state as? Camera2ZoomAndTorchUiState.Previewing ?: return@update state
                current.copy(zoomRatio = ratio.coerceIn(current.minZoom, current.maxZoom))
            }
        }

        fun toggleTorch() {
            _uiState.update { state ->
                val current = state as? Camera2ZoomAndTorchUiState.Previewing ?: return@update state
                if (!current.hasFlash) return@update state
                current.copy(torchOn = !current.torchOn)
            }
        }

        fun resetError() {
            _uiState.value = previewing(isFrontCamera)
        }

        private fun previewing(isFrontCamera: Boolean): Camera2ZoomAndTorchUiState.Previewing =
            Camera2ZoomAndTorchUiState.Previewing(
                isFrontCamera = isFrontCamera,
                zoomRatio = 1f,
                minZoom = 1f,
                maxZoom = 1f,
                torchOn = false,
                hasFlash = false,
            )
    }
