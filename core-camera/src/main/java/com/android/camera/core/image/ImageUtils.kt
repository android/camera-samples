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
package com.android.camera.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import androidx.camera.core.ImageProxy

/*
 * Shared JPEG decode + orient helpers so every photo sample stops re-implementing the same
 * "read plane 0, decode, rotate, mirror" logic.
 *
 * Both ImageProxy.toBitmap and Image.toBitmap consume and close the source image, so the caller
 * must not use it afterwards.
 */

/**
 * Decodes a CameraX JPEG [ImageProxy] into a correctly oriented [Bitmap].
 *
 * @param mirror flips horizontally (used for front-facing capture).
 */
fun ImageProxy.toBitmap(mirror: Boolean = false): Bitmap {
    try {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return decodeOriented(bytes, imageInfo.rotationDegrees, mirror)
    } finally {
        close()
    }
}

/**
 * Decodes a Camera2 JPEG [Image] into a correctly oriented [Bitmap].
 *
 * @param sensorOrientation the camera sensor orientation in degrees.
 * @param mirror flips horizontally (used for front-facing capture).
 */
fun Image.toBitmap(
    sensorOrientation: Int,
    mirror: Boolean = false,
): Bitmap {
    try {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return decodeOriented(bytes, sensorOrientation, mirror)
    } finally {
        close()
    }
}

private fun decodeOriented(
    bytes: ByteArray,
    rotationDegrees: Int,
    mirror: Boolean,
): Bitmap {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (rotationDegrees == 0 && !mirror) return original
    val matrix =
        Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
    return Bitmap
        .createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        .also { rotated -> if (rotated !== original) original.recycle() }
}
