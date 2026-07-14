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
package com.android.camera2.manualcontrols

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.android.camera.core.camera2.BaseCamera2Controller

private const val TAG = "Camera2ManualCtrl"

// A reasonable preview resolution; the viewfinder letterboxes/crops to fit.
private const val PREVIEW_WIDTH = 1280
private const val PREVIEW_HEIGHT = 720

@Composable
fun rememberCamera2ManualControlsController(
    context: Context,
    onUnsupported: () -> Unit,
    onCameraReady: (
        supported: Boolean,
        isoRange: Pair<Int, Int>,
        exposureRangeNs: Pair<Long, Long>,
        minFocusDistance: Float,
    ) -> Unit,
): Camera2ManualControlsController {
    val latestOnUnsupported by rememberUpdatedState(onUnsupported)
    val latestOnCameraReady by rememberUpdatedState(onCameraReady)
    return remember(context) {
        Camera2ManualControlsController(
            context,
            onUnsupported = { latestOnUnsupported() },
            onCameraReady = { supported, isoRange, exposureRangeNs, minFocusDistance ->
                latestOnCameraReady(supported, isoRange, exposureRangeNs, minFocusDistance)
            },
        )
    }
}

/**
 * Camera2 manual-sensor-controls controller. Shared open/close/focus/transform plumbing lives in
 * [BaseCamera2Controller]; this class drives a preview-only session whose repeating request applies
 * either auto exposure/focus or fully manual ISO ([CaptureRequest.SENSOR_SENSITIVITY]), shutter
 * speed ([CaptureRequest.SENSOR_EXPOSURE_TIME]), and focus distance
 * ([CaptureRequest.LENS_FOCUS_DISTANCE]).
 */
@Stable
class Camera2ManualControlsController(
    context: Context,
    private val onUnsupported: () -> Unit,
    private val onCameraReady: (
        supported: Boolean,
        isoRange: Pair<Int, Int>,
        exposureRangeNs: Pair<Long, Long>,
        minFocusDistance: Float,
    ) -> Unit,
) : BaseCamera2Controller(context, isFrontCamera = false) {
    override val previewSize: Size = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

    private var isSupported = false

    private var isoRange: Pair<Int, Int> = 100 to 100
    private var exposureRangeNs: Pair<Long, Long> = 0L to 0L
    private var minFocusDistance: Float = 0f

    private var manualEnabled = false
    private var iso: Int = 100
    private var exposureNs: Long = 0L
    private var focusDistance: Float = 0f

    override fun onCameraPrepared(characteristics: CameraCharacteristics) {
        val capabilities =
            characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
        isSupported =
            capabilities.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
            )

        val sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val maxDiopters =
            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

        if (sensitivityRange != null) {
            isoRange = sensitivityRange.lower to sensitivityRange.upper
        }
        if (exposureRange != null) {
            exposureRangeNs = exposureRange.lower to exposureRange.upper
        }
        // maxDiopters == 0f indicates a fixed-focus lens.
        minFocusDistance = maxDiopters

        // Seed our own manual defaults so the first repeating request is valid.
        iso = ((isoRange.first + isoRange.second) / 2).coerceIn(isoRange.first, isoRange.second)
        val sixtieth = 1_000_000_000L / 60L
        exposureNs = sixtieth.coerceIn(exposureRangeNs.first, exposureRangeNs.second)
        focusDistance = minFocusDistance / 2f

        onCameraReady(isSupported, isoRange, exposureRangeNs, minFocusDistance)
    }

    override fun onCameraOpened(
        camera: CameraDevice,
        surface: Surface,
    ) {
        if (!isSupported) {
            onUnsupported()
            return
        }
        previewRequestBuilder =
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
        createCaptureSession(camera, listOf(surface)) {
            applyRepeating()
        }
    }

    /** Pushes the current auto/manual settings into the repeating preview request. */
    private fun applyRepeating() {
        try {
            val builder = previewRequestBuilder ?: return
            val session = captureSession ?: return

            if (manualEnabled) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            } else {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            }

            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to apply repeating request", e)
        }
    }

    fun setManualEnabled(enabled: Boolean) {
        manualEnabled = enabled
        applyRepeating()
    }

    fun setIso(value: Int) {
        iso = value.coerceIn(isoRange.first, isoRange.second)
        applyRepeating()
    }

    fun setExposure(value: Long) {
        exposureNs = value.coerceIn(exposureRangeNs.first, exposureRangeNs.second)
        applyRepeating()
    }

    fun setFocusDistance(value: Float) {
        focusDistance = value.coerceIn(0f, minFocusDistance)
        applyRepeating()
    }
}
