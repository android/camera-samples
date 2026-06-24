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
package com.android.camera2.extensions

import android.media.Image
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.preview.CapturedImagePreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView

@Composable
fun Camera2ExtensionsScreen(
    viewModel: Camera2ExtensionsViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERA2) {
        when (val state = uiState) {
            Camera2ExtensionsUiState.Initial -> {
                LoadingView()
            }

            Camera2ExtensionsUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.extensions_unsupported))
            }

            is Camera2ExtensionsUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is Camera2ExtensionsUiState.Previewing -> {
                CapturingContent(
                    currentExtension = state.currentExtension,
                    supportedExtensions = state.supportedExtensions,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }

            is Camera2ExtensionsUiState.PhotoCaptured -> {
                CapturingContent(
                    currentExtension = NO_SELECTION,
                    supportedExtensions = emptyList(),
                    viewModel = viewModel,
                    onBack = onBack,
                )
                CapturedImagePreview(
                    bitmap = state.photoBitmap,
                    onRetake = viewModel::resetToCamera,
                    onDone = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CapturingContent(
    currentExtension: Int,
    supportedExtensions: List<Int>,
    viewModel: Camera2ExtensionsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var settingsVisible by remember { mutableStateOf(false) }

    val onPhotoCaptured: (Image, Int) -> Unit = remember(viewModel) { viewModel::processImage }

    val controller =
        rememberCamera2ExtensionsController(
            context = context,
            onUnsupported = viewModel::markUnsupported,
            onSupportedExtensions = viewModel::setSupported,
            onPhotoCaptured = onPhotoCaptured,
        )

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.extensions_back),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )

    ScrimIconButton(
        onClick = { settingsVisible = true },
        imageVector = Icons.Filled.Settings,
        contentDescription = stringResource(R.string.extensions_settings),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        center = { ShutterButton(onClick = controller::capturePhoto) },
    )

    SettingsOverlay(
        visible = settingsVisible,
        onDismiss = { settingsVisible = false },
    ) {
        SettingsHeader(text = stringResource(R.string.extensions_settings_header))
        SettingsDropdown(
            label = stringResource(R.string.extensions_dropdown_label),
            options = supportedExtensions,
            selected = currentExtension,
            onSelected = { extension ->
                viewModel.setExtension(extension)
                controller.setExtension(extension)
            },
            optionLabel = { extension -> context.getString(extensionLabel(extension)) },
        )
    }
}

/** Placeholder selection while the captured-photo overlay is shown over the live preview. */
private const val NO_SELECTION = Camera2ExtensionsViewModel.NO_EXTENSION
