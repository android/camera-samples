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
package com.android.camerax.videopauseresume

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXVideoPauseResumeScreen(
    viewModel: CameraXVideoPauseResumeViewModel =
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
        // Previewing / Recording / VideoCaptured share a SINGLE CapturingContent call site so the
        // camera controller (and the in-progress recording) survive the state transitions —
        // including Recording <-> Paused. Splitting them into separate when-branches would dispose
        // and recreate the controller, instantly killing the recording.
        when (val state = uiState) {
            CameraXVideoPauseResumeUiState.Initial -> {
                LoadingView()
            }

            is CameraXVideoPauseResumeUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val isFrontCamera =
                    when (state) {
                        is CameraXVideoPauseResumeUiState.Previewing -> state.isFrontCamera
                        is CameraXVideoPauseResumeUiState.Recording -> state.isFrontCamera
                        is CameraXVideoPauseResumeUiState.VideoCaptured -> state.isFrontCamera
                        else -> false
                    }
                CapturingContent(
                    isFrontCamera = isFrontCamera,
                    recording = state as? CameraXVideoPauseResumeUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is CameraXVideoPauseResumeUiState.VideoCaptured) {
                    CapturedVideoPreview(uri = state.videoUri, onDismiss = viewModel::resetToCamera)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    isFrontCamera: Boolean,
    recording: CameraXVideoPauseResumeUiState.Recording?,
    viewModel: CameraXVideoPauseResumeViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXVideoPauseResumeController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            isFrontCamera = isFrontCamera,
            onVideoCaptured = viewModel::videoCaptured,
            onDuration = viewModel::setDuration,
        )
    val displayRotation = rememberDisplayRotation()

    val isRecording = recording != null
    val isPaused = recording?.paused == true

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
        )
    }

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )

    if (recording != null) {
        ViewfinderTitleChip(
            text = recordingLabel(isPaused, recording.elapsedNanos),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp),
        )
    }

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        startSlot = {
            if (isRecording) {
                ScrimIconButton(
                    onClick = {
                        if (isPaused) {
                            controller.resumeRecording()
                            viewModel.setPaused(false)
                        } else {
                            controller.pauseRecording()
                            viewModel.setPaused(true)
                        }
                    },
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                )
            }
        },
        endSlot = { if (!isRecording) CameraSwitchButton(onClick = viewModel::swapCamera) },
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

/** Formats the recording indicator, e.g. `REC 01:07` or `PAUSED 01:07`. */
private fun recordingLabel(
    paused: Boolean,
    elapsedNanos: Long,
): String {
    val totalSeconds = (elapsedNanos / 1_000_000_000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val clock = "%02d:%02d".format(minutes, seconds)
    return if (paused) "Paused $clock" else "Rec $clock"
}
