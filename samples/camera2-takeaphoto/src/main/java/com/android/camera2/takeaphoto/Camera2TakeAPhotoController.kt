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
package com.android.camera2.takeaphoto

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.util.Log
import androidx.camera.viewfinder.core.ScaleType
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.android.camera.core.camera2.BaseCamera2Controller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "Camera2TakeAPhotoController"

@Composable
fun rememberCamera2TakeAPhotoController(
    context: Context,
    isFrontCamera: Boolean,
    onPhotoCaptured: (Image, Int) -> Unit,
): Camera2TakeAPhotoController {
    val coroutineScope = rememberCoroutineScope()
    return remember(context, isFrontCamera, onPhotoCaptured) {
        Camera2TakeAPhotoController(context, isFrontCamera, onPhotoCaptured, coroutineScope)
    }
}

/**
 * Camera2 still-capture controller. The shared open/close/focus/transform plumbing lives in
 * [BaseCamera2Controller]; this class only adds the JPEG [ImageReader] and the still-capture flow.
 */
@Stable
class Camera2TakeAPhotoController(
    context: Context,
    isFrontCamera: Boolean,
    private val onPhotoCaptured: (Image, Int) -> Unit,
    private val coroutineScope: CoroutineScope,
) : BaseCamera2Controller(context, isFrontCamera) {
    companion object {
        const val PREVIEW_WIDTH = 1920
        const val PREVIEW_HEIGHT = 1080
    }

    private var imageReader: ImageReader? = null
    private var previewRequest: CaptureRequest? = null

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        imageReader =
            ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.JPEG, 1).apply {
                setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.let { image ->
                        val sensorOrientation =
                            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                        onPhotoCaptured(image, sensorOrientation)
                    }
                }, backgroundHandler)
            }
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        viewfinder: ViewfinderView,
    ) {
        coroutineScope.launch {
            try {
                val request = ViewfinderSurfaceRequest(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                updateTransformationInfo(currentDisplayRotation)
                viewfinder.scaleType = ScaleType.FILL_CENTER

                surfaceSession?.close()
                val session = viewfinder.requestSurfaceSessionAsync(request).await()
                surfaceSession = session
                val surface = session.surface
                val activeImageReader = imageReader ?: return@launch

                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }

                createCaptureSession(camera, listOf(surface, activeImageReader.surface)) {
                    startRepeatingRequest()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
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

            try {
                session.stopRepeating()
            } catch (e: CameraAccessException) {
                Log.w(TAG, "Exception while stopping repeating", e)
            }
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        resetFocusAndResumePreview()
                    }
                },
                null,
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

    override fun onCameraClosed() {
        imageReader?.close()
        imageReader = null
    }
}
