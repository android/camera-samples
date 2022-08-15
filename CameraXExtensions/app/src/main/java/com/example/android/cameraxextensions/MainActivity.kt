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

package com.example.android.cameraxextensions

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.extensions.ExtensionMode
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.android.cameraxextensions.adapter.CameraExtensionItem
import com.example.android.cameraxextensions.model.CameraState
import com.example.android.cameraxextensions.model.CameraUiAction
import com.example.android.cameraxextensions.model.CaptureState
import com.example.android.cameraxextensions.ui.CameraExtensionsScreen
import com.example.android.cameraxextensions.ui.doOnLaidOut
import com.example.android.cameraxextensions.viewmodel.CameraExtensionsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val extensionName = mapOf(
        ExtensionMode.AUTO to R.string.camera_mode_auto,
        ExtensionMode.NIGHT to R.string.camera_mode_night,
        ExtensionMode.HDR to R.string.camera_mode_hdr,
        ExtensionMode.FACE_RETOUCH to R.string.camera_mode_face_retouch,
        ExtensionMode.BOKEH to R.string.camera_mode_bokeh,
        ExtensionMode.NONE to R.string.camera_mode_none,
    )

    private val cameraExtensionsViewModel: CameraExtensionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val cameraExtensionsScreen = CameraExtensionsScreen(findViewById(R.id.root))

        lifecycleScope.launch {
            cameraExtensionsScreen.action.collectLatest { action ->
                when (action) {
                    is CameraUiAction.SelectCameraExtension -> {
                        cameraExtensionsViewModel.setExtensionMode(action.extension)
                    }
                    CameraUiAction.ShutterButtonClick -> {
                        cameraExtensionsViewModel.capturePhoto()
                    }
                    CameraUiAction.SwitchCameraClick -> {
                        cameraExtensionsViewModel.switchCamera()
                    }
                    CameraUiAction.ClosePhotoPreviewClick -> {
                        cameraExtensionsScreen.hidePhoto()
                        cameraExtensionsScreen.showCameraControls()
                        cameraExtensionsViewModel.startPreview(
                            this@MainActivity as LifecycleOwner, cameraExtensionsScreen.previewView
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            cameraExtensionsViewModel.captureUiState.collectLatest { state ->
                when (state) {
                    CaptureState.CaptureNotReady -> {
                        cameraExtensionsScreen.hidePhoto()
                        cameraExtensionsScreen.enableCameraShutter(true)
                        cameraExtensionsScreen.enableSwitchLens(true)
                    }
                    CaptureState.CaptureReady -> {
                        cameraExtensionsScreen.enableCameraShutter(true)
                        cameraExtensionsScreen.enableSwitchLens(true)
                    }
                    CaptureState.CaptureStarted -> {
                        cameraExtensionsScreen.enableCameraShutter(false)
                        cameraExtensionsScreen.enableSwitchLens(false)
                    }
                    is CaptureState.CaptureFinished -> {
                        cameraExtensionsViewModel.stopPreview()
                        cameraExtensionsScreen.showPhoto(state.outputResults.savedUri)
                        cameraExtensionsScreen.hideCameraControls()
                    }
                    is CaptureState.CaptureFailed -> {
                        cameraExtensionsScreen.showCaptureError("Couldn't take photo")
                        cameraExtensionsViewModel.startPreview(
                            this@MainActivity as LifecycleOwner, cameraExtensionsScreen.previewView
                        )
                        cameraExtensionsScreen.enableCameraShutter(true)
                        cameraExtensionsScreen.enableSwitchLens(true)
                    }
                }
            }
        }

        lifecycleScope.launch {
            cameraExtensionsViewModel.cameraUiState.collectLatest { state ->

                when (state.cameraState) {
                    CameraState.NOT_READY -> {
                        cameraExtensionsScreen.hidePhoto()
                        cameraExtensionsScreen.enableCameraShutter(false)
                        cameraExtensionsScreen.enableSwitchLens(false)
                        cameraExtensionsViewModel.initializeCamera()
                    }
                    CameraState.READY -> {
                        cameraExtensionsScreen.previewView.doOnLaidOut {
                            cameraExtensionsViewModel.startPreview(
                                this@MainActivity as LifecycleOwner,
                                cameraExtensionsScreen.previewView
                            )
                        }
                        cameraExtensionsScreen.setAvailableExtensions(state.availableExtensions.map {
                            CameraExtensionItem(
                                it, getString(extensionName[it]!!), state.extensionMode == it
                            )
                        })
                    }
                    CameraState.PREVIEW_STOPPED -> Unit
                }
            }
        }
    }
}
