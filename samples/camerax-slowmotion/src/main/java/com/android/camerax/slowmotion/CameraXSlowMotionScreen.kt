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
package com.android.camerax.slowmotion

import android.util.Range
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.feedback.ObserveSaveEvents
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView

@Composable
fun CameraXSlowMotionScreen(
    viewModel: CameraXSlowMotionViewModel =
        hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    ObserveSaveEvents(viewModel.events)

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO, api = CameraApi.CAMERAX) {
        // Previewing / Recording / VideoCaptured share a SINGLE CapturingContent call site so the
        // controller (and an in-progress recording) survive the state transitions — splitting them
        // into separate when-branches would dispose and recreate the controller when recording
        // starts, which instantly kills the recording.
        when (val state = uiState) {
            CameraXSlowMotionUiState.Initial -> {
                LoadingView()
            }

            CameraXSlowMotionUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.slowmotion_unsupported))
            }

            is CameraXSlowMotionUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val frameRate =
                    when (state) {
                        is CameraXSlowMotionUiState.Previewing -> state.frameRate
                        is CameraXSlowMotionUiState.Recording -> state.frameRate
                        is CameraXSlowMotionUiState.VideoCaptured -> state.frameRate
                        else -> Range(0, 0)
                    }
                CapturingContent(
                    frameRate = frameRate,
                    isRecording = state is CameraXSlowMotionUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is CameraXSlowMotionUiState.VideoCaptured) {
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
    frameRate: Range<Int>,
    isRecording: Boolean,
    viewModel: CameraXSlowMotionViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXSlowMotionController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onSessionConfigured = viewModel::onSessionConfigured,
            onUnsupported = viewModel::setUnsupported,
            onVideoCaptured = viewModel::videoCaptured,
        )

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
        CameraXPreview(surfaceRequest = request)
    }

    ViewfinderTopBar(
        title = stringResource(R.string.slowmotion_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
    )

    if (frameRate.upper > 0) {
        // Show the high-speed frame rate the camera is capturing at, below the top bar.
        ViewfinderTitleChip(
            text = stringResource(R.string.slowmotion_fps_badge, frameRate.upper),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 96.dp),
        )
    }

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
                        controller.startRecording(context)
                    }
                },
            )
        },
    )
}
