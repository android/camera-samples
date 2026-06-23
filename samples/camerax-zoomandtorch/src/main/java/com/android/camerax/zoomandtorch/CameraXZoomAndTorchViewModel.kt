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
package com.android.camerax.zoomandtorch

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CameraXZoomAndTorchViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXZoomAndTorchUiState>(CameraXZoomAndTorchUiState.Initial)
        val uiState: StateFlow<CameraXZoomAndTorchUiState> = _uiState.asStateFlow()

        private var isFrontCamera = false

        fun initialize() {
            if (_uiState.value is CameraXZoomAndTorchUiState.Initial) {
                _uiState.value = previewingState()
            }
        }

        fun swapCamera() {
            isFrontCamera = !isFrontCamera
            // Recreating the controller resets zoom and torch, so reset the UI state to match.
            _uiState.value = previewingState()
        }

        /**
         * Reported by the controller once binding completes and the camera's capabilities are known.
         * Clamps the current zoom into the new lens' supported range.
         */
        fun onCameraReady(
            min: Float,
            max: Float,
            hasFlash: Boolean,
        ) {
            _uiState.update { current ->
                val previewing = current as? CameraXZoomAndTorchUiState.Previewing
                val clampedZoom = previewing?.zoomRatio?.coerceIn(min, max) ?: min
                CameraXZoomAndTorchUiState.Previewing(
                    isFrontCamera = isFrontCamera,
                    zoomRatio = clampedZoom,
                    minZoom = min,
                    maxZoom = max,
                    torchOn = false,
                    hasFlash = hasFlash,
                )
            }
        }

        fun setZoom(ratio: Float) {
            _uiState.update { current ->
                val previewing =
                    current as? CameraXZoomAndTorchUiState.Previewing ?: return@update current
                previewing.copy(zoomRatio = ratio.coerceIn(previewing.minZoom, previewing.maxZoom))
            }
        }

        fun toggleTorch() {
            _uiState.update { current ->
                val previewing =
                    current as? CameraXZoomAndTorchUiState.Previewing ?: return@update current
                if (!previewing.hasFlash) return@update current
                previewing.copy(torchOn = !previewing.torchOn)
            }
        }

        fun resetError() {
            _uiState.value = previewingState()
        }

        private fun previewingState(): CameraXZoomAndTorchUiState.Previewing =
            CameraXZoomAndTorchUiState.Previewing(
                isFrontCamera = isFrontCamera,
                zoomRatio = 1f,
                minZoom = 1f,
                maxZoom = 1f,
                torchOn = false,
                hasFlash = false,
            )
    }
