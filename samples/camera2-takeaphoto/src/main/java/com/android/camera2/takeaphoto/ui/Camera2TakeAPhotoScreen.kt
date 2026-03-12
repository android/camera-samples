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
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

@Composable
fun Camera2TakeAPhotoScreen(
    viewModel: Camera2TakeAPhotoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Launch permission request
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        hasCameraPermission = it
    }

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
                is Camera2TakeAPhotoUiState.Initial -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is Camera2TakeAPhotoUiState.Capturing -> {
                    if (hasCameraPermission) {
                        CameraPreview(
                            isFrontCamera = state.isFrontCamera,
                            onPhotoCaptured = { image, orientation ->
                                viewModel.processImage(image, orientation)
                            },
                        ) { captureAction, swapAction ->
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    viewModel.swapCamera()
                                    swapAction()
                                }) {
                                    Icon(
                                        painter = painterResource(id = android.R.drawable.ic_menu_rotate),
                                        contentDescription = "Swap Camera",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }

                                IconButton(onClick = captureAction) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(
                                                Color.White,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "Camera permission is required",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                is Camera2TakeAPhotoUiState.PhotoCaptured -> {
                    Image(
                        bitmap = state.photoBitmap.asImageBitmap(),
                        contentDescription = "Captured Photo",
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = { viewModel.resetToCamera() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                is Camera2TakeAPhotoUiState.Error -> {
                    Text(
                        text = state.errorMessage ?: "Unknown Error",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Button(
                        onClick = { viewModel.resetError() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(32.dp)
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CameraPreview(
    isFrontCamera: Boolean,
    onPhotoCaptured: (android.media.Image, Int) -> Unit,
    controls: @Composable BoxScope.(() -> Unit, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var viewfinder: ViewfinderView? by remember { mutableStateOf(null) }
    var cameraDevice: CameraDevice? by remember { mutableStateOf(null) }
    var captureSession: CameraCaptureSession? by remember { mutableStateOf(null) }
    var imageReader: ImageReader? by remember { mutableStateOf(null) }
    var previewRequestBuilder: CaptureRequest.Builder? by remember { mutableStateOf(null) }
    var previewRequest: CaptureRequest? by remember { mutableStateOf(null) }
    var cameraId by remember { mutableStateOf("") }

    val cameraManager =
        remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val backgroundThread = remember { HandlerThread("CameraBackground").apply { start() } }
    val backgroundHandler = remember { Handler(backgroundThread.looper) }

    fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    fun takePicture() {
        if (cameraDevice == null || imageReader == null) return
        try {
            val captureBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(imageReader!!.surface)
            previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)?.let {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, it)
            }

            captureSession?.stopRepeating()
            captureSession?.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        try {
                            previewRequestBuilder?.set(
                                CaptureRequest.CONTROL_AF_TRIGGER,
                                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                            )
                            previewRequestBuilder?.build()
                                ?.let { captureSession?.capture(it, null, backgroundHandler) }
                            previewRequest?.let {
                                captureSession?.setRepeatingRequest(
                                    it,
                                    null,
                                    backgroundHandler
                                )
                            }
                        } catch (e: CameraAccessException) {
                            Log.e("CameraPreview", e.toString())
                        }
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e("CameraPreview", e.toString())
        }
    }

    fun openCamera() {
        val currentViewfinder = viewfinder ?: return
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val targetFacing =
                    if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK

                if (facing == targetFacing) {
                    cameraId = id
                    imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1).apply {
                        setOnImageAvailableListener({ reader ->
                            val image = reader.acquireLatestImage()
                            image?.let {
                                val sOrientation =
                                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
                                        ?: 90
                                onPhotoCaptured(it, sOrientation)
                            }
                        }, backgroundHandler)
                    }
                    cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            coroutineScope.launch {
                                try {
                                    val request = ViewfinderSurfaceRequest(1920, 1080)
                                    val session =
                                        currentViewfinder.requestSurfaceSessionAsync(request)
                                            .await()
                                    val surface = session.surface

                                    previewRequestBuilder =
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                    previewRequestBuilder!!.addTarget(surface)

                                    camera.createCaptureSession(
                                        listOf(surface, imageReader!!.surface),
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                if (cameraDevice == null) return
                                                captureSession = session
                                                try {
                                                    previewRequestBuilder!!.set(
                                                        CaptureRequest.CONTROL_AF_MODE,
                                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                                    )
                                                    previewRequest = previewRequestBuilder!!.build()
                                                    captureSession!!.setRepeatingRequest(
                                                        previewRequest!!,
                                                        null,
                                                        backgroundHandler
                                                    )
                                                } catch (e: CameraAccessException) {
                                                    Log.e("CameraPreview", e.toString())
                                                }
                                            }

                                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                                        },
                                        null
                                    )
                                } catch (e: Exception) {
                                    Log.e("CameraPreview", e.toString())
                                }
                            }
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            cameraDevice = null
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            camera.close()
                            cameraDevice = null
                        }
                    }, backgroundHandler)
                    return
                }
            }
        } catch (e: CameraAccessException) {
            Log.e("CameraPreview", e.toString())
        }
    }

    DisposableEffect(lifecycleOwner, isFrontCamera) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                openCamera()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                closeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        openCamera()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            closeCamera()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                ViewfinderView(ctx).apply {
                    viewfinder = this
                    post { openCamera() }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isFrontCamera) {
                    detectTapGestures(
                        onTap = { offset ->
                            try {
                                val characteristics =
                                    cameraManager.getCameraCharacteristics(cameraId)
                                val sensorArraySize =
                                    characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                                        ?: return@detectTapGestures

                                val y0 = (offset.x / size.width) * sensorArraySize.height()
                                val x0 = (offset.y / size.height) * sensorArraySize.width()
                                val halfTouchWidth = 150
                                val halfTouchHeight = 150

                                val focusArea = Rect(
                                    (x0 - halfTouchWidth).toInt().coerceAtLeast(0),
                                    (y0 - halfTouchHeight).toInt().coerceAtLeast(0),
                                    (x0 + halfTouchWidth).toInt()
                                        .coerceAtMost(sensorArraySize.width()),
                                    (y0 + halfTouchHeight).toInt()
                                        .coerceAtMost(sensorArraySize.height())
                                )

                                captureSession?.stopRepeating()
                                previewRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AF_REGIONS,
                                    arrayOf(
                                        MeteringRectangle(
                                            focusArea,
                                            MeteringRectangle.METERING_WEIGHT_MAX
                                        )
                                    )
                                )
                                previewRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AE_REGIONS,
                                    arrayOf(
                                        MeteringRectangle(
                                            focusArea,
                                            MeteringRectangle.METERING_WEIGHT_MAX
                                        )
                                    )
                                )
                                previewRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_AUTO
                                )
                                previewRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AF_TRIGGER,
                                    CameraMetadata.CONTROL_AF_TRIGGER_START
                                )

                                previewRequestBuilder?.build()
                                    ?.let { captureSession?.capture(it, null, backgroundHandler) }

                                previewRequestBuilder?.set(
                                    CaptureRequest.CONTROL_AF_TRIGGER,
                                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE
                                )
                                previewRequestBuilder?.build()?.let {
                                    captureSession?.setRepeatingRequest(
                                        it,
                                        null,
                                        backgroundHandler
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Focus error: ${e.message}")
                            }
                        }
                    )
                }
        )

        controls(
            { takePicture() },
            { /* swap is handled via recomposition of isFrontCamera */ }
        )
    }
}
