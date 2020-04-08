/*
 * Copyright 2020 The Android Open Source Project
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

package com.example.android.camera.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface

private const val TAG: String = "ExifUtils"

/** Transforms rotation and mirroring information into one of the [ExifInterface] constants */
fun computeExifOrientation(rotationDegrees: Int, mirrored: Boolean) = when {
    rotationDegrees == 0 && !mirrored -> ExifInterface.ORIENTATION_NORMAL
    rotationDegrees == 0 && mirrored -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
    rotationDegrees == 180 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_180
    rotationDegrees == 180 && mirrored -> ExifInterface.ORIENTATION_FLIP_VERTICAL
    rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
    rotationDegrees == 90 && !mirrored -> ExifInterface.ORIENTATION_ROTATE_90
    rotationDegrees == 90 && mirrored -> ExifInterface.ORIENTATION_TRANSPOSE
    rotationDegrees == 270 && mirrored -> ExifInterface.ORIENTATION_ROTATE_270
    rotationDegrees == 270 && !mirrored -> ExifInterface.ORIENTATION_TRANSVERSE
    else -> ExifInterface.ORIENTATION_UNDEFINED
}

/**
 * Helper function used to convert an EXIF orientation enum into a transformation matrix
 * that can be applied to a bitmap.
 *
 * @return matrix - Transformation required to properly display [Bitmap]
 */
fun decodeExifOrientation(exifOrientation: Int): Matrix {
    val matrix = Matrix()

    // Apply transformation corresponding to declared EXIF orientation
    when (exifOrientation) {
        ExifInterface.ORIENTATION_NORMAL -> Unit
        ExifInterface.ORIENTATION_UNDEFINED -> Unit
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postScale(-1F, 1F)
            matrix.postRotate(270F)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postScale(-1F, 1F)
            matrix.postRotate(90F)
        }

        // Error out if the EXIF orientation is invalid
        else -> Log.e(TAG, "Invalid orientation: $exifOrientation")
    }

    // Return the resulting matrix
    return matrix
}
