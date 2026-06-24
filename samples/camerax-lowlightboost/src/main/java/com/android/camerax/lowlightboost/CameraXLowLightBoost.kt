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
package com.android.camerax.lowlightboost

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView

@Composable
fun CameraXLowLightBoost(
    viewModel: CameraXLowLightBoostViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            CameraXLowLightBoostUiState.Initial -> {
                LoadingView()
            }

            CameraXLowLightBoostUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.lowlightboost_unsupported))
            }

            is CameraXLowLightBoostUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXLowLightBoostUiState.Previewing -> {
                PreviewingContent(state = state, viewModel = viewModel, onBack = onBack)
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(
    state: CameraXLowLightBoostUiState.Previewing,
    viewModel: CameraXLowLightBoostViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXLowLightBoostController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onCameraReady = viewModel::onCameraReady,
        )

    LaunchedEffect(controller, state.enabled) { controller.setEnabled(state.enabled) }

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
        imageVector = Icons.Filled.Close,
        contentDescription = stringResource(R.string.lowlightboost_close),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
    )

    ViewfinderTitleChip(
        text = stringResource(R.string.lowlightboost_title),
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 44.dp),
    )

    LowLightChip(
        on = state.enabled,
        onToggle = viewModel::toggle,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
    )
}

@Composable
private fun LowLightChip(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f)
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.WbSunny,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text =
                stringResource(
                    if (on) R.string.lowlightboost_chip_on else R.string.lowlightboost_chip,
                ).uppercase(),
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 10.sp, letterSpacing = 0.1.em),
            color = tint,
        )
    }
}
