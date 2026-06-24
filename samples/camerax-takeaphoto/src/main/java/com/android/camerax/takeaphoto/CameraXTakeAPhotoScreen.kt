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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.overlay.FocusIndicator
import com.android.camera.coreui.overlay.RuleOfThirdsGrid
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.preview.CapturedImagePreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

private const val SAMPLE_TITLE = "Take a Photo"

@Composable
fun CameraXTakeAPhotoScreen(
    viewModel: CameraXTakeAPhotoViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            CameraXTakeAPhotoUiState.Initial -> {
                LoadingView()
            }

            is CameraXTakeAPhotoUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXTakeAPhotoUiState.Capturing -> {
                CapturingContent(
                    isFrontCamera = state.isFrontCamera,
                    onPhotoCaptured = viewModel::processImage,
                    onSwapCamera = viewModel::swapCamera,
                    onBack = onBack,
                )
            }

            is CameraXTakeAPhotoUiState.PhotoCaptured -> {
                CapturingContent(
                    isFrontCamera = state.isFrontCamera,
                    onPhotoCaptured = viewModel::processImage,
                    onSwapCamera = viewModel::swapCamera,
                    onBack = onBack,
                )
                CapturedImagePreview(
                    bitmap = state.photoBitmap,
                    onRetake = viewModel::resetToCamera,
                    onDone = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    isFrontCamera: Boolean,
    onPhotoCaptured: (ImageProxy) -> Unit,
    onSwapCamera: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXTakeAPhotoController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            isFrontCamera = isFrontCamera,
            onPhotoCaptured = onPhotoCaptured,
        )
    val displayRotation = rememberDisplayRotation()
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(displayRotation, controller) {
        controller.updateTargetRotation(displayRotation)
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
            onFocusTap = { focusPoint = it },
        )
    }

    RuleOfThirdsGrid()
    FocusIndicator(tapOffset = focusPoint)

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.Filled.Close,
        contentDescription = "Close",
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
    )

    ViewfinderTitleChip(
        text = SAMPLE_TITLE,
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 44.dp),
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        endSlot = {
            ScrimIconButton(
                onClick = onSwapCamera,
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Swap Camera",
            )
        },
        center = { ShutterButton(onClick = controller::takePicture) },
    )
}
