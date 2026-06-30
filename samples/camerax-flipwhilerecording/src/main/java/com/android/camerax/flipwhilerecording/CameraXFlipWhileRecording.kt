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
package com.android.camerax.flipwhilerecording

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
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
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.display.rememberDisplayRotation
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.feedback.ObserveSaveEvents
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXFlipWhileRecording(
    viewModel: CameraXFlipWhileRecordingViewModel =
        hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }
    ObserveSaveEvents(viewModel.events)

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO, api = CameraApi.CAMERAX) {
        // Previewing / Recording / VideoCaptured share a SINGLE CapturingContent call site so the
        // controller (and the in-progress recording) survive every transition — including a lens flip.
        when (val state = uiState) {
            CameraXFlipWhileRecordingUiState.Initial -> {
                LoadingView()
            }

            is CameraXFlipWhileRecordingUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val isFrontCamera =
                    when (state) {
                        is CameraXFlipWhileRecordingUiState.Previewing -> state.isFrontCamera
                        is CameraXFlipWhileRecordingUiState.Recording -> state.isFrontCamera
                        is CameraXFlipWhileRecordingUiState.VideoCaptured -> state.isFrontCamera
                        else -> false
                    }
                val quality =
                    when (state) {
                        is CameraXFlipWhileRecordingUiState.Previewing -> state.quality
                        is CameraXFlipWhileRecordingUiState.Recording -> state.quality
                        is CameraXFlipWhileRecordingUiState.VideoCaptured -> state.quality
                        else -> VideoQuality.HD
                    }
                CapturingContent(
                    isFrontCamera = isFrontCamera,
                    quality = quality,
                    isRecording = state is CameraXFlipWhileRecordingUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is CameraXFlipWhileRecordingUiState.VideoCaptured) {
                    CapturedVideoPreview(
                        uri = state.videoUri,
                        onRetake = viewModel::retake,
                        onDone = onBack,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    isFrontCamera: Boolean,
    quality: VideoQuality,
    isRecording: Boolean,
    viewModel: CameraXFlipWhileRecordingViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXFlipWhileRecordingController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            initialFrontCamera = isFrontCamera,
            quality = quality,
            onVideoCaptured = viewModel::videoCaptured,
        )
    val displayRotation = rememberDisplayRotation()
    var isOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(displayRotation, controller) { controller.updateTargetRotation(displayRotation) }
    LaunchedEffect(quality, controller) { controller.updateQuality(quality) }
    // NOTE: no LaunchedEffect on isFrontCamera — the flip is driven only by the swap button (below),
    // which calls controller.flipCamera() directly so the recording is never interrupted.

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

    ViewfinderTopBar(
        title = stringResource(R.string.flipwhilerecording_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            if (!isRecording) {
                ScrimIconButton(
                    onClick = { isOverlayVisible = true },
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.flipwhilerecording_settings),
                    size = 34.dp,
                    iconSize = 18.dp,
                )
            }
        },
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        // The swap button stays enabled WHILE recording — that's the whole point of this sample.
        endSlot = {
            CameraSwitchButton(
                onClick = {
                    viewModel.swapCamera()
                    controller.flipCamera()
                },
            )
        },
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
        SettingsHeader(text = stringResource(R.string.flipwhilerecording_settings_header))
        SettingsDropdown(
            label = stringResource(R.string.flipwhilerecording_quality_label),
            options = VideoQuality.entries,
            selected = quality,
            onSelected = viewModel::updateQuality,
            optionLabel = { context.getString(it.label) },
        )
    }
}
