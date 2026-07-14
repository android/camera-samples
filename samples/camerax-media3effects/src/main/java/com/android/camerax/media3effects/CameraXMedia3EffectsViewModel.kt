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
package com.android.camerax.media3effects

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXMedia3EffectsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXMedia3EffectsUiState>(CameraXMedia3EffectsUiState.Initial)
        val uiState: StateFlow<CameraXMedia3EffectsUiState> = _uiState.asStateFlow()

        fun initialize() {
            if (_uiState.value is CameraXMedia3EffectsUiState.Initial) {
                _uiState.value = CameraXMedia3EffectsUiState.Previewing(Media3EffectMode.NONE)
            }
        }

        fun selectEffect(mode: Media3EffectMode) {
            val state = _uiState.value
            if (state is CameraXMedia3EffectsUiState.Previewing) {
                _uiState.value = state.copy(effect = mode)
            }
        }

        fun resetError() {
            _uiState.value = CameraXMedia3EffectsUiState.Previewing(Media3EffectMode.NONE)
        }
    }
