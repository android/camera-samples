/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.camera2.takeavideo

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.camera2.takeavideo.Camera2TakeAVideoController
import com.android.camera2.takeavideo.rememberCamera2TakeAVideoController

@Composable
fun Camera2TakeAVideoScreen(
    viewModel: Camera2TakeAVideoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasPermissions by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        hasPermissions = cameraGranted && audioGranted
    }

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        )
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
                is Camera2TakeAVideoUiState.Initial -> LoadingView()
                is Camera2TakeAVideoUiState.Setup -> {
                    if (hasPermissions) {
                        Camera2TakeAVideoSetup(
                            onConfigurationComplete = { config ->
                                viewModel.submitConfig(config)
                            }
                        )
                    } else {
                        PermissionDeniedView()
                    }
                }
                is Camera2TakeAVideoUiState.Error -> {
                    ErrorView(
                        errorMessage = state.errorMessage,
                        onRetry = viewModel::resetError
                    )
                }
                else -> {
                    if (hasPermissions) {
                        val isFrontCamera = when (state) {
                            is Camera2TakeAVideoUiState.Previewing -> state.isFrontCamera
                            is Camera2TakeAVideoUiState.Recording -> state.isFrontCamera
                            is Camera2TakeAVideoUiState.VideoCaptured -> state.isFrontCamera
                            else -> false
                        }

                        val config = when (state) {
                            is Camera2TakeAVideoUiState.Previewing -> state.config
                            is Camera2TakeAVideoUiState.Recording -> state.config
                            is Camera2TakeAVideoUiState.VideoCaptured -> state.config
                            else -> return@Box
                        }

                        val isRecording = state is Camera2TakeAVideoUiState.Recording

                        CapturingView(
                            isFrontCamera = isFrontCamera,
                            config = config,
                            isRecording = isRecording,
                            onStartRecording = viewModel::startRecording,
                            onVideoCaptured = { file -> viewModel.videoCaptured(file.toUri()) },
                            onSwapCamera = viewModel::swapCamera,
                            onBack = {
                                viewModel.initialize() // Reset to setup step
                            }
                        )

                        if (state is Camera2TakeAVideoUiState.VideoCaptured) {
                            CapturedVideoView(
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
        Text("Camera and Audio permissions are required", color = Color.White)
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
private fun CapturedVideoView(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = "Video Saved!",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
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
    config: CameraVideoConfig,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onVideoCaptured: (java.io.File) -> Unit,
    onSwapCamera: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cameraController = rememberCamera2TakeAVideoController(
        context = context,
        isFrontCamera = isFrontCamera,
        config = config,
        onVideoCaptured = onVideoCaptured
    )

    DisposableEffect(cameraController) {
        onDispose {
            cameraController.release()
        }
    }

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
                isRecording = isRecording,
                onSwapCamera = onSwapCamera,
                onToggleRecord = {
                    if (isRecording) {
                        cameraController.stopRecording()
                    } else {
                        onStartRecording()
                        cameraController.startRecording()
                    }
                }
            )
        }
    }
}

@Composable
private fun CameraPreviewContent(cameraController: Camera2TakeAVideoController) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
            ViewfinderView(ctx).apply {
                cameraController.viewfinder = this
            }
        },
        update = { view ->
            cameraController.viewfinder = view
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

    DisposableEffect(lifecycleOwner, cameraController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_CREATE || event == Lifecycle.Event.ON_RESUME) {
                cameraController.viewfinder?.post {
                    cameraController.openCamera()
                }
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                cameraController.closeCamera()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraController.closeCamera()
        }
    }
}

@Composable
private fun CameraControls(isRecording: Boolean, onSwapCamera: () -> Unit, onToggleRecord: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 32.dp, end = 48.dp)
    ) {
        IconButton(
            onClick = onToggleRecord,
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isRecording) Color.Red else Color.White, shape = CircleShape)
            )
        }
        
        if (!isRecording) {
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
}
