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
package com.android.camera2.qrscanner

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun Camera2QrScannerScreen(
    viewModel: Camera2QrScannerViewModel =
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
            Camera2QrScannerUiState.Initial -> {
                LoadingView()
            }

            is Camera2QrScannerUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is Camera2QrScannerUiState.Scanning -> {
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
    state: Camera2QrScannerUiState.Scanning,
    onBarcodes: (List<DetectedBarcode>, Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val controller =
        rememberCamera2QrScannerController(
            context = context,
            isFrontCamera = false,
            onBarcodes = onBarcodes,
        )

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

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
}

/**
 * Draws each detected barcode's bounding box on top of the live preview. The analysis frame is
 * [sourceWidth] x [sourceHeight]; each box is scaled into the Canvas with a simple per-axis scale.
 * Source and view aspect ratios may differ, so the boxes are representative rather than pixel-perfect.
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

/** A console pill showing the most recently decoded barcode value. */
@Composable
private fun BarcodeValueChip(
    value: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.9f),
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 12.sp),
        )
    }
}
