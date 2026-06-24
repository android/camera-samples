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
package com.android.camera2.manualcontrols

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Switch
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
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.controls.ValueSlider
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.overlay.SettingsRow
import com.android.camera.coreui.overlay.settingsSwitchColors
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView
import kotlin.math.roundToInt

@Composable
fun Camera2ManualControlsScreen(
    viewModel: Camera2ManualControlsViewModel =
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
            Camera2ManualControlsUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.manualcontrols_unsupported))
            }

            is Camera2ManualControlsUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            // Initial and Previewing both host the camera so the controller can report the device's
            // supported ranges (or flip to Unsupported); a spinner shows until those arrive.
            else -> {
                CameraContent(
                    previewing = state as? Camera2ManualControlsUiState.Previewing,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.CameraContent(
    previewing: Camera2ManualControlsUiState.Previewing?,
    viewModel: Camera2ManualControlsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2ManualControlsController(
            context = context,
            onUnsupported = viewModel::markUnsupported,
            onCameraReady = viewModel::onCameraReady,
        )

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.manualcontrols_back),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )

    if (previewing == null) {
        // Camera is opening / reporting its capabilities.
        LoadingView()
        return
    }

    val state = previewing

    // Push UI state changes down into the controller's repeating request.
    LaunchedEffect(controller, state.manualEnabled) {
        controller.setManualEnabled(state.manualEnabled)
    }
    LaunchedEffect(controller, state.iso) { controller.setIso(state.iso) }
    LaunchedEffect(controller, state.exposureNs) { controller.setExposure(state.exposureNs) }
    LaunchedEffect(controller, state.focusDistance) { controller.setFocusDistance(state.focusDistance) }

    var settingsVisible by remember { mutableStateOf(false) }

    ScrimIconButton(
        onClick = { settingsVisible = true },
        imageVector = Icons.Filled.Tune,
        contentDescription = stringResource(R.string.manualcontrols_settings),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
    )

    SettingsOverlay(visible = settingsVisible, onDismiss = { settingsVisible = false }) {
        SettingsHeader(text = stringResource(R.string.manualcontrols_settings_header))
        SettingsRow(label = stringResource(R.string.manualcontrols_manual_label)) {
            Switch(
                checked = state.manualEnabled,
                onCheckedChange = viewModel::setManualEnabled,
                colors = settingsSwitchColors(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ValueSlider(
            label = stringResource(R.string.manualcontrols_iso_label),
            value = state.iso.toFloat(),
            onValueChange = { viewModel.setIso(it.roundToInt()) },
            valueRange = state.isoRange.first.toFloat()..state.isoRange.second.toFloat(),
            enabled = state.manualEnabled,
            valueLabel = state.iso.toString(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        ValueSlider(
            label = stringResource(R.string.manualcontrols_shutter_label),
            value = state.exposureNs.toFloat(),
            onValueChange = { viewModel.setExposure(it.toLong()) },
            valueRange = state.exposureRangeNs.first.toFloat()..state.exposureRangeNs.second.toFloat(),
            enabled = state.manualEnabled,
            valueLabel = formatShutter(context, state.exposureNs),
        )

        Spacer(modifier = Modifier.height(8.dp))

        ValueSlider(
            label = stringResource(R.string.manualcontrols_focus_label),
            value = state.focusDistance,
            onValueChange = viewModel::setFocusDistance,
            valueRange = 0f..state.minFocusDistance.coerceAtLeast(0.01f),
            enabled = state.manualEnabled && state.minFocusDistance > 0f,
            valueLabel = stringResource(R.string.manualcontrols_focus_value, "%.1f".format(state.focusDistance)),
        )
    }
}

/** Formats an exposure time in nanoseconds as a "1/Ns" shutter-speed label. */
private fun formatShutter(
    context: Context,
    exposureNs: Long,
): String {
    if (exposureNs <= 0L) return context.getString(R.string.manualcontrols_shutter_unavailable)
    val denominator = (1e9 / exposureNs).roundToInt()
    return if (denominator >= 1) {
        context.getString(R.string.manualcontrols_shutter_fraction, denominator)
    } else {
        context.getString(R.string.manualcontrols_shutter_seconds, (exposureNs / 1_000_000_000.0).toString())
    }
}
