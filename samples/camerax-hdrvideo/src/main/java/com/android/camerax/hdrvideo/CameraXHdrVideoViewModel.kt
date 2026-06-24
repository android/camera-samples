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
package com.android.camerax.hdrvideo

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CameraXHdrVideoViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXHdrVideoUiState>(CameraXHdrVideoUiState.Initial)
        val uiState: StateFlow<CameraXHdrVideoUiState> = _uiState.asStateFlow()

        private val isFrontCamera = false
        private var selectedRange = HdrDynamicRange.SDR
        private var supportedRanges = listOf(HdrDynamicRange.SDR)
        private var quality = VideoQuality.HD

        fun initialize() {
            if (_uiState.value is CameraXHdrVideoUiState.Initial) {
                _uiState.value = previewing()
            }
        }

        /**
         * Result of the controller's device scan. The dropdown only offers [ranges]; if none are 10-bit
         * HDR (e.g. on an emulator), this is an HDR sample with nothing to show, so go [Unsupported].
         * Otherwise default to the first HDR range — matching how jetpack-camera-app opens in HDR.
         */
        fun onRangesScanned(ranges: List<HdrDynamicRange>) {
            if (ranges.none { it.isHdr }) {
                _uiState.value = CameraXHdrVideoUiState.Unsupported
                return
            }
            supportedRanges = ranges
            selectedRange = ranges.first { it.isHdr }
            if (_uiState.value !is CameraXHdrVideoUiState.Recording) {
                _uiState.value = previewing()
            }
        }

        fun updateRange(range: HdrDynamicRange) {
            selectedRange = range
            if (_uiState.value is CameraXHdrVideoUiState.Previewing) {
                _uiState.value = previewing()
            }
        }

        fun updateQuality(newQuality: VideoQuality) {
            quality = newQuality
            if (_uiState.value is CameraXHdrVideoUiState.Previewing) {
                _uiState.value = previewing()
            }
        }

        fun startRecording() {
            _uiState.value =
                CameraXHdrVideoUiState.Recording(isFrontCamera, selectedRange, supportedRanges, quality)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                CameraXHdrVideoUiState.VideoCaptured(
                    uri,
                    isFrontCamera,
                    selectedRange,
                    supportedRanges,
                    quality,
                )
        }

        fun resetToCamera() {
            _uiState.value = previewing()
        }

        fun resetError() {
            _uiState.value = previewing()
        }

        private fun previewing() = CameraXHdrVideoUiState.Previewing(isFrontCamera, selectedRange, supportedRanges, quality)
    }
