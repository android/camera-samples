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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.android.camera.core.camera2.BaseCamera2Controller
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

private const val TAG = "Camera2QrScanner"
private const val PREVIEW_WIDTH = 1920
private const val PREVIEW_HEIGHT = 1080

@Composable
fun rememberCamera2QrScannerController(
    context: Context,
    isFrontCamera: Boolean,
    onBarcodes: (barcodes: List<DetectedBarcode>, sourceWidth: Int, sourceHeight: Int) -> Unit,
): Camera2QrScannerController =
    remember(context, isFrontCamera, onBarcodes) {
        Camera2QrScannerController(context, isFrontCamera, onBarcodes)
    }

/**
 * Camera2 barcode/QR scanner. The shared open/close/transform plumbing lives in
 * [BaseCamera2Controller]; this class adds a `YUV_420_888` [ImageReader] as a second repeating
 * target and feeds each frame to ML Kit's on-device [BarcodeScanning] detector — the Camera2
 * counterpart to the CameraX `ImageAnalysis` QR sample. Only one frame is in flight at a time so the
 * reader never stalls.
 */
@Stable
class Camera2QrScannerController(
    context: Context,
    isFrontCamera: Boolean,
    private val onBarcodes: (
        barcodes: List<DetectedBarcode>,
        sourceWidth: Int,
        sourceHeight: Int,
    ) -> Unit,
) : BaseCamera2Controller(context, isFrontCamera) {
    private var imageReader: ImageReader? = null
    private var sensorOrientation: Int = 90

    @Volatile
    private var processing = false

    private val scanner = BarcodeScanning.getClient()

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        val analysisSize = chooseAnalysisSize(characteristics)
        imageReader =
            ImageReader
                .newInstance(analysisSize.width, analysisSize.height, ImageFormat.YUV_420_888, 2)
                .apply {
                    setOnImageAvailableListener({ reader -> analyze(reader) }, backgroundHandler)
                }
    }

    /** Picks the largest advertised YUV size at or below 1080p so analysis stays cheap. */
    private fun chooseAnalysisSize(characteristics: CameraCharacteristics): Size {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888).orEmpty().toList()
        return sizes
            .filter { it.width * it.height <= PREVIEW_WIDTH * PREVIEW_HEIGHT }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { it.width.toLong() * it.height }
            ?: Size(1280, 720)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(reader: ImageReader) {
        val image: Image = reader.acquireLatestImage() ?: return
        if (processing) {
            image.close()
            return
        }
        processing = true

        val width = image.width
        val height = image.height
        val rotated = sensorOrientation == 90 || sensorOrientation == 270
        val sourceWidth = if (rotated) height else width
        val sourceHeight = if (rotated) width else height

        val inputImage = InputImage.fromMediaImage(image, sensorOrientation)
        scanner
            .process(inputImage)
            .addOnSuccessListener { barcodes ->
                onBarcodes(
                    barcodes.map { DetectedBarcode(it.rawValue ?: "", it.boundingBox) },
                    sourceWidth,
                    sourceHeight,
                )
            }.addOnFailureListener { e -> Log.e(TAG, "Barcode scanning failed", e) }
            .addOnCompleteListener {
                image.close()
                processing = false
            }
    }

    override val previewSize: Size = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        val readerSurface = imageReader?.surface ?: return
        previewRequestBuilder =
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                addTarget(readerSurface)
            }
        createCaptureSession(camera, listOf(surface, readerSurface)) {
            try {
                val builder = previewRequestBuilder ?: return@createCaptureSession
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
                captureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to start preview", e)
            }
        }
    }

    override fun onCameraClosed() {
        processing = false
        imageReader?.close()
        imageReader = null
    }

    override fun release() {
        super.release()
        scanner.close()
    }
}
