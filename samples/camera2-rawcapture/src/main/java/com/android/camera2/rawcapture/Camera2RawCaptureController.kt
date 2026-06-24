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
package com.android.camera2.rawcapture

import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.ExifInterface
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.android.camera.core.media.MediaStoreSaver
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "Camera2RawCapture"
private const val PREVIEW_WIDTH = 1920
private const val PREVIEW_HEIGHT = 1080

@Composable
fun rememberCamera2RawCaptureController(
    context: Context,
    isFrontCamera: Boolean,
    onDngSaved: (uri: Uri, rotationDegrees: Int) -> Unit,
    onUnsupported: () -> Unit,
): Camera2RawCaptureController =
    remember(context, isFrontCamera, onDngSaved, onUnsupported) {
        Camera2RawCaptureController(context, isFrontCamera, onDngSaved, onUnsupported)
    }

/**
 * Captures a single `RAW_SENSOR` frame and writes it as a DNG via [DngCreator]. The shared
 * open/close/transform plumbing lives in [com.android.camera.core.camera2.BaseCamera2Controller];
 * this class gates on the camera's RAW capability, adds a `RAW_SENSOR` [ImageReader], and pairs each
 * captured [Image] with its [TotalCaptureResult] (DngCreator needs both) before saving. All work
 * happens on the controller's background handler.
 */
@Stable
class Camera2RawCaptureController(
    context: Context,
    isFrontCamera: Boolean,
    private val onDngSaved: (uri: Uri, rotationDegrees: Int) -> Unit,
    private val onUnsupported: () -> Unit,
) : com.android.camera.core.camera2.BaseCamera2Controller(context, isFrontCamera) {
    private var rawReader: ImageReader? = null
    private var sensorOrientation: Int = 90

    // DngCreator needs the RAW Image and the TotalCaptureResult that produced it; they arrive on two
    // callbacks (both on the background handler), so hold each until its partner is ready.
    private var pendingImage: Image? = null
    private var pendingResult: TotalCaptureResult? = null

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val capabilities =
            characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        val rawSupported =
            capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW,
            )
        if (!rawSupported) {
            onUnsupported()
            return
        }

        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val rawSize =
            map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
        if (rawSize == null) {
            onUnsupported()
            return
        }

        rawReader =
            ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                setOnImageAvailableListener({ reader ->
                    pendingImage = reader.acquireNextImage()
                    tryWriteDng()
                }, backgroundHandler)
            }
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        val targets = mutableListOf(surface)
        rawReader?.surface?.let { targets.add(it) }

        previewRequestBuilder =
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }

        createCaptureSession(camera, targets) { startRepeatingRequest() }
    }

    private fun startRepeatingRequest() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return
            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start repeating request", e)
        }
    }

    /** Issues a still-capture request to the RAW reader and resumes preview afterwards. */
    fun captureRaw() {
        val device = cameraDevice ?: return
        val reader = rawReader ?: return
        val session = captureSession ?: return

        try {
            val captureBuilder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        pendingResult = result
                        tryWriteDng()
                    }
                },
                backgroundHandler,
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to capture RAW", e)
        }
    }

    /** Runs on the background handler; writes the DNG once both the image and its result exist. */
    private fun tryWriteDng() {
        val image = pendingImage ?: return
        val result = pendingResult ?: return
        val characteristics = currentCharacteristics ?: return
        pendingImage = null
        pendingResult = null

        try {
            val fileName = "RAW_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US).format(Date())}.dng"
            val uri = saveDng(characteristics, result, image, fileName)
            if (uri != null) onDngSaved(uri, sensorOrientation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write DNG", e)
        } finally {
            image.close()
        }
    }

    private fun saveDng(
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        image: Image,
        fileName: String,
    ): Uri? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "image/x-adobe-dng")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CameraSamples")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    DngCreator(characteristics, result).use { dng ->
                        dng.setOrientation(exifOrientation(sensorOrientation))
                        dng.writeImage(out, image)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                Log.e(TAG, "DNG write (MediaStore) failed", e)
                resolver.delete(uri, null, null)
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val dir =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "CameraSamples",
                )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            try {
                FileOutputStream(file).use { out ->
                    DngCreator(characteristics, result).use { dng ->
                        dng.setOrientation(exifOrientation(sensorOrientation))
                        dng.writeImage(out, image)
                    }
                }
                MediaStoreSaver.scanFile(context, file, "image/x-adobe-dng")
                Uri.fromFile(file)
            } catch (e: Exception) {
                Log.e(TAG, "DNG write (file) failed", e)
                null
            }
        }
    }

    private fun exifOrientation(degrees: Int): Int =
        when (degrees) {
            90 -> ExifInterface.ORIENTATION_ROTATE_90
            180 -> ExifInterface.ORIENTATION_ROTATE_180
            270 -> ExifInterface.ORIENTATION_ROTATE_270
            else -> ExifInterface.ORIENTATION_NORMAL
        }

    override fun onCameraClosed() {
        pendingImage?.close()
        pendingImage = null
        pendingResult = null
        rawReader?.close()
        rawReader = null
    }
}
