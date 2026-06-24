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
package com.android.camerax.videostabilization

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
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
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXVideoStabilization(
    viewModel: CameraXVideoStabilizationViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            CameraXVideoStabilizationUiState.Initial -> {
                LoadingView()
            }

            is CameraXVideoStabilizationUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val stabilization =
                    when (state) {
                        is CameraXVideoStabilizationUiState.Previewing -> state.stabilization
                        is CameraXVideoStabilizationUiState.Recording -> state.stabilization
                        is CameraXVideoStabilizationUiState.VideoCaptured -> state.stabilization
                        else -> StabilizationMode.OFF
                    }
                val supported =
                    when (state) {
                        is CameraXVideoStabilizationUiState.Previewing -> state.supported
                        is CameraXVideoStabilizationUiState.Recording -> state.supported
                        is CameraXVideoStabilizationUiState.VideoCaptured -> state.supported
                        else -> false
                    }
                val quality =
                    when (state) {
                        is CameraXVideoStabilizationUiState.Previewing -> state.quality
                        is CameraXVideoStabilizationUiState.Recording -> state.quality
                        is CameraXVideoStabilizationUiState.VideoCaptured -> state.quality
                        else -> VideoQuality.HD
                    }
                CapturingContent(
                    stabilization = stabilization,
                    supported = supported,
                    quality = quality,
                    isRecording = state is CameraXVideoStabilizationUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is CameraXVideoStabilizationUiState.VideoCaptured) {
                    CapturedVideoPreview(uri = state.videoUri, onDismiss = viewModel::resetToCamera)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    stabilization: StabilizationMode,
    supported: Boolean,
    quality: VideoQuality,
    isRecording: Boolean,
    viewModel: CameraXVideoStabilizationViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXVideoStabilizationController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            quality = quality,
            stabilization = stabilization,
            onStabilizationSupported = viewModel::onStabilizationSupported,
            onVideoCaptured = viewModel::videoCaptured,
        )
    val displayRotation = rememberDisplayRotation()
    var isOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(displayRotation, controller) { controller.updateTargetRotation(displayRotation) }
    LaunchedEffect(stabilization, controller) { controller.updateStabilization(stabilization) }
    LaunchedEffect(quality, controller) { controller.updateQuality(quality) }

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

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ScrimIconButton(
            onClick = onBack,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.videostabilization_back),
            size = 34.dp,
            iconSize = 18.dp,
        )
        if (!isRecording) {
            ScrimIconButton(
                onClick = { isOverlayVisible = true },
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.videostabilization_settings),
                size = 34.dp,
                iconSize = 18.dp,
            )
        }
    }

    if (stabilization == StabilizationMode.ON) {
        ViewfinderTitleChip(
            text = stringResource(R.string.videostabilization_badge),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 44.dp),
        )
    }

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        endSlot = {},
        center = {
            RecordButton(
                isRecording = isRecording,
                onClick = {
                    if (isRecording) {
                        controller.stopRecording()
                    } else {
                        viewModel.startRecording()
                        controller.startRecording(context)
                    }
                },
            )
        },
    )

    SettingsOverlay(
        visible = isOverlayVisible && !isRecording,
        onDismiss = { isOverlayVisible = false },
    ) {
        SettingsHeader(text = stringResource(R.string.videostabilization_settings_header))
        if (supported) {
            SettingsDropdown(
                label = stringResource(R.string.videostabilization_stab_label),
                options = StabilizationMode.entries,
                selected = stabilization,
                onSelected = viewModel::updateStabilization,
                optionLabel = { context.getString(it.label) },
            )
        }
        SettingsDropdown(
            label = stringResource(R.string.videostabilization_quality_label),
            options = VideoQuality.entries,
            selected = quality,
            onSelected = viewModel::updateQuality,
            optionLabel = { context.getString(it.label) },
        )
    }
}
