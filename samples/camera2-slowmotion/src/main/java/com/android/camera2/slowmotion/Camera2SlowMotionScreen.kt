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
package com.android.camera2.slowmotion

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.feedback.ObserveSaveEvents
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView

@Composable
fun Camera2SlowMotionScreen(
    viewModel: Camera2SlowMotionViewModel =
        hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    ObserveSaveEvents(viewModel.events)

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO, api = CameraApi.CAMERA2) {
        when (val state = uiState) {
            is Camera2SlowMotionUiState.Initial -> {
                LoadingView()
            }

            is Camera2SlowMotionUiState.Unsupported -> {
                UnsupportedView(
                    message = stringResource(R.string.slowmotion_unsupported),
                )
            }

            is Camera2SlowMotionUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            // Previewing / Recording / VideoCaptured share a SINGLE CapturingContent call site so
            // the camera controller (and an in-progress recording) survive the state transitions
            // instead of being disposed and recreated when recording starts.
            else -> {
                CapturingContent(
                    isRecording = state is Camera2SlowMotionUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is Camera2SlowMotionUiState.VideoCaptured) {
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
    isRecording: Boolean,
    viewModel: Camera2SlowMotionViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2SlowMotionController(
            context = context,
            onUnsupported = viewModel::markUnsupported,
            onVideoCaptured = viewModel::videoCaptured,
        )

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

    ViewfinderTopBar(
        title = stringResource(R.string.slowmotion_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
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
}
