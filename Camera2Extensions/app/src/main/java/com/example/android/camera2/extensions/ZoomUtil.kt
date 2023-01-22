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