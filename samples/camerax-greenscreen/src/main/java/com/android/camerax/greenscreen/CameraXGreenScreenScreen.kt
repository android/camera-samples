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
package com.android.camerax.greenscreen

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView

@Composable
fun CameraXGreenScreenScreen(
    viewModel: CameraXGreenScreenViewModel =
        hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            CameraXGreenScreenUiState.Initial -> {
                LoadingView()
            }

            CameraXGreenScreenUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.greenscreen_unsupported))
            }

            is CameraXGreenScreenUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXGreenScreenUiState.Compositing -> {
                CompositingContent(
                    state = state,
                    onPersonOverlay = viewModel::setPersonOverlay,
                    onUnsupported = viewModel::setUnsupported,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CompositingContent(
    state: CameraXGreenScreenUiState.Compositing,
    onPersonOverlay: (ImageBitmap) -> Unit,
    onUnsupported: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXGreenScreenController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onPersonOverlay = onPersonOverlay,
            onUnsupported = onUnsupported,
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

    // Back camera: the live background, full screen.
    val backRequest = controller.backSurfaceRequest
    if (backRequest != null) {
        CameraXPreview(surfaceRequest = backRequest)
    } else {
        LoadingView()
    }

    // Front camera lives ONLY in the bottom-right tile, with the green-screen segmentation applied:
    // the subject is kept and the rest is transparent, so the full-screen back camera shows through
    // the tile around the person. Tile size matches the Concurrent Camera sample.
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(bottom = 24.dp, end = 16.dp)
                .width(116.dp)
                .height(174.dp)
                .clip(RoundedCornerShape(18.dp)),
    ) {
        state.personOverlay?.let { overlay ->
            Image(
                bitmap = overlay,
                contentDescription = stringResource(R.string.greenscreen_overlay_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }

    ViewfinderTopBar(
        title = stringResource(R.string.greenscreen_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
    )
}
