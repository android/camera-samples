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
package com.android.camerax.media3effects

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXMedia3EffectsScreen(
    viewModel: CameraXMedia3EffectsViewModel =
        hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            CameraXMedia3EffectsUiState.Initial -> {
                LoadingView()
            }

            is CameraXMedia3EffectsUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXMedia3EffectsUiState.Previewing -> {
                PreviewingContent(
                    effect = state.effect,
                    onSelectEffect = viewModel::selectEffect,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(
    effect: Media3EffectMode,
    onSelectEffect: (Media3EffectMode) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXMedia3EffectsController(
            context = context,
            lifecycleOwner = lifecycleOwner,
        )
    var isOverlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(effect, controller) {
        controller.applyEffect(effect)
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

    ViewfinderTopBar(
        title = stringResource(R.string.media3effects_title, stringResource(effect.label)),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            ScrimIconButton(
                onClick = { isOverlayVisible = true },
                imageVector = Icons.Filled.AutoFixHigh,
                contentDescription = stringResource(R.string.media3effects_settings),
                size = 34.dp,
                iconSize = 18.dp,
            )
        },
    )

    SettingsOverlay(
        visible = isOverlayVisible,
        onDismiss = { isOverlayVisible = false },
    ) {
        SettingsHeader(text = stringResource(R.string.media3effects_settings_header))
        SettingsDropdown(
            label = stringResource(R.string.media3effects_effect_label),
            options = Media3EffectMode.entries,
            selected = effect,
            onSelected = onSelectEffect,
            optionLabel = { context.getString(it.label) },
        )
    }
}
