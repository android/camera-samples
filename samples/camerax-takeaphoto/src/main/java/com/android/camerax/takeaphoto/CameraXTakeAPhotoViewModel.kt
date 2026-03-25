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
package com.android.camerax.takeaphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
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
class CameraXTakeAPhotoViewModel @Inject constructor() : ViewModel() {

    private val _uiState =
        MutableStateFlow<CameraXTakeAPhotoUiState>(CameraXTakeAPhotoUiState.Initial)
    val uiState: StateFlow<CameraXTakeAPhotoUiState> = _uiState.asStateFlow()

    private var isFrontCamera = false

    fun initialize() {
        if (_uiState.value is CameraXTakeAPhotoUiState.Initial) {
            _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
        }
    }

    fun swapCamera() {
        isFrontCamera = !isFrontCamera
        _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
    }

    fun resetToCamera() {
        _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
    }

    fun processImage(image: ImageProxy) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val buffer: ByteBuffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    
                    val sensorOrientation = image.imageInfo.rotationDegrees

                    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val matrix = Matrix()
                    matrix.postRotate(sensorOrientation.toFloat())

                    if (isFrontCamera) {
                        matrix.postScale(-1f, 1f)
                    }

                    val finalBitmap = Bitmap.createBitmap(
                        originalBitmap,
                        0,
                        0,
                        originalBitmap.width,
                        originalBitmap.height,
                        matrix,
                        true
                    )
                    image.close()
                    finalBitmap
                }

                _uiState.value = CameraXTakeAPhotoUiState.PhotoCaptured(bitmap, isFrontCamera)
            } catch (e: Exception) {
                try {
                    image.close()
                } catch (_: Exception) {
                }
                _uiState.value =
                    CameraXTakeAPhotoUiState.Error("Error processing image: ${e.message}")
            }
        }
    }

    fun resetError() {
        _uiState.value = CameraXTakeAPhotoUiState.Capturing(isFrontCamera)
    }
}
