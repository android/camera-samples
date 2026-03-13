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
package com.android.camera2.takeaphoto.ui

import android.Manifest
import android.content.Context
import android.media.Image
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun Camera2TakeAPhotoScreen(
    viewModel: Camera2TakeAPhotoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasCameraPermission = it
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        viewModel.initialize()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is Camera2TakeAPhotoUiState.Initial -> LoadingView()
                is Camera2TakeAPhotoUiState.Error -> {
                    ErrorView(
                        errorMessage = state.errorMessage,
                        onRetry = viewModel::resetError
                    )
                }

                else -> {
                    if (hasCameraPermission) {
                        val isFrontCamera = when (state) {
                            is Camera2TakeAPhotoUiState.Capturing -> state.isFrontCamera
                            is Camera2TakeAPhotoUiState.PhotoCaptured -> state.isFrontCamera
                            else -> false
                        }

                        CapturingView(
                            isFrontCamera = isFrontCamera,
                            onPhotoCaptured = viewModel::processImage,
                            onSwapCamera = viewModel::swapCamera,
                            onBack = { backDispatcher?.onBackPressed() }
                        )

                        if (state is Camera2TakeAPhotoUiState.PhotoCaptured) {
                            CapturedPhotoView(
                                bitmap = state.photoBitmap,
                                onDismiss = viewModel::resetToCamera
                            )
                        }
                    } else {
                        PermissionDeniedView()
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PermissionDeniedView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Camera permission is required", color = Color.White)
    }
}

@Composable
private fun ErrorView(errorMessage: String?, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = errorMessage ?: "Unknown Error",
            color = Color.Red,
            modifier = Modifier.align(Alignment.Center)
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun CapturedPhotoView(bitmap: android.graphics.Bitmap, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured Photo",
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun CapturingView(
    isFrontCamera: Boolean,
    onPhotoCaptured: (Image, Int) -> Unit,
    onSwapCamera: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraController = rememberCamera2TakeAPhotoController(
        context = context,
        isFrontCamera = isFrontCamera,
        onPhotoCaptured = onPhotoCaptured
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewContent(cameraController)
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            CameraControls(
                onSwapCamera = onSwapCamera,
                onCapture = { cameraController.takePicture() }
            )
        }
    }
}

@Composable
private fun CameraPreviewContent(cameraController: Camera2TakeAPhotoController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, cameraController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) cameraController.openCamera()
            if (event == Lifecycle.Event.ON_PAUSE) cameraController.closeCamera()
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraController.closeCamera()
        }
    }

    val displayRotation = remember(configuration) {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        display?.rotation ?: Surface.ROTATION_0
    }

    LaunchedEffect(displayRotation) {
        cameraController.updateTransformationInfo(displayRotation)
    }

    AndroidView(
        factory = { ctx ->
            ViewfinderView(ctx)
        },
        update = { view ->
            cameraController.viewfinder = view
            // view.post(cameraController::openCamera)
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(cameraController.isFrontCamera) {
                detectTapGestures(
                    onTap = { offset ->
                        cameraController.focus(offset, size.width.toFloat(), size.height.toFloat())
                    }
                )
            }
    )
}

@Composable
private fun CameraControls(onSwapCamera: () -> Unit, onCapture: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 32.dp, end = 48.dp)
    ) {
        IconButton(
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White, shape = CircleShape)
            )
        }
        IconButton(
            onClick = onSwapCamera,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Swap Camera",
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
