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
package com.android.camera2.takeavideo

import android.util.Size
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import java.io.File

@Composable
fun Camera2TakeAVideoScreen(
    viewModel: Camera2TakeAVideoViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO, api = CameraApi.CAMERA2) {
        // Previewing / Recording / VideoCaptured share a SINGLE CapturingContent call site so the
        // camera controller (and an in-progress recording) survive the state transitions. Separate
        // when-branches would give each its own composition identity, disposing and recreating the
        // controller when recording starts — which instantly kills the recording.
        when (val state = uiState) {
            is Camera2TakeAVideoUiState.Initial -> {
                LoadingView()
            }

            is Camera2TakeAVideoUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val isFrontCamera =
                    when (state) {
                        is Camera2TakeAVideoUiState.Previewing -> state.isFrontCamera
                        is Camera2TakeAVideoUiState.Recording -> state.isFrontCamera
                        is Camera2TakeAVideoUiState.VideoCaptured -> state.isFrontCamera
                        else -> false
                    }
                val config =
                    when (state) {
                        is Camera2TakeAVideoUiState.Previewing -> state.config
                        is Camera2TakeAVideoUiState.Recording -> state.config
                        is Camera2TakeAVideoUiState.VideoCaptured -> state.config
                        else -> CameraVideoConfig()
                    }
                val isOverlayVisible =
                    when (state) {
                        is Camera2TakeAVideoUiState.Previewing -> state.isOverlayVisible
                        is Camera2TakeAVideoUiState.Recording -> state.isOverlayVisible
                        else -> false
                    }
                CapturingContent(
                    isFrontCamera = isFrontCamera,
                    config = config,
                    isRecording = state is Camera2TakeAVideoUiState.Recording,
                    isOverlayVisible = isOverlayVisible,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is Camera2TakeAVideoUiState.VideoCaptured) {
                    CapturedVideoPreview(uri = state.videoUri, onDismiss = viewModel::resetToCamera)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    isFrontCamera: Boolean,
    config: CameraVideoConfig,
    isRecording: Boolean,
    isOverlayVisible: Boolean,
    viewModel: Camera2TakeAVideoViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2TakeAVideoController(
            context = context,
            isFrontCamera = isFrontCamera,
            config = config,
            onVideoCaptured = { file -> viewModel.videoCaptured(file.toUri()) },
        )

    LaunchedEffect(config) { controller.updateConfig(config) }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

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
            contentDescription = "Back",
            size = 34.dp,
            iconSize = 18.dp,
        )
        if (!isRecording) {
            ScrimIconButton(
                onClick = viewModel::toggleOverlay,
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                size = 34.dp,
                iconSize = 18.dp,
            )
        }
    }

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        endSlot = { if (!isRecording) CameraSwitchButton(onClick = viewModel::swapCamera) },
        center = {
            RecordButton(
                isRecording = isRecording,
                onClick = {
                    if (isRecording) {
                        controller.stopRecording()
                    } else {
                        viewModel.startRecording()
                        controller.startRecording()
                    }
                },
            )
        },
    )

    SettingsOverlay(visible = isOverlayVisible && !isRecording, onDismiss = viewModel::toggleOverlay) {
        VideoSettings(config = config, onConfigUpdate = viewModel::updateConfig)
    }
}

@Composable
private fun VideoSettings(
    config: CameraVideoConfig,
    onConfigUpdate: ((CameraVideoConfig) -> CameraVideoConfig) -> Unit,
) {
    SettingsHeader(text = "Recording settings")

    SettingsDropdown(
        label = "FPS",
        options = listOf(24, 30, 60),
        selected = config.fps,
        onSelected = { fps -> onConfigUpdate { it.copy(fps = fps) } },
        optionLabel = { "$it" },
    )
    SettingsDropdown(
        label = "Codec",
        options = listOf(0, 1, 2),
        selected = config.videoCodec,
        onSelected = { codec -> onConfigUpdate { it.copy(videoCodec = codec) } },
        optionLabel = { codecLabel(it) },
    )
    SettingsDropdown(
        label = "Resolution",
        options = listOf(Size(1280, 720), Size(1920, 1080), Size(3840, 2160)),
        selected = config.size,
        onSelected = { size -> onConfigUpdate { it.copy(size = size) } },
        optionLabel = { "${it.width}x${it.height}" },
    )
}

private fun codecLabel(codec: Int): String =
    when (codec) {
        0 -> "HEVC"
        1 -> "H264"
        2 -> "AV1"
        else -> "H264"
    }
