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
package com.android.camera2.takeaphoto.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject

@HiltViewModel
class Camera2TakeAPhotoViewModel @Inject constructor() : ViewModel() {

    private val _uiState =
        MutableStateFlow<Camera2TakeAPhotoUiState>(Camera2TakeAPhotoUiState.Initial)
    val uiState: StateFlow<Camera2TakeAPhotoUiState> = _uiState.asStateFlow()

    private var isFrontCamera = false

    fun initialize() {
        if (_uiState.value is Camera2TakeAPhotoUiState.Initial) {
            _uiState.value = Camera2TakeAPhotoUiState.Capturing(isFrontCamera)
        }
    }

    fun swapCamera() {
        isFrontCamera = !isFrontCamera
        _uiState.value = Camera2TakeAPhotoUiState.Capturing(isFrontCamera)
    }

    fun resetToCamera() {
        _uiState.value = Camera2TakeAPhotoUiState.Capturing(isFrontCamera)
    }

    fun processImage(image: Image, sensorOrientation: Int) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val buffer: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val matrix = Matrix()
                    matrix.postRotate(sensorOrientation.toFloat())

                    if (isFrontCamera) {
                        matrix.postScale(-1f, 1f)
                    }

                    Bitmap.createBitmap(
                        originalBitmap,
                        0,
                        0,
                        originalBitmap.width,
                        originalBitmap.height,
                        matrix,
                        true
                    )
                }

                _uiState.value = Camera2TakeAPhotoUiState.PhotoCaptured(bitmap, isFrontCamera)
            } catch (e: Exception) {
                try {
                    image.close()
                } catch (_: Exception) {
                }
                _uiState.value =
                    Camera2TakeAPhotoUiState.Error("Error processing image: ${e.message}")
            }
        }
    }

    fun resetError() {
        _uiState.value = Camera2TakeAPhotoUiState.Capturing(isFrontCamera)
    }
}
