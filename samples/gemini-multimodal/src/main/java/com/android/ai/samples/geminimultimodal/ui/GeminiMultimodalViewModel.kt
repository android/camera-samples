/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.ai.samples.geminimultimodal.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.ai.samples.geminimultimodal.data.GeminiDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GeminiMultimodalViewModel @Inject constructor(private val geminiDataSource: GeminiDataSource) : ViewModel() {

    private val _uiState = MutableStateFlow<GeminiMultimodalUiState>(GeminiMultimodalUiState.Initial)
    val uiState: StateFlow<GeminiMultimodalUiState> = _uiState

    fun generate(bitmap: Bitmap, prompt: String) {
        _uiState.value = GeminiMultimodalUiState.Loading
        viewModelScope.launch {
            try {
                val result = geminiDataSource.generateText(bitmap, prompt)
                _uiState.value = GeminiMultimodalUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = GeminiMultimodalUiState.Error(e.message)
            }
        }
    }

    fun resetError() {
        _uiState.value = GeminiMultimodalUiState.Initial
    }
}
