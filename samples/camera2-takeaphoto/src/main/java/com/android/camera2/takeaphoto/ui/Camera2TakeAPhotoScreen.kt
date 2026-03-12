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
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.viewfinder.core.ScaleType
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.camera.viewfinder.core.camera2.Camera2TransformationInfo
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val TAG = "CameraPreview"

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
                is Camera2TakeAPhotoUiState.Capturing -> {
                    if (hasCameraPermission) {
                        CapturingView(
                            isFrontCamera = state.isFrontCamera,
                            onPhotoCaptured = viewModel::processImage,
                            onSwapCamera = viewModel::swapCamera
                        )
                    } else {
                        PermissionDeniedView()
                    }
                }

                is Camera2TakeAPhotoUiState.PhotoCaptured -> {
                    CapturedPhotoView(
                        bitmap = state.photoBitmap,
                        onDismiss = viewModel::resetToCamera
                    )
                }

                is Camera2TakeAPhotoUiState.Error -> {
                    ErrorView(
                        errorMessage = state.errorMessage,
                        onRetry = viewModel::resetError
                    )
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
    Box(modifier = Modifier.fillMaxSize()) {
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
                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
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
    onSwapCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraState = rememberCamera2State(
        context = context,
        isFrontCamera = isFrontCamera,
        onPhotoCaptured = onPhotoCaptured
    )

    DisposableEffect(lifecycleOwner, cameraState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraState.openCamera()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                cameraState.closeCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        cameraState.openCamera()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraState.closeCamera()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewContent(cameraState)
        CameraControls(
            onSwapCamera = onSwapCamera,
            onCapture = { cameraState.takePicture() }
        )
    }
}

@Composable
private fun CameraPreviewContent(cameraState: Camera2State) {
    AndroidView(
        factory = { ctx ->
            ViewfinderView(ctx).apply {
                cameraState.viewfinder = this
                post { cameraState.openCamera() }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(cameraState.isFrontCamera) {
                detectTapGestures(
                    onTap = { offset ->
                        cameraState.focus(offset, size.width.toFloat(), size.height.toFloat())
                    }
                )
            }
    )
}

@Composable
private fun CameraControls(onSwapCamera: () -> Unit, onCapture: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSwapCamera) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_rotate),
                    contentDescription = "Swap Camera",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            IconButton(onClick = onCapture) {
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
}

@Composable
fun rememberCamera2State(
    context: Context,
    isFrontCamera: Boolean,
    onPhotoCaptured: (Image, Int) -> Unit
): Camera2State {
    val coroutineScope = rememberCoroutineScope()
    return remember(context, isFrontCamera, onPhotoCaptured) {
        Camera2State(context, isFrontCamera, onPhotoCaptured, coroutineScope)
    }
}

@Stable
class Camera2State(
    private val context: Context,
    val isFrontCamera: Boolean,
    private val onPhotoCaptured: (Image, Int) -> Unit,
    private val coroutineScope: CoroutineScope
) {
    var viewfinder: ViewfinderView? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null
    private var cameraId: String = ""

    fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        val currentViewfinder = viewfinder ?: return
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                val targetFacing = if (isFrontCamera) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }

                if (facing == targetFacing) {
                    cameraId = id
                    setupImageReader(characteristics)

                    cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            cameraDevice = camera
                            startPreviewSession(camera, currentViewfinder, characteristics)
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
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun setupImageReader(characteristics: CameraCharacteristics) {
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let {
                    val sOrientation =
                        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                    onPhotoCaptured(it, sOrientation)
                }
            }, backgroundHandler)
        }
    }

    private fun startPreviewSession(
        camera: CameraDevice,
        currentViewfinder: ViewfinderView,
        characteristics: CameraCharacteristics
    ) {
        coroutineScope.launch {
            try {
                val request = ViewfinderSurfaceRequest(1920, 1080)
                currentViewfinder.transformationInfo =
                    Camera2TransformationInfo.createFromCharacteristics(characteristics)
                currentViewfinder.scaleType = ScaleType.FILL_CENTER

                val session = currentViewfinder.requestSurfaceSessionAsync(request).await()
                val surface = session.surface

                val activeImageReader = imageReader ?: return@launch

                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }

                camera.createCaptureSession(
                    listOf(surface, activeImageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            if (cameraDevice == null) return
                            captureSession = session
                            startRepeatingRequest()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Failed to configure capture session")
                        }
                    },
                    null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception starting preview", e)
            }
        }
    }

    private fun startRepeatingRequest() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return

            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            previewRequest = builder.build()
            session.setRepeatingRequest(previewRequest!!, null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start repeating request", e)
        }
    }

    fun takePicture() {
        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val session = captureSession ?: return

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(reader.surface)

            previewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)?.let {
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, it)
            }

            session.stopRepeating()
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        resetFocusAndResumePreview()
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to take picture", e)
        }
    }

    private fun resetFocusAndResumePreview() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            session.capture(builder.build(), null, backgroundHandler)

            previewRequest?.let {
                session.setRepeatingRequest(it, null, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to reset focus", e)
        }
    }

    fun focus(offset: Offset, viewWidth: Float, viewHeight: Float) {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorArraySize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

            val y0 = (offset.x / viewWidth) * sensorArraySize.height()
            val x0 = (offset.y / viewHeight) * sensorArraySize.width()
            val halfTouchWidth = 150
            val halfTouchHeight = 150

            val focusArea = Rect(
                (x0 - halfTouchWidth).toInt().coerceAtLeast(0),
                (y0 - halfTouchHeight).toInt().coerceAtLeast(0),
                (x0 + halfTouchWidth).toInt().coerceAtMost(sensorArraySize.width()),
                (y0 + halfTouchHeight).toInt().coerceAtMost(sensorArraySize.height())
            )

            session.stopRepeating()

            val rectangle = MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rectangle))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rectangle))
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)

            session.capture(builder.build(), null, backgroundHandler)

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Focus error", e)
        }
    }
}
