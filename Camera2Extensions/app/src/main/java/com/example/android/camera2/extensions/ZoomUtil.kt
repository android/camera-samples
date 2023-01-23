/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.extensions

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics

object ZoomUtil {

    fun minZoom() = 1.0f

    fun maxZoom(characteristics: CameraCharacteristics): Float {
        val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: return minZoom()

        if (maxZoom < minZoom()) return minZoom()

        return maxZoom
    }

    fun getZoomCropRect(zoomRatio: Float, characteristics: CameraCharacteristics): Rect {
        val sensorRect = sensorRect(characteristics)
        return cropRectByRatio(zoomRatio, sensorRect)
    }

    private fun cropRectByRatio(zoomRatio: Float, sensorRect: Rect): Rect {
        val cropWidth = sensorRect.width() / zoomRatio
        val cropHeight = sensorRect.height() / zoomRatio

        val left = (sensorRect.width() - cropWidth) / 2.0f
        val top = (sensorRect.height() - cropHeight) / 2.0f

        return Rect(
            left.toInt(),
            top.toInt(),
            (left + cropWidth).toInt(),
            (top + cropHeight).toInt()
        )
    }

    private fun sensorRect(characteristics: CameraCharacteristics): Rect =
        characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
}