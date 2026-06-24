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
package com.android.camera2.zoomandtorch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.android.camera.core.camera2.BaseCamera2Controller

private const val TAG = "Camera2ZoomAndTorchCtrl"

@Composable
fun rememberCamera2ZoomAndTorchController(
    context: Context,
    isFrontCamera: Boolean,
    onCameraReady: (minZoom: Float, maxZoom: Float, hasFlash: Boolean) -> Unit,
): Camera2ZoomAndTorchController =
    remember(context, isFrontCamera, onCameraReady) {
        Camera2ZoomAndTorchController(context, isFrontCamera, onCameraReady)
    }

/**
 * Camera2 controller exposing optical/digital zoom via [CaptureRequest.CONTROL_ZOOM_RATIO]
 * (API 30+) and a torch toggle via [CaptureRequest.FLASH_MODE_TORCH] on the repeating preview
 * request. The shared open/close/focus/transform plumbing lives in [BaseCamera2Controller]; this
 * class builds a preview-only session and re-issues the repeating request whenever the zoom ratio
 * or torch state changes.
 */
@Stable
class Camera2ZoomAndTorchController(
    context: Context,
    isFrontCamera: Boolean,
    private val onCameraReady: (minZoom: Float, maxZoom: Float, hasFlash: Boolean) -> Unit,
) : BaseCamera2Controller(context, isFrontCamera) {
    companion object {
        const val PREVIEW_WIDTH = 1920
        const val PREVIEW_HEIGHT = 1080
    }

    private var currentZoom: Float = 1f
    private var torchOn: Boolean = false

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        val (minZoom, maxZoom) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val range =
                    characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                if (range != null) {
                    range.lower to range.upper
                } else {
                    1f to 1f
                }
            } else {
                1f to 1f
            }

        val hasFlash =
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

        currentZoom = currentZoom.coerceIn(minZoom, maxZoom)
        if (!hasFlash) torchOn = false

        onCameraReady(minZoom, maxZoom, hasFlash)
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        previewRequestBuilder =
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
        createCaptureSession(camera, listOf(surface)) {
            applyRepeating()
        }
    }

    /** Re-issues the repeating request with the current zoom ratio and torch state. */
    private fun applyRepeating() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return

            builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom)
            }

            builder.set(
                CaptureRequest.FLASH_MODE,
                if (torchOn) {
                    CaptureRequest.FLASH_MODE_TORCH
                } else {
                    CaptureRequest.FLASH_MODE_OFF
                },
            )

            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to apply repeating request", e)
        }
    }

    /** Sets the zoom ratio (API 30+) and re-issues the repeating request. */
    fun setZoom(ratio: Float) {
        currentZoom = ratio
        applyRepeating()
    }

    /** Toggles the torch (FLASH_MODE_TORCH) on the preview request. */
    fun setTorch(on: Boolean) {
        torchOn = on
        applyRepeating()
    }
}
