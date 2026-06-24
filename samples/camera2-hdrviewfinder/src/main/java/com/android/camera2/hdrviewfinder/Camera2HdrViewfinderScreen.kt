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
package com.android.camera2.hdrviewfinder

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun Camera2HdrViewfinderScreen(
    viewModel: Camera2HdrViewfinderViewModel =
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
            Camera2HdrViewfinderUiState.Initial -> {
                LoadingView()
            }

            is Camera2HdrViewfinderUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is Camera2HdrViewfinderUiState.Previewing -> {
                ViewfinderContent(
                    mode = state.mode,
                    onModeSelected = viewModel::setMode,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ViewfinderContent(
    mode: ProcessingMode,
    onModeSelected: (ProcessingMode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller = rememberCamera2HdrViewfinderController(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(controller, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE,
                    Lifecycle.Event.ON_RESUME,
                    -> controller.openCamera()

                    Lifecycle.Event.ON_PAUSE -> controller.closeCamera()

                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    val frame = controller.frame
    if (frame != null) {
        Image(
            bitmap = frame.asImageBitmap(),
            contentDescription = "Processed camera viewfinder",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        LoadingView()
    }

    var isOverlayVisible by remember { mutableStateOf(false) }

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

    ScrimIconButton(
        onClick = { isOverlayVisible = true },
        imageVector = Icons.Filled.Tune,
        contentDescription = "Processing mode",
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
    )

    SettingsOverlay(visible = isOverlayVisible, onDismiss = { isOverlayVisible = false }) {
        SettingsHeader(text = "Processing mode")
        SettingsDropdown(
            label = "Mode",
            options = ProcessingMode.entries,
            selected = mode,
            onSelected = { selected ->
                onModeSelected(selected)
                controller.setMode(selected)
            },
            optionLabel = { modeLabel(it) },
        )
    }
}

private fun modeLabel(mode: ProcessingMode): String =
    when (mode) {
        ProcessingMode.NORMAL -> "Normal"
        ProcessingMode.INVERT -> "Invert"
        ProcessingMode.THRESHOLD -> "Threshold"
        ProcessingMode.POSTERIZE -> "Posterize"
    }
