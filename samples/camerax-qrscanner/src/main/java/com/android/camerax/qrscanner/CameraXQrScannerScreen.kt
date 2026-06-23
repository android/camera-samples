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
package com.android.camerax.qrscanner

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun CameraXQrScannerScreen(
    viewModel: CameraXQrScannerViewModel =
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
            CameraXQrScannerUiState.Initial -> {
                LoadingView()
            }

            is CameraXQrScannerUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXQrScannerUiState.Scanning -> {
                ScanningContent(
                    state = state,
                    onBarcodes = viewModel::setBarcodes,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ScanningContent(
    state: CameraXQrScannerUiState.Scanning,
    onBarcodes: (List<DetectedBarcode>, Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXQrScannerController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onBarcodes = onBarcodes,
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

    controller.surfaceRequest?.let { request ->
        CameraXPreview(surfaceRequest = request)
    }

    BarcodeOverlay(
        barcodes = state.barcodes,
        sourceWidth = state.sourceWidth,
        sourceHeight = state.sourceHeight,
        modifier = Modifier.fillMaxSize(),
    )

    val latestValue = state.barcodes.firstOrNull { it.value.isNotEmpty() }?.value
    if (latestValue != null) {
        BarcodeValueChip(
            value = latestValue,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
        )
    }

    CameraBackButton(
        onClick = onBack,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
    )
}

/**
 * Draws each detected barcode's bounding box on top of the live preview. The analysis frame is
 * [sourceWidth] x [sourceHeight]; each box is scaled from that space into the Canvas size with a
 * simple per-axis scale. The source and view aspect ratios may differ, so the boxes are
 * representative rather than pixel-perfect.
 */
@Composable
private fun BarcodeOverlay(
    barcodes: List<DetectedBarcode>,
    sourceWidth: Int,
    sourceHeight: Int,
    modifier: Modifier = Modifier,
) {
    if (sourceWidth <= 0 || sourceHeight <= 0) return

    val strokeWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    val cornerRadiusPx = with(LocalDensity.current) { 8.dp.toPx() }

    Canvas(modifier = modifier) {
        val scaleX = size.width / sourceWidth
        val scaleY = size.height / sourceHeight
        barcodes.forEach { barcode ->
            val bounds = barcode.bounds ?: return@forEach
            drawRoundRect(
                color = Color.Green,
                topLeft =
                    Offset(
                        x = bounds.left * scaleX,
                        y = bounds.top * scaleY,
                    ),
                size =
                    Size(
                        width = bounds.width() * scaleX,
                        height = bounds.height() * scaleY,
                    ),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = strokeWidthPx),
            )
        }
    }
}

/** A pill showing the most recently decoded barcode value. */
@Composable
private fun BarcodeValueChip(
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
