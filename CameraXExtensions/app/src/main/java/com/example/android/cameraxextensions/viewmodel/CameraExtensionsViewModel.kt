/*
 * Copyright 2022 The Android Open Source Project
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

package com.example.android.cameraxextensions.viewmodel

import android.app.Application
import android.os.Environment
import androidx.camera.core.*
import androidx.camera.core.CameraSelector.LensFacing
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.android.cameraxextensions.app.CameraExtensionsApplication
import com.example.android.cameraxextensions.model.CameraState
import com.example.android.cameraxextensions.model.CameraUiState
import com.example.android.cameraxextensions.model.CaptureState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.io.File

/**
 * View model for camera extensions. This manages all the operations on the camera.
 * This includes opening and closing the camera, showing the camera preview, capturing a photo,
 * checking which extensions are available, and selecting an extension.
 *
 * Camera UI state is communicated via the cameraUiState flow.
 * Capture UI state is communicated via the captureUiState flow.
 *
 * Rebinding to the UI state flows will always emit the last UI state.
 */
class CameraExtensionsViewModel(application: Application) : AndroidViewModel(application) {
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager

    private val imageCapture = ImageCapture.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .build()

    private val preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .build()

    private val _cameraUiState: MutableStateFlow<CameraUiState> = MutableStateFlow(CameraUiState())
    private val _captureUiState: MutableStateFlow<CaptureState> =
        MutableStateFlow(CaptureState.CaptureNotReady)

    val cameraUiState: Flow<CameraUiState> = _cameraUiState
    val captureUiState: Flow<CaptureState> = _captureUiState

    /**
     * Initializes the camera and checks which extensions are available for the selected camera lens
     * face. If no extensions are available then the selected extension will be set to None and the
     * available extensions list will also contain None.
     * Because this operation is async, clients should wait for cameraUiState to emit
     * CameraState.READY. Once the camera is ready the client can start the preview.
     */
    fun initializeCamera() {
        viewModelScope.launch {
            val currentCameraUiState = _cameraUiState.value

            // get the camera selector for the select lens face
            val cameraSelector = cameraLensToSelector(currentCameraUiState.cameraLens)

            // wait for the camera provider instance and extensions manager instance
            cameraProvider = ProcessCameraProvider.getInstance(getApplication()).await()
            extensionsManager =
                ExtensionsManager.getInstanceAsync(getApplication(), cameraProvider).await()

            val availableCameraLens =
                listOf(
                    CameraSelector.LENS_FACING_BACK,
                    CameraSelector.LENS_FACING_FRONT
                ).filter { lensFacing ->
                    cameraProvider.hasCamera(cameraLensToSelector(lensFacing))
                }

            // get the supported extensions for the selected camera lens by filtering the full list
            // of extensions and checking each one if it's available
            val availableExtensions = listOf(
                ExtensionMode.AUTO,
                ExtensionMode.BOKEH,
                ExtensionMode.HDR,
                ExtensionMode.NIGHT,
                ExtensionMode.FACE_RETOUCH
            ).filter { extensionMode ->
                extensionsManager.isExtensionAvailable(cameraSelector, extensionMode)
            }

            // prepare the new camera UI state which is now in the READY state and contains the list
            // of available extensions, available lens faces.
            val newCameraUiState = currentCameraUiState.copy(
                cameraState = CameraState.READY,
                availableExtensions = listOf(ExtensionMode.NONE) + availableExtensions,
                availableCameraLens = availableCameraLens,
                extensionMode = if (availableExtensions.isEmpty()) ExtensionMode.NONE else currentCameraUiState.extensionMode,
            )
            _cameraUiState.emit(newCameraUiState)
        }
    }

    /**
     * Starts the preview stream. The camera state should be in the READY or PREVIEW_STOPPED state
     * when calling this operation.
     * This process will bind the preview and image capture uses cases to the camera provider.
     */
    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        val currentCameraUiState = _cameraUiState.value
        val cameraSelector = if (currentCameraUiState.extensionMode == ExtensionMode.NONE) {
            cameraLensToSelector(currentCameraUiState.cameraLens)
        } else {
            extensionsManager.getExtensionEnabledCameraSelector(
                cameraLensToSelector(currentCameraUiState.cameraLens),
                currentCameraUiState.extensionMode
            )
        }
        val useCaseGroup = UseCaseGroup.Builder()
            .setViewPort(previewView.viewPort!!)
            .addUseCase(imageCapture)
            .addUseCase(preview)
            .build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            useCaseGroup
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)

        viewModelScope.launch {
            _cameraUiState.emit(_cameraUiState.value.copy(cameraState = CameraState.READY))
            _captureUiState.emit(CaptureState.CaptureReady)
        }
    }

    /**
     * Stops the preview stream. This should be invoked when the captured image is displayed.
     */
    fun stopPreview() {
        preview.setSurfaceProvider(null)
        viewModelScope.launch {
            _cameraUiState.emit(_cameraUiState.value.copy(cameraState = CameraState.PREVIEW_STOPPED))
        }
    }

    /**
     * Toggle the camera lens face. This has no effect if there is only one available camera lens.
     */
    fun switchCamera() {
        val currentCameraUiState = _cameraUiState.value
        if (currentCameraUiState.cameraState == CameraState.READY) {
            // To switch the camera lens, there has to be at least 2 camera lenses
            if (currentCameraUiState.availableCameraLens.size == 1) return

            val camLensFacing = currentCameraUiState.cameraLens
            // Toggle the lens facing
            val newCameraUiState = if (camLensFacing == CameraSelector.LENS_FACING_BACK) {
                currentCameraUiState.copy(cameraLens = CameraSelector.LENS_FACING_FRONT)
            } else {
                currentCameraUiState.copy(cameraLens = CameraSelector.LENS_FACING_BACK)
            }

            viewModelScope.launch {
                _cameraUiState.emit(
                    newCameraUiState.copy(
                        cameraState = CameraState.NOT_READY,
                    )
                )
                _captureUiState.emit(CaptureState.CaptureNotReady)
            }
        }
    }

    /**
     * Captures the photo and saves it to the pictures directory that's inside the app-specific
     * directory on external storage.
     * Upon successful capture, the captureUiState flow will emit CaptureFinished with the URI to
     * the captured photo.
     * If the capture operation failed then captureUiState flow will emit CaptureFailed with the
     * exception containing more details on the reason for failure.
     */
    fun capturePhoto() {
        viewModelScope.launch {
            _captureUiState.emit(CaptureState.CaptureStarted)
        }
        val photoFile = File(getCaptureStorageDirectory(), "test.jpg")
        val metadata = ImageCapture.Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal =
                _cameraUiState.value.cameraLens == CameraSelector.LENS_FACING_FRONT
        }
        val outputFileOptions =
            ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

        imageCapture.takePicture(
            outputFileOptions,
            Dispatchers.Default.asExecutor(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        _captureUiState.emit(CaptureState.CaptureFinished(outputFileResults))
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModelScope.launch {
                        _captureUiState.emit(CaptureState.CaptureFailed(exception))
                    }
                }
            })
    }

    /**
     * Sets the current extension mode. This will force the camera to rebind the use cases.
     */
    fun setExtensionMode(@ExtensionMode.Mode extensionMode: Int) {
        viewModelScope.launch {
            _cameraUiState.emit(
                _cameraUiState.value.copy(
                    cameraState = CameraState.NOT_READY,
                    extensionMode = extensionMode,
                )
            )
            _captureUiState.emit(CaptureState.CaptureNotReady)
        }
    }

    private fun cameraLensToSelector(@LensFacing lensFacing: Int): CameraSelector =
        when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraSelector.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> throw IllegalArgumentException("Invalid lens facing type: $lensFacing")
        }

    private fun getCaptureStorageDirectory(): File {
        // Get the pictures directory that's inside the app-specific directory on
        // external storage.
        val context = getApplication<CameraExtensionsApplication>()

        val file =
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "camera_extensions")
        file.mkdirs()
        return file
    }
}
