package com.example.android.camera2.slowmo

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.view.OrientationEventListener
import android.view.Surface
import androidx.lifecycle.LiveData


/**
 * Calculates closest 90-degree orientation to compensate for the device
 * rotation relative to sensor orientation, i.e., allows user to see camera
 * frames with the expected orientation.
 */
class OrientationLiveData(
        context: Context,
        characteristics: CameraCharacteristics
): LiveData<Int>() {

    private val listener = object : OrientationEventListener(context.applicationContext) {
        override fun onOrientationChanged(orientation: Int) {
            val rotation = when {
                orientation <= 45 -> Surface.ROTATION_0
                orientation <= 135 -> Surface.ROTATION_90
                orientation <= 225 -> Surface.ROTATION_180
                orientation <= 315 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }
            val relative = computeRelativeRotation(characteristics, rotation)
            if (relative != value) postValue(relative)
        }
    }

    override fun onActive() {
        super.onActive()
        listener.enable()
    }

    override fun onInactive() {
        super.onInactive()
        listener.disable()
    }

    companion object {

        /**
         * Computes rotation required to transform from the camera sensor orientation to the
         * device's current orientation in degrees.
         *
         * @param characteristics the [CameraCharacteristics] to query for the sensor orientation.
         * @param surfaceRotation the current device orientation as a Surface constant
         * @return the relative rotation from the camera sensor to the current device orientation.
         */
        @JvmStatic
        private fun computeRelativeRotation(
                characteristics: CameraCharacteristics,
                surfaceRotation: Int
        ): Int {
            val sensorOrientationDegrees =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

            val deviceOrientationDegrees = when (surfaceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            // Reverse device orientation for front-facing cameras
            val sign = if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT) 1 else -1

            // Calculate desired JPEG orientation relative to camera orientation to make
            // the image upright relative to the device orientation
            return (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360
        }
    }
}