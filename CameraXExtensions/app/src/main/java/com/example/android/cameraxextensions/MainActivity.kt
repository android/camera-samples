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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.extensions.ExtensionMode
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.android.cameraxextensions.adapter.CameraExtensionItem
import com.example.android.cameraxextensions.model.CameraState
import com.example.android.cameraxextensions.model.CameraUiAction
import com.example.android.cameraxextensions.model.CaptureState
import com.example.android.cameraxextensions.model.PermissionState
import com.example.android.cameraxextensions.ui.CameraExtensionsScreen
import com.example.android.cameraxextensions.ui.doOnLaidOut
import com.example.android.cameraxextensions.viewmodel.CameraExtensionsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Displays the camera preview with camera controls and available extensions. Tapping on the shutter
 * button will capture a photo and display the photo.
 */
class MainActivity : AppCompatActivity() {
    private val extensionName = mapOf(
        ExtensionMode.AUTO to R.string.camera_mode_auto,
        ExtensionMode.NIGHT to R.string.camera_mode_night,
        ExtensionMode.HDR to R.string.camera_mode_hdr,
        ExtensionMode.FACE_RETOUCH to R.string.camera_mode_face_retouch,
        ExtensionMode.BOKEH to R.string.camera_mode_bokeh,
        ExtensionMode.NONE to R.string.camera_mode_none,
    )

    // view model for operating on the camera and capturing a photo
    private val cameraExtensionsViewModel: CameraExtensionsViewModel by viewModels()

    // monitors changes in camera permission state
    private lateinit var permissionState: MutableStateFlow<PermissionState>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        // capture screen abstracts the UI logic and exposes simple functions on how to interact
        // with the UI layer.
        val cameraExtensionsScreen = CameraExtensionsScreen(findViewById(R.id.root))

        // initialize the permission state flow with the current camera permission status
        permissionState = MutableStateFlow(getCurrentPermissionState())

        val requestPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    lifecycleScope.launch { permissionState.emit(PermissionState.Granted) }
                } else {
                    lifecycleScope.launch { permissionState.emit(PermissionState.Denied(true)) }
                }
            }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // check the current permission state every time upon the activity is resumed
                permissionState.emit(getCurrentPermissionState())
            }
        }

        // Consumes actions emitted by the UI and performs the appropriate operation associated with
        // the view model or permission flow.
        // Note that this flow is a shared flow and will not emit the last action unlike the state
        // flows exposed by the view model for consuming UI state.
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
                    CameraUiAction.RequestPermissionClick -> requestPermissionsLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }
            }
        }

        // Consume state emitted by the view model to render the Photo Capture state.
        // Upon collecting this state, the last emitted state will be immediately received.
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

        // Because camera state is dependent on the camera permission status, we combine both camera
        // UI state and permission state such that each emission accurately captures the current
        // permission status and camera UI state.
        // The camera permission is always checked to see if it's granted. If it isn't then stop
        // interacting with the camera and display the permission request screen. The user can tap
        // on "Turn On" to request permissions.
        lifecycleScope.launch {
            permissionState.combine(cameraExtensionsViewModel.cameraUiState) { permissionState, cameraUiState ->
                Pair(permissionState, cameraUiState)
            }
                .collectLatest { (permissionState, cameraUiState) ->
                    when (permissionState) {
                        PermissionState.Granted -> {
                            cameraExtensionsScreen.hidePermissionsRequest()
                        }
                        is PermissionState.Denied -> {
                            if (cameraUiState.cameraState != CameraState.PREVIEW_STOPPED) {
                                cameraExtensionsScreen.showPermissionsRequest(permissionState.shouldShowRationale)
                                return@collectLatest
                            }
                        }
                    }

                    when (cameraUiState.cameraState) {
                        CameraState.NOT_READY -> {
                            cameraExtensionsScreen.hidePhoto()
                            cameraExtensionsScreen.showCameraControls()
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
                            cameraExtensionsScreen.setAvailableExtensions(cameraUiState.availableExtensions.map {
                                CameraExtensionItem(
                                    it,
                                    getString(extensionName[it]!!),
                                    cameraUiState.extensionMode == it
                                )
                            })
                            cameraExtensionsScreen.showCameraControls()
                        }
                        CameraState.PREVIEW_STOPPED -> Unit
                    }
                }
        }
    }

    private fun getCurrentPermissionState(): PermissionState {
        val status = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        return if (status == PackageManager.PERMISSION_GRANTED) {
            PermissionState.Granted
        } else {
            PermissionState.Denied(
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.CAMERA
                )
            )
        }
    }
}
