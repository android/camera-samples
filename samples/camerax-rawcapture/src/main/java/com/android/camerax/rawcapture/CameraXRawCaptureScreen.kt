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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.display.rememberDisplayRotation
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraControlsBar
import com.android.camera.coreui.controls.ShutterButton
import com.android.camera.coreui.feedback.ObserveSaveEvents
import com.android.camera.coreui.overlay.RuleOfThirdsGrid
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.preview.CapturedImagePreview
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.state.UnsupportedView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CameraXRawCaptureScreen(
    viewModel: CameraXRawCaptureViewModel =
        hiltViewModel(),
) {
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

            CameraXRawCaptureUiState.Previewing -> {
                PreviewingContent(
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

    RuleOfThirdsGrid()

    ViewfinderTopBar(
        title = stringResource(R.string.rawcapture_title),
        onClose = onBack,
        closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
    )

    CameraControlsBar(
        modifier = Modifier.align(Alignment.BottomCenter),
        center = { ShutterButton(onClick = controller::takePicture) },
    )
}

/**
 * Reviews the capture by decoding the companion JPEG to a [Bitmap] (the DNG is preserved on disk but
 * not all devices can decode it in-app). Retake deletes both saved files; Done leaves the sample.
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
        CapturedImagePreview(
            bitmap = current,
            onRetake = onRetake,
            onDone = onDone,
        )
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
