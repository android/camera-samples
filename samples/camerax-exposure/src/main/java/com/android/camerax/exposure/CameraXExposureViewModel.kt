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
package com.android.camerax.exposure

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXExposureViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXExposureUiState>(CameraXExposureUiState.Initial)
        val uiState: StateFlow<CameraXExposureUiState> = _uiState.asStateFlow()

        // The active lens lives in the Previewing UiState; read it back out for each transition.
        private val isFrontCamera: Boolean
            get() = (_uiState.value as? CameraXExposureUiState.Previewing)?.isFrontCamera ?: false

        fun initialize() {
            if (_uiState.value is CameraXExposureUiState.Initial) {
                _uiState.value = previewing(isFrontCamera)
            }
        }

        fun swapCamera() {
            // Reset to a neutral exposure; the new camera reports its own range via onCameraReady.
            _uiState.value = previewing(!isFrontCamera)
        }

        fun onCameraReady(
            supported: Boolean,
            min: Int,
            max: Int,
            stepEv: Float,
        ) {
            val current = _uiState.value
            if (current !is CameraXExposureUiState.Previewing) return
            val clampedIndex = current.evIndex.coerceIn(min, max)
            _uiState.value =
                current.copy(
                    evIndex = clampedIndex,
                    minIndex = min,
                    maxIndex = max,
                    stepEv = stepEv,
                    supported = supported,
                )
        }

        fun setExposureIndex(index: Int) {
            val current = _uiState.value
            if (current !is CameraXExposureUiState.Previewing) return
            val clampedIndex = index.coerceIn(current.minIndex, current.maxIndex)
            if (clampedIndex == current.evIndex) return
            _uiState.value = current.copy(evIndex = clampedIndex)
        }

        fun resetError() {
            _uiState.value = previewing(isFrontCamera)
        }

        private fun previewing(isFrontCamera: Boolean): CameraXExposureUiState.Previewing =
            CameraXExposureUiState.Previewing(
                isFrontCamera = isFrontCamera,
                evIndex = 0,
                minIndex = 0,
                maxIndex = 0,
                stepEv = 0f,
                supported = false,
            )
    }
