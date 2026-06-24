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
package com.android.camera2.zoomandtorch

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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
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

private const val SAMPLE_TITLE = "Zoom & Torch"

@Composable
fun Camera2ZoomAndTorchScreen(
    viewModel: Camera2ZoomAndTorchViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERA2) {
        when (val state = uiState) {
            Camera2ZoomAndTorchUiState.Initial -> {
                LoadingView()
            }

            is Camera2ZoomAndTorchUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is Camera2ZoomAndTorchUiState.Previewing -> {
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
    state: Camera2ZoomAndTorchUiState.Previewing,
    viewModel: Camera2ZoomAndTorchViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2ZoomAndTorchController(
            context = context,
            isFrontCamera = state.isFrontCamera,
            onCameraReady = viewModel::onCameraReady,
        )
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(state.zoomRatio) { controller.setZoom(state.zoomRatio) }
    LaunchedEffect(state.torchOn) { controller.setTorch(state.torchOn) }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(
        controller = controller,
        onFocusTap = { focusPoint = it },
    )

    TorchGlow(visible = state.torchOn)
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
