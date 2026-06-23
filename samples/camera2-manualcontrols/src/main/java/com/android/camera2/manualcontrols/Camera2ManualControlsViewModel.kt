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
package com.android.camera2.manualcontrols

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class Camera2ManualControlsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2ManualControlsUiState>(Camera2ManualControlsUiState.Initial)
        val uiState: StateFlow<Camera2ManualControlsUiState> = _uiState.asStateFlow()

        fun initialize() {
            // The camera controller reports its supported ranges via [onCameraReady]; until then we
            // stay in [Initial] so the screen shows the loading spinner.
        }

        /**
         * Invoked by the controller once the device is prepared. Seeds the manual sensor values with
         * sane mid-range defaults so the sliders start somewhere usable.
         */
        fun onCameraReady(
            supported: Boolean,
            isoRange: Pair<Int, Int>,
            exposureRangeNs: Pair<Long, Long>,
            minFocusDistance: Float,
        ) {
            if (!supported) {
                _uiState.value = Camera2ManualControlsUiState.Unsupported
                return
            }

            // Don't clobber values the user has already started adjusting.
            if (_uiState.value is Camera2ManualControlsUiState.Previewing) return

            val defaultIso = ((isoRange.first + isoRange.second) / 2).coerceIn(isoRange.first, isoRange.second)
            // Default to a hand-holdable ~1/60s exposure when it falls within the supported range.
            val sixtieth = 1_000_000_000L / 60L
            val defaultExposure = sixtieth.coerceIn(exposureRangeNs.first, exposureRangeNs.second)
            // Mid focus distance in diopters (0 = infinity, minFocusDistance = closest).
            val defaultFocus = minFocusDistance / 2f

            _uiState.value =
                Camera2ManualControlsUiState.Previewing(
                    manualEnabled = false,
                    iso = defaultIso,
                    isoRange = isoRange,
                    exposureNs = defaultExposure,
                    exposureRangeNs = exposureRangeNs,
                    focusDistance = defaultFocus,
                    minFocusDistance = minFocusDistance,
                )
        }

        fun setManualEnabled(enabled: Boolean) {
            updatePreviewing { it.copy(manualEnabled = enabled) }
        }

        fun setIso(iso: Int) {
            updatePreviewing { it.copy(iso = iso.coerceIn(it.isoRange.first, it.isoRange.second)) }
        }

        fun setExposure(exposureNs: Long) {
            updatePreviewing {
                it.copy(exposureNs = exposureNs.coerceIn(it.exposureRangeNs.first, it.exposureRangeNs.second))
            }
        }

        fun setFocusDistance(distance: Float) {
            updatePreviewing { it.copy(focusDistance = distance.coerceIn(0f, it.minFocusDistance)) }
        }

        fun markUnsupported() {
            _uiState.value = Camera2ManualControlsUiState.Unsupported
        }

        fun resetError() {
            _uiState.value = Camera2ManualControlsUiState.Initial
        }

        private inline fun updatePreviewing(
            transform: (Camera2ManualControlsUiState.Previewing) -> Camera2ManualControlsUiState.Previewing,
        ) {
            val current = _uiState.value
            if (current is Camera2ManualControlsUiState.Previewing) {
                _uiState.value = transform(current)
            }
        }
    }
