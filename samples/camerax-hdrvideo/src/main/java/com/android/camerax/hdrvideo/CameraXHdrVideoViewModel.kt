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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.android.camera.core.media.MediaStoreSaver
import com.android.camera.coreui.feedback.SaveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class CameraXHdrVideoViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<CameraXHdrVideoUiState>(CameraXHdrVideoUiState.Initial)
        val uiState: StateFlow<CameraXHdrVideoUiState> = _uiState.asStateFlow()

        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        private val isFrontCamera = false

        // Range/quality selections live in the UiState; read them back out for each transition.
        private val selectedRange: HdrDynamicRange
            get() =
                when (val state = _uiState.value) {
                    is CameraXHdrVideoUiState.Previewing -> state.selectedRange
                    is CameraXHdrVideoUiState.Recording -> state.selectedRange
                    is CameraXHdrVideoUiState.VideoCaptured -> state.selectedRange
                    else -> HdrDynamicRange.SDR
                }

        private val supportedRanges: List<HdrDynamicRange>
            get() =
                when (val state = _uiState.value) {
                    is CameraXHdrVideoUiState.Previewing -> state.supportedRanges
                    is CameraXHdrVideoUiState.Recording -> state.supportedRanges
                    is CameraXHdrVideoUiState.VideoCaptured -> state.supportedRanges
                    else -> listOf(HdrDynamicRange.SDR)
                }

        private val quality: VideoQuality
            get() =
                when (val state = _uiState.value) {
                    is CameraXHdrVideoUiState.Previewing -> state.quality
                    is CameraXHdrVideoUiState.Recording -> state.quality
                    is CameraXHdrVideoUiState.VideoCaptured -> state.quality
                    else -> VideoQuality.HD
                }

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
            if (_uiState.value !is CameraXHdrVideoUiState.Recording) {
                _uiState.value =
                    CameraXHdrVideoUiState.Previewing(
                        isFrontCamera,
                        ranges.first { it.isHdr },
                        ranges,
                        quality,
                    )
            }
        }

        fun updateRange(range: HdrDynamicRange) {
            val state = _uiState.value
            if (state is CameraXHdrVideoUiState.Previewing) {
                _uiState.value = state.copy(selectedRange = range)
            }
        }

        fun updateQuality(newQuality: VideoQuality) {
            val state = _uiState.value
            if (state is CameraXHdrVideoUiState.Previewing) {
                _uiState.value = state.copy(quality = newQuality)
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
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? CameraXHdrVideoUiState.VideoCaptured)?.let {
                MediaStoreSaver.deleteMedia(context, it.videoUri)
            }
            resetToCamera()
        }

        fun resetToCamera() {
            _uiState.value = previewing()
        }

        fun resetError() {
            _uiState.value = previewing()
        }

        private fun previewing() = CameraXHdrVideoUiState.Previewing(isFrontCamera, selectedRange, supportedRanges, quality)
    }
