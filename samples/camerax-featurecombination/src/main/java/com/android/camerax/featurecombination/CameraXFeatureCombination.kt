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
package com.android.camerax.featurecombination

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
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
import com.android.camera.core.display.rememberDisplayRotation
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.CameraSwitchButton
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

private val PanelSurface = Color(0xFF14160D)
private val ChipIdleBg = Color.White.copy(alpha = 0.05f)
private val ChipIdleBorder = Color.White.copy(alpha = 0.12f)
private val ChipIdleText = Color.White.copy(alpha = 0.85f)
private val UnsupportedRed = Color(0xFFE57373)

@Composable
fun CameraXFeatureCombination(
    viewModel: CameraXFeatureCombinationViewModel =
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

    CameraSampleScaffold(permissions = CameraPermissions.VIDEO, api = CameraApi.CAMERAX) {
        // Loading and Ready share ONE DiagnosticContent call site so the controller (which owns the
        // matrix query and the preview) is created once per lens and survives the Loading → Ready
        // transition without re-querying.
        when (val state = uiState) {
            CameraXFeatureCombinationUiState.Initial -> {
                LoadingView()
            }

            is CameraXFeatureCombinationUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            else -> {
                val isFrontCamera =
                    when (state) {
                        is CameraXFeatureCombinationUiState.Loading -> state.isFrontCamera
                        is CameraXFeatureCombinationUiState.Ready -> state.isFrontCamera
                        else -> false
                    }
                DiagnosticContent(
                    isFrontCamera = isFrontCamera,
                    ready = state as? CameraXFeatureCombinationUiState.Ready,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.DiagnosticContent(
    isFrontCamera: Boolean,
    ready: CameraXFeatureCombinationUiState.Ready?,
    viewModel: CameraXFeatureCombinationViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXFeatureCombinationController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            isFrontCamera = isFrontCamera,
            onMatrixComputed = viewModel::onMatrixComputed,
        )
    val displayRotation = rememberDisplayRotation()

    LaunchedEffect(displayRotation, controller) { controller.updateTargetRotation(displayRotation) }

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
        CameraXPreview(surfaceRequest = request, onTapToFocus = controller::focus)
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ScrimIconButton(
            onClick = onBack,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.featurecombination_back),
            size = 34.dp,
            iconSize = 18.dp,
        )
        CameraSwitchButton(onClick = viewModel::swapCamera)
    }

    if (ready == null) {
        ViewfinderTitleChip(
            text = stringResource(R.string.featurecombination_querying),
            modifier = Modifier.align(Alignment.Center),
        )
    } else {
        DiagnosticPanel(
            ready = ready,
            onToggle = { toggle ->
                val newSelection =
                    if (toggle in ready.selected) ready.selected - toggle else ready.selected + toggle
                viewModel.onSelectionEvaluated(newSelection, controller.isSupported(newSelection))
            },
            onApply = {
                controller.bindWith(ready.selected)
                viewModel.onApplied()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DiagnosticPanel(
    ready: CameraXFeatureCombinationUiState.Ready,
    onToggle: (FeatureToggle) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(PanelSurface)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp),
    ) {
        SettingsHeader(text = stringResource(R.string.featurecombination_matrix_header))
        ready.matrix.forEach { row -> MatrixRowView(row) }

        Spacer(Modifier.height(18.dp))

        SettingsHeader(text = stringResource(R.string.featurecombination_interactive_header))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeatureToggle.entries.forEach { toggle ->
                FeatureChip(
                    label = context.getString(toggle.label),
                    selected = toggle in ready.selected,
                    onClick = { onToggle(toggle) },
                )
            }
        }

        if (ready.selected.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            if (!ready.currentlySupported) {
                Text(
                    text = stringResource(R.string.featurecombination_not_supported),
                    style = TextStyle(fontFamily = monoFontFamily, fontSize = 12.sp, letterSpacing = 0.04.em),
                    color = UnsupportedRed,
                )
            } else {
                Button(onClick = onApply, enabled = !ready.applied) {
                    Text(
                        text =
                            stringResource(
                                if (ready.applied) {
                                    R.string.featurecombination_applied
                                } else {
                                    R.string.featurecombination_apply
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixRowView(row: MatrixRow) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(row.label),
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 12.sp),
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (row.supported) "✓" else "✗",
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = if (row.supported) accent else Color.White.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun FeatureChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    val styled =
        if (selected) {
            Modifier.clip(shape).background(accent)
        } else {
            Modifier.clip(shape).background(ChipIdleBg).border(1.dp, ChipIdleBorder, shape)
        }
    Box(
        modifier = styled.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else ChipIdleText,
        )
    }
}
