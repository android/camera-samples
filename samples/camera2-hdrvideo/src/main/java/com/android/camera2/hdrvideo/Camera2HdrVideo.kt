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
package com.android.camera2.hdrvideo

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
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
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
import com.android.camera.coreui.state.UnsupportedView
import com.android.camera.coreui.widget.HdrWindowColorMode

@Composable
fun Camera2HdrVideo(
    viewModel: Camera2HdrVideoViewModel =
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
        // controller (and an in-progress recording) survive the transitions — see the take-a-video
        // sample for why splitting them kills the recording.
        when (val state = uiState) {
            Camera2HdrVideoUiState.Initial -> {
                LoadingView()
            }

            Camera2HdrVideoUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.hdrvideo_unsupported))
            }

            is Camera2HdrVideoUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val selectedRange =
                    when (state) {
                        is Camera2HdrVideoUiState.Previewing -> state.selectedRange
                        is Camera2HdrVideoUiState.Recording -> state.selectedRange
                        is Camera2HdrVideoUiState.VideoCaptured -> state.selectedRange
                        else -> HdrDynamicRange.SDR
                    }
                val supportedRanges =
                    when (state) {
                        is Camera2HdrVideoUiState.Previewing -> state.supportedRanges
                        is Camera2HdrVideoUiState.Recording -> state.supportedRanges
                        is Camera2HdrVideoUiState.VideoCaptured -> state.supportedRanges
                        else -> listOf(HdrDynamicRange.SDR)
                    }
                CapturingContent(
                    selectedRange = selectedRange,
                    supportedRanges = supportedRanges,
                    isRecording = state is Camera2HdrVideoUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is Camera2HdrVideoUiState.VideoCaptured) {
                    CapturedVideoPreview(uri = state.videoUri, onDismiss = viewModel::resetToCamera)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    selectedRange: HdrDynamicRange,
    supportedRanges: List<HdrDynamicRange>,
    isRecording: Boolean,
    viewModel: Camera2HdrVideoViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2HdrVideoController(
            context = context,
            onRangesScanned = viewModel::onRangesScanned,
            onVideoCaptured = { file -> viewModel.videoCaptured(file.toUri()) },
            onRecordingError = viewModel::resetToCamera,
        )
    var isOverlayVisible by remember { mutableStateOf(false) }

    HdrWindowColorMode(enabled = selectedRange.isHdr)

    LaunchedEffect(selectedRange, controller) { controller.updateRange(selectedRange) }

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
            contentDescription = stringResource(R.string.hdrvideo_back),
            size = 34.dp,
            iconSize = 18.dp,
        )
        if (!isRecording) {
            ScrimIconButton(
                onClick = { isOverlayVisible = true },
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.hdrvideo_settings),
                size = 34.dp,
                iconSize = 18.dp,
            )
        }
    }

    if (selectedRange.isHdr) {
        ViewfinderTitleChip(
            text = stringResource(R.string.hdrvideo_hdr_badge),
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
                        controller.startRecording()
                    }
                },
            )
        },
    )

    SettingsOverlay(
        visible = isOverlayVisible && !isRecording,
        onDismiss = { isOverlayVisible = false },
    ) {
        SettingsHeader(text = stringResource(R.string.hdrvideo_settings_header))
        SettingsDropdown(
            label = stringResource(R.string.hdrvideo_range_label),
            options = supportedRanges,
            selected = selectedRange,
            onSelected = viewModel::updateRange,
            optionLabel = { context.getString(it.label) },
        )
    }
}
