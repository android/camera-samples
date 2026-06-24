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
package com.android.camerax.zoomandtorch

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.controls.ZoomControls
import com.android.camera.coreui.overlay.FocusIndicator
import com.android.camera.coreui.overlay.RuleOfThirdsGrid
import com.android.camera.coreui.overlay.TorchChip
import com.android.camera.coreui.overlay.TorchGlow
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXZoomAndTorchScreen(
    viewModel: CameraXZoomAndTorchViewModel =
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
            CameraXZoomAndTorchUiState.Initial -> {
                LoadingView()
            }

            is CameraXZoomAndTorchUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXZoomAndTorchUiState.Previewing -> {
                PreviewingContent(
                    state = state,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(
    state: CameraXZoomAndTorchUiState.Previewing,
    viewModel: CameraXZoomAndTorchViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXZoomAndTorchController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            isFrontCamera = state.isFrontCamera,
            onCameraReady = viewModel::onCameraReady,
        )
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    // Push UI state down into the controller / CameraControl.
    LaunchedEffect(controller, state.zoomRatio) {
        controller.setZoomRatio(state.zoomRatio)
    }
    LaunchedEffect(controller, state.torchOn) {
        controller.setTorch(state.torchOn)
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

    TorchGlow(visible = state.torchOn)
    RuleOfThirdsGrid()
    FocusIndicator(tapOffset = focusPoint)

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.Filled.Close,
        contentDescription = stringResource(R.string.zoomandtorch_close),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
    )

    ViewfinderTitleChip(
        text = stringResource(R.string.zoomandtorch_title),
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 44.dp),
    )

    // Torch toggle only makes sense on a back camera that actually has a flash unit.
    if (state.hasFlash && !state.isFrontCamera) {
        TorchChip(
            on = state.torchOn,
            onToggle = viewModel::toggleTorch,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
        )
    }

    val zoomEnabled = state.minZoom < state.maxZoom
    // Guard against a degenerate range (e.g. before the camera reports its zoom limits, or on a
    // fixed-zoom lens) which would make the Slider divide by zero. Widen it and disable instead.
    val zoomRange =
        if (zoomEnabled) {
            state.minZoom..state.maxZoom
        } else {
            state.minZoom..(state.minZoom + 1f)
        }
    ZoomControls(
        zoomRatio = state.zoomRatio,
        valueRange = zoomRange,
        onValueChange = viewModel::setZoom,
        enabled = zoomEnabled,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 150.dp),
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        endSlot = { CameraSwitchButton(onClick = viewModel::swapCamera) },
        center = {},
    )
}
