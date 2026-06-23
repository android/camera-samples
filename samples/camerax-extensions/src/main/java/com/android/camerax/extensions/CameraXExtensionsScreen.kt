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
package com.android.camerax.extensions

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.display.rememberDisplayRotation
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraBackButton
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraOverlayButton
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.preview.CapturedImagePreview
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXExtensionsScreen(
    viewModel: CameraXExtensionsViewModel =
        hiltViewModel(
            checkNotNull(LocalViewModelStoreOwner.current) {
                "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
            },
            null,
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    // Latched from the latest Previewing state so the camera behind a captured-photo overlay keeps
    // its selected extension rather than rebinding to NONE.
    var displayedMode by remember { mutableStateOf(EXTENSION_MODE_NONE) }
    var displayedModes by remember { mutableStateOf(listOf(EXTENSION_MODE_NONE)) }

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO) {
        when (val state = uiState) {
            CameraXExtensionsUiState.Initial -> {
                LoadingView()
            }

            is CameraXExtensionsUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXExtensionsUiState.Previewing -> {
                displayedMode = state.currentMode
                displayedModes = state.availableModes
                CapturingContent(
                    currentMode = state.currentMode,
                    availableModes = state.availableModes,
                    onPhotoCaptured = viewModel::processImage,
                    onModesReady = viewModel::setAvailableModes,
                    onExtensionSelected = viewModel::setExtension,
                    onBack = onBack,
                )
            }

            is CameraXExtensionsUiState.PhotoCaptured -> {
                CapturingContent(
                    currentMode = displayedMode,
                    availableModes = displayedModes,
                    onPhotoCaptured = viewModel::processImage,
                    onModesReady = viewModel::setAvailableModes,
                    onExtensionSelected = viewModel::setExtension,
                    onBack = onBack,
                )
                CapturedImagePreview(bitmap = state.photoBitmap, onDismiss = viewModel::resetToCamera)
            }
        }
    }
}

// ExtensionMode.NONE is 0; kept here so this when-branch needn't import the extensions library.
private const val EXTENSION_MODE_NONE = 0

@Composable
private fun BoxScope.CapturingContent(
    currentMode: Int,
    availableModes: List<Int>,
    onPhotoCaptured: (ImageProxy) -> Unit,
    onModesReady: (List<Int>) -> Unit,
    onExtensionSelected: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXExtensionsController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            initialMode = currentMode,
            onPhotoCaptured = onPhotoCaptured,
            onModesReady = onModesReady,
        )
    val displayRotation = rememberDisplayRotation()

    var settingsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(displayRotation, controller) {
        controller.updateTargetRotation(displayRotation)
    }

    LaunchedEffect(currentMode, controller) {
        controller.setExtension(currentMode)
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_RESUME -> {
                        controller.openCamera()
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        controller.closeCamera()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    controller.surfaceRequest?.let { request ->
        CameraXPreview(
            surfaceRequest = request,
            onTapToFocus = { surfaceCoords, width, height ->
                controller.focus(surfaceCoords, width, height)
            },
        )
    }

    CameraBackButton(
        onClick = onBack,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )

    CameraOverlayButton(
        onClick = { settingsVisible = true },
        imageVector = Icons.Filled.AutoAwesome,
        contentDescription = "Extension mode",
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        center = { ShutterButton(onClick = controller::takePicture) },
    )

    SettingsOverlay(
        visible = settingsVisible,
        onDismiss = { settingsVisible = false },
    ) {
        SettingsDropdown(
            label = "Extension",
            options = availableModes,
            selected = currentMode,
            onSelected = { mode ->
                onExtensionSelected(mode)
                controller.setExtension(mode)
            },
            optionLabel = ::extensionModeLabel,
        )
    }
}
