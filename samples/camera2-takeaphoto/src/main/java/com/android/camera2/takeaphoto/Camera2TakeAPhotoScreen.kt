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
package com.android.camera2.takeaphoto

import android.media.Image
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraBackButton
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.preview.CapturedImagePreview
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun Camera2TakeAPhotoScreen(
    viewModel: Camera2TakeAPhotoViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO) {
        when (val state = uiState) {
            Camera2TakeAPhotoUiState.Initial -> {
                LoadingView()
            }

            is Camera2TakeAPhotoUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is Camera2TakeAPhotoUiState.Capturing -> {
                CapturingContent(
                    isFrontCamera = state.isFrontCamera,
                    onPhotoCaptured = viewModel::processImage,
                    onSwapCamera = viewModel::swapCamera,
                    onBack = onBack,
                )
            }

            is Camera2TakeAPhotoUiState.PhotoCaptured -> {
                CapturingContent(
                    isFrontCamera = state.isFrontCamera,
                    onPhotoCaptured = viewModel::processImage,
                    onSwapCamera = viewModel::swapCamera,
                    onBack = onBack,
                )
                CapturedImagePreview(bitmap = state.photoBitmap, onDismiss = viewModel::resetToCamera)
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    isFrontCamera: Boolean,
    onPhotoCaptured: (Image, Int) -> Unit,
    onSwapCamera: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2TakeAPhotoController(
            context = context,
            isFrontCamera = isFrontCamera,
            onPhotoCaptured = onPhotoCaptured,
        )

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

    CameraBackButton(
        onClick = onBack,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        endSlot = { CameraSwitchButton(onClick = onSwapCamera) },
        center = { ShutterButton(onClick = controller::takePicture) },
    )
}
