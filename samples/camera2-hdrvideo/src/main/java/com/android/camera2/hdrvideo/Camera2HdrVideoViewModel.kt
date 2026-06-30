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
package com.android.camera2.hdrvideo

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
class Camera2HdrVideoViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<Camera2HdrVideoUiState>(Camera2HdrVideoUiState.Initial)
        val uiState: StateFlow<Camera2HdrVideoUiState> = _uiState.asStateFlow()

        private val _events = Channel<SaveEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        // Range selections live in the UiState; read them back out for each transition.
        private val selectedRange: HdrDynamicRange
            get() =
                when (val state = _uiState.value) {
                    is Camera2HdrVideoUiState.Previewing -> state.selectedRange
                    is Camera2HdrVideoUiState.Recording -> state.selectedRange
                    is Camera2HdrVideoUiState.VideoCaptured -> state.selectedRange
                    else -> HdrDynamicRange.SDR
                }

        private val supportedRanges: List<HdrDynamicRange>
            get() =
                when (val state = _uiState.value) {
                    is Camera2HdrVideoUiState.Previewing -> state.supportedRanges
                    is Camera2HdrVideoUiState.Recording -> state.supportedRanges
                    is Camera2HdrVideoUiState.VideoCaptured -> state.supportedRanges
                    else -> listOf(HdrDynamicRange.SDR)
                }

        fun initialize() {
            if (_uiState.value is Camera2HdrVideoUiState.Initial) {
                _uiState.value = previewing()
            }
        }

        /**
         * Result of the controller's profile scan (called off the main thread — [MutableStateFlow] is
         * safe). No 10-bit HDR profile (or API < 33) means this HDR sample has nothing to show, so go
         * [Unsupported]; otherwise default to the first HDR profile.
         */
        fun onRangesScanned(ranges: List<HdrDynamicRange>) {
            if (ranges.none { it.isHdr }) {
                _uiState.value = Camera2HdrVideoUiState.Unsupported
                return
            }
            val range =
                if (selectedRange in ranges && selectedRange.isHdr) {
                    selectedRange
                } else {
                    ranges.first { it.isHdr }
                }
            if (_uiState.value !is Camera2HdrVideoUiState.Recording) {
                _uiState.value = Camera2HdrVideoUiState.Previewing(range, ranges)
            }
        }

        fun updateRange(range: HdrDynamicRange) {
            val state = _uiState.value
            if (state is Camera2HdrVideoUiState.Previewing) {
                _uiState.value = state.copy(selectedRange = range)
            }
        }

        fun startRecording() {
            _uiState.value = Camera2HdrVideoUiState.Recording(selectedRange, supportedRanges)
        }

        fun videoCaptured(uri: Uri) {
            _uiState.value =
                Camera2HdrVideoUiState.VideoCaptured(uri, selectedRange, supportedRanges)
            _events.trySend(SaveEvent.Saved)
        }

        /** Discards the just-saved clip from the gallery and returns to the viewfinder. */
        fun retake() {
            (_uiState.value as? Camera2HdrVideoUiState.VideoCaptured)?.let {
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

        private fun previewing() = Camera2HdrVideoUiState.Previewing(selectedRange, supportedRanges)
    }
