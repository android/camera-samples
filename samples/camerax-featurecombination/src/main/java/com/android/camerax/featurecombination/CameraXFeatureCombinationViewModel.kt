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
package com.android.camerax.featurecombination

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXFeatureCombinationViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXFeatureCombinationUiState>(CameraXFeatureCombinationUiState.Initial)
        val uiState: StateFlow<CameraXFeatureCombinationUiState> = _uiState.asStateFlow()

        private var isFrontCamera = false

        fun initialize() {
            if (_uiState.value is CameraXFeatureCombinationUiState.Initial) {
                _uiState.value = CameraXFeatureCombinationUiState.Loading(isFrontCamera)
            }
        }

        /** The controller finished querying every combination for the current lens. */
        fun onMatrixComputed(
            isFront: Boolean,
            matrix: List<MatrixRow>,
        ) {
            _uiState.value =
                CameraXFeatureCombinationUiState.Ready(
                    isFrontCamera = isFront,
                    matrix = matrix,
                    selected = emptySet(),
                    currentlySupported = true,
                    applied = false,
                )
        }

        /** The interactive selection changed; the controller re-queried whether it's supported. */
        fun onSelectionEvaluated(
            selected: Set<FeatureToggle>,
            supported: Boolean,
        ) {
            val state = _uiState.value
            if (state is CameraXFeatureCombinationUiState.Ready) {
                _uiState.value =
                    state.copy(selected = selected, currentlySupported = supported, applied = false)
            }
        }

        fun onApplied() {
            val state = _uiState.value
            if (state is CameraXFeatureCombinationUiState.Ready) {
                _uiState.value = state.copy(applied = true)
            }
        }

        fun swapCamera() {
            isFrontCamera = !isFrontCamera
            _uiState.value = CameraXFeatureCombinationUiState.Loading(isFrontCamera)
        }

        fun resetError() {
            _uiState.value = CameraXFeatureCombinationUiState.Loading(isFrontCamera)
        }
    }
