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
package com.android.camera2.rawcapture

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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.overlay.RuleOfThirdsGrid
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView

@Composable
fun Camera2RawCaptureScreen(
    viewModel: Camera2RawCaptureViewModel =
        hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERA2) {
        when (val state = uiState) {
            Camera2RawCaptureUiState.Initial -> {
                LoadingView()
            }

            Camera2RawCaptureUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.rawcapture_unsupported))
            }

            is Camera2RawCaptureUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            Camera2RawCaptureUiState.Previewing -> {
                PreviewingContent(
                    onDngSaved = viewModel::onDngSaved,
                    onUnsupported = viewModel::setUnsupported,
                    onBack = onBack,
                )
            }

            is Camera2RawCaptureUiState.Editing -> {
                RawEditorContent(
                    dngUri = state.dngUri,
                    rotationDegrees = state.rotationDegrees,
                    onBack = viewModel::backToCamera,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(
    onDngSaved: (android.net.Uri, Int) -> Unit,
    onUnsupported: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2RawCaptureController(
            context = context,
            isFrontCamera = false,
            onDngSaved = onDngSaved,
            onUnsupported = onUnsupported,
        )

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

    RuleOfThirdsGrid()

    ViewfinderTopBar(
        title = stringResource(R.string.rawcapture_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        center = { ShutterButton(onClick = controller::captureRaw) },
    )
}
