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
package com.android.camerax.exposure

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraBackButton
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.ValueSlider
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import kotlin.math.roundToInt

@Composable
fun CameraXExposureScreen(
    viewModel: CameraXExposureViewModel =
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
            CameraXExposureUiState.Initial -> {
                LoadingView()
            }

            is CameraXExposureUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXExposureUiState.Previewing -> {
                PreviewingContent(
                    state = state,
                    onCameraReady = viewModel::onCameraReady,
                    onExposureChange = viewModel::setExposureIndex,
                    onSwapCamera = viewModel::swapCamera,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(
    state: CameraXExposureUiState.Previewing,
    onCameraReady: (supported: Boolean, minIndex: Int, maxIndex: Int, stepEv: Float) -> Unit,
    onExposureChange: (Int) -> Unit,
    onSwapCamera: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXExposureController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            isFrontCamera = state.isFrontCamera,
            onCameraReady = onCameraReady,
        )

    LaunchedEffect(state.evIndex, controller) {
        controller.setExposureIndex(state.evIndex)
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
        CameraXPreview(surfaceRequest = request)
    }

    CameraBackButton(
        onClick = onBack,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )

    Column(
        modifier = Modifier.align(Alignment.BottomCenter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val sliderEnabled = state.supported && state.minIndex < state.maxIndex
        ValueSlider(
            label = "Exposure (EV)",
            value = state.evIndex.toFloat(),
            onValueChange = { value -> onExposureChange(value.roundToInt()) },
            valueRange = state.minIndex.toFloat()..state.maxIndex.toFloat(),
            steps = (state.maxIndex - state.minIndex - 1).coerceAtLeast(0),
            enabled = sliderEnabled,
            valueLabel = "%+.1f EV".format(state.evIndex * state.stepEv),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        )
        CameraControlsBar(
            modifier = Modifier.fillMaxWidth(),
            endSlot = { CameraSwitchButton(onClick = onSwapCamera) },
            center = {},
        )
    }
}
