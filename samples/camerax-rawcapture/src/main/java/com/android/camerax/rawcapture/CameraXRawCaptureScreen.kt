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
package com.android.camerax.rawcapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.display.rememberDisplayRotation
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coretheme.bodyFontFamily
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.feedback.ObserveSaveEvents
import com.android.camera.coreui.overlay.RuleOfThirdsGrid
import com.android.camera.coreui.overlay.SettingsDropdown
import com.android.camera.coreui.overlay.SettingsHeader
import com.android.camera.coreui.overlay.SettingsOverlay
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun CameraXRawCaptureScreen(viewModel: CameraXRawCaptureViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    ObserveSaveEvents(viewModel.events)

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            CameraXRawCaptureUiState.Initial -> {
                LoadingView()
            }

            CameraXRawCaptureUiState.Unsupported -> {
                UnsupportedView(message = stringResource(R.string.rawcapture_unsupported))
            }

            is CameraXRawCaptureUiState.Error -> {
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)
            }

            is CameraXRawCaptureUiState.Previewing -> {
                PreviewingContent(
                    state = state,
                    viewModel = viewModel,
                    onCaptured = viewModel::captured,
                    onUnsupported = viewModel::setUnsupported,
                    onBack = onBack,
                )
            }

            is CameraXRawCaptureUiState.Captured -> {
                CapturedReview(
                    jpegUri = state.jpegUri,
                    onRetake = viewModel::retake,
                    onDone = onBack,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(
    state: CameraXRawCaptureUiState.Previewing,
    viewModel: CameraXRawCaptureViewModel,
    onCaptured: (dngUri: Uri, jpegUri: Uri) -> Unit,
    onUnsupported: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller =
        rememberCameraXRawCaptureController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onCaptured = onCaptured,
            onCapabilitiesReady = viewModel::onCapabilitiesEvaluated,
            onUnsupported = onUnsupported,
        )
    val displayRotation = rememberDisplayRotation()

    LaunchedEffect(displayRotation, controller) {
        controller.updateTargetRotation(displayRotation)
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_RESUME -> {
                        controller.openCamera(state.selectedMode)
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

    RuleOfThirdsGrid()

    ViewfinderTopBar(
        title = stringResource(R.string.rawcapture_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            ScrimIconButton(
                onClick = { viewModel.setSettingsVisible(true) },
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.rawcapture_settings_button),
            )
        },
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        center = { ShutterButton(onClick = controller::takePicture) },
    )

    val pixelBinSummary =
        state.pixelBinResolutionLabel.ifEmpty {
            context.getString(R.string.rawcapture_mode_default_fallback)
        }
    val fullSensorSummary =
        state.fullSensorResolutionLabel.ifEmpty {
            context.getString(R.string.rawcapture_mode_max_fallback)
        }

    val options =
        if (state.isFullSensorSupported) {
            listOf(RawSensorMode.PIXEL_BIN, RawSensorMode.FULL_SENSOR)
        } else {
            listOf(RawSensorMode.PIXEL_BIN)
        }

    SettingsOverlay(
        visible = state.showSettings,
        onDismiss = { viewModel.setSettingsVisible(false) },
    ) {
        SettingsHeader(text = stringResource(R.string.rawcapture_settings_title))
        SettingsDropdown(
            label = stringResource(R.string.rawcapture_mode_label),
            options = options,
            selected = state.selectedMode,
            onSelected = { mode ->
                viewModel.selectMode(mode)
                controller.setSensorMode(mode)
            },
            optionLabel = { mode ->
                when (mode) {
                    RawSensorMode.PIXEL_BIN -> {
                        context.getString(R.string.rawcapture_mode_pixel_bin, pixelBinSummary)
                    }

                    RawSensorMode.FULL_SENSOR -> {
                        context.getString(R.string.rawcapture_mode_full_sensor, fullSensorSummary)
                    }
                }
            },
        )
    }
}

/**
 * Reviews the capture with interactive pinch-to-zoom and panning.
 */
@Composable
private fun BoxScope.CapturedReview(
    jpegUri: Uri,
    onRetake: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, jpegUri) {
        value = withContext(Dispatchers.IO) { decodeJpeg(context, jpegUri) }
    }

    val current = bitmap
    if (current == null) {
        LoadingView()
    } else {
        ZoomableCapturedImagePreview(
            bitmap = current,
            onRetake = onRetake,
            onDone = onDone,
        )
    }
}

@Composable
private fun ZoomableCapturedImagePreview(
    bitmap: Bitmap,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val mp = (bitmap.width.toLong() * bitmap.height) / 1_000_000.0
    val mpLabel =
        if (mp >= 10.0 && mp % 1.0 < 0.05) {
            String.format(Locale.US, "%.0f MP", mp)
        } else {
            String.format(Locale.US, "%.1f MP", mp)
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 3.5f
                                    val maxOffsetX = (size.width * (3.5f - 1f)) / 2f
                                    val maxOffsetY = (size.height * (3.5f - 1f)) / 2f
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    offset =
                                        Offset(
                                            x =
                                                ((centerX - tapOffset.x) * (3.5f - 1f)).coerceIn(
                                                    -maxOffsetX,
                                                    maxOffsetX,
                                                ),
                                            y =
                                                ((centerY - tapOffset.y) * (3.5f - 1f)).coerceIn(
                                                    -maxOffsetY,
                                                    maxOffsetY,
                                                ),
                                        )
                                }
                            },
                        )
                    }.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            scale = newScale
                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                            offset =
                                if (newScale > 1f) {
                                    Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                                    )
                                } else {
                                    Offset.Zero
                                }
                        }
                    },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.rawcapture_captured_photo),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                contentScale = ContentScale.Fit,
            )
        }

        ScrimIconButton(
            onClick = onDone,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.rawcapture_back),
            size = 40.dp,
            iconSize = 20.dp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
        )

        if (scale > 1.05f) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fx", scale),
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 20.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = "${bitmap.width} × ${bitmap.height} • $mpLabel",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = Color.White,
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.FilledTonalButton(
                onClick = onRetake,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.rawcapture_retake),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            androidx.compose.material3.Button(
                onClick = onDone,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.rawcapture_done),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun decodeJpeg(
    context: Context,
    uri: Uri,
): Bitmap? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder
                .decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        null
    }
