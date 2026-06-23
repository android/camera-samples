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
package com.android.camerax.takeavideo

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.android.camera.coreui.controls.CameraBackButton
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraOverlayButton
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.RecordButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.preview.CapturedVideoPreview
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXTakeAVideoScreen(
    viewModel: CameraXTakeAVideoViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO) {
        // Previewing / Recording / VideoCaptured share a SINGLE CapturingContent call site so the
        // camera controller (and an in-progress recording) survive the state transitions. Splitting
        // them into separate when-branches would give each its own composition identity, disposing
        // and recreating the controller when recording starts — which instantly kills the recording.
        when (val state = uiState) {
            CameraXTakeAVideoUiState.Initial -> {
                LoadingView()
            }

            is CameraXTakeAVideoUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val isFrontCamera =
                    when (state) {
                        is CameraXTakeAVideoUiState.Previewing -> state.isFrontCamera
                        is CameraXTakeAVideoUiState.Recording -> state.isFrontCamera
                        is CameraXTakeAVideoUiState.VideoCaptured -> state.isFrontCamera
                        else -> false
                    }
                val quality =
                    when (state) {
                        is CameraXTakeAVideoUiState.Previewing -> state.quality
                        is CameraXTakeAVideoUiState.Recording -> state.quality
                        is CameraXTakeAVideoUiState.VideoCaptured -> state.quality
                        else -> VideoQuality.entries.first()
                    }
                CapturingContent(
                    isFrontCamera = isFrontCamera,
                    quality = quality,
                    isRecording = state is CameraXTakeAVideoUiState.Recording,
                    viewModel = viewModel,
                    onBack = onBack,
                )
                if (state is CameraXTakeAVideoUiState.VideoCaptured) {
                    CapturedVideoPreview(uri = state.videoUri, onDismiss = viewModel::resetToCamera)
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
    viewModel: CameraXTakeAVideoViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXTakeAVideoController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            isFrontCamera = isFrontCamera,
            quality = quality,
            onVideoCaptured = viewModel::videoCaptured,
        )
    val displayRotation = rememberDisplayRotation()
    var isOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(displayRotation, controller) {
        controller.updateTargetRotation(displayRotation)
    }

    LaunchedEffect(quality, controller) {
        controller.updateQuality(quality)
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

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CameraBackButton(onClick = onBack)
        if (!isRecording) {
            CameraOverlayButton(
                onClick = { isOverlayVisible = true },
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
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
        VideoSettings(quality = quality, onQualitySelected = viewModel::updateQuality)
    }
}

@Composable
private fun VideoSettings(
    quality: VideoQuality,
    onQualitySelected: (VideoQuality) -> Unit,
) {
    Text(text = "Recording settings", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))

    SettingsDropdown(
        label = "Quality",
        options = VideoQuality.entries,
        selected = quality,
        onSelected = onQualitySelected,
        optionLabel = { it.label },
    )
}
