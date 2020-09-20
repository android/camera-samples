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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.*
import java.nio.ByteBuffer
import kotlin.experimental.inv

/**
 * Helper class used to efficiently convert a [Media.Image] object from
 * [ImageFormat.YUV_420_888] format to an RGB [Bitmap] object.
 *
 * The [yuvToRgb] method is able to achieve the same FPS as the CameraX image
 * analysis use case on a Pixel 3 XL device at the default analyzer resolution,
 * which is 30 FPS with 640x480.
 *
 * NOTE: This has been tested in a limited number of devices and is not
 * considered production-ready code. It was created for illustration purposes,
 * since this is not an efficient camera pipeline due to the multiple copies
 * required to convert each frame.
 */
class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var pixelCount: Int = -1
    private lateinit var yuvBuffer: ByteArray
    private lateinit var inputAllocation: Allocation
    private lateinit var outputAllocation: Allocation

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {

        // Ensure that the intermediate output byte buffer is allocated
        if (!::yuvBuffer.isInitialized) {
            pixelCount = image.cropRect.width() * image.cropRect.height()
            // Bits per pixel is an average for the whole image, so it's useful to compute the size
            // of the full buffer but should not be used to determine pixel offsets
            val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            yuvBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
        }

        // Get the YUV data in byte array form using NV21 format
        image.toByteArray(yuvBuffer)

        // Ensure that the RenderScript inputs and outputs are allocated
        if (!::inputAllocation.isInitialized) {
            // Explicitly create an element with type NV21, since that's the pixel format we use
            val elemType = Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21).create()
            inputAllocation = Allocation.createSized(rs, elemType.element, yuvBuffer.size)
        }
        if (!::outputAllocation.isInitialized) {
            outputAllocation = Allocation.createFromBitmap(rs, output)
        }

        // Convert NV21 format YUV to RGB
        inputAllocation.copyFrom(yuvBuffer)
        scriptYuvToRgb.setInput(inputAllocation)
        scriptYuvToRgb.forEach(outputAllocation)
        outputAllocation.copyTo(output)
    }

    private fun Allocation.copyFromPlane(plane: Image.Plane) {
        if (plane.buffer.hasArray())
            this.copyFrom(plane.buffer.array())
        else {
            this.copyFromBuffer(plane.buffer)
        }
    }

    private fun Allocation.copyFromBuffer(buffer: ByteBuffer) {
        buffer.rewind()
        val array = ByteArray(buffer.limit())
        buffer.get(array)
        this.copyFrom(array)
    }

    // see https://stackoverflow.com/a/52740776/192373
// for 1920x1080 interleaved time is reduced from 13 ms to 2 ms
// for less optimal resolution 1440x1080 to 5 ms
    private fun Image.toByteArray(outputBuffer: ByteArray) {

        assert(format == ImageFormat.YUV_420_888)
        assert(planes[1].pixelStride == planes[2].pixelStride)
        assert(planes[1].rowStride == planes[2].rowStride)

        planes[0].extractLuminance(cropRect, outputBuffer)

        if (planes[2].pixelStride == 2 && planes[2].buffer.isInterleavedWith(planes[1].buffer))
            planes[2].extractChromaInterleaved(planes[1], cropRect, outputBuffer)
        else if (planes[2].pixelStride == 2 && planes[1].buffer.isInterleavedWith(planes[2].buffer))
            planes[2].extractChromaInterleaved(planes[1], cropRect, outputBuffer, uPlaneOffset=-1)
        else {
            planes[1].extractChroma(1, cropRect, outputBuffer)
            planes[2].extractChroma(0, cropRect, outputBuffer)
        }
    }

    // maybe this and other buffers overlap?
    private fun ByteBuffer.isInterleavedWith(other: ByteBuffer): Boolean {
        if (get(1) == other[0]) {
            val savePixel = other[0]
            val changed = savePixel.inv()
            try {
                other.put(0, changed) // does changing vBuffer effect uBuffer?
                if (get(1) == changed) {
                    return true
                }
            } catch (th: Throwable) {
                // silently catch everything
            } finally {
                other.put(0, savePixel) // restore
            }
        }
        return false
    }

    private fun Image.Plane.extractLuminance(imageCrop: Rect, outputBuffer: ByteArray) {
        var outputOffset = 0

        assert(pixelStride == 1)

        val planeWidth = imageCrop.width()
        val planeHeight = imageCrop.height()

        // Size of each row in bytes
        val rowLength = planeWidth

        if (rowStride == rowLength) {
            assert(imageCrop.left == 0)
            assert(imageCrop.top == 0)
            buffer.position(0)
            buffer.get(outputBuffer, 0, planeWidth * planeHeight)
        } else {
            for (row in 0 until planeHeight) {
                // Move buffer position to the beginning of this row
                buffer.position((row + imageCrop.top) * rowStride + imageCrop.left)

                // When there is a single stride value for pixel and output, we can just copy
                // the entire row in a single step
                buffer.get(outputBuffer, outputOffset, rowLength)
                outputOffset += rowLength
            }
        }
    }

    private fun Image.Plane.extractChromaInterleaved(
            uPlane: Image.Plane,
            imageCrop: Rect,
            outputArray: ByteArray,
            uPlaneOffset: Int = 1
    ) {
        assert(pixelStride == 2)
        assert(pixelStride == uPlane.pixelStride)
        assert(rowStride == uPlane.rowStride)

        val planeCrop = imageCrop.halve()
        val planeWidth = planeCrop.width()
        val planeHeight = planeCrop.height()

        // Size of each row in bytes
        val rowLength = planeWidth * pixelStride

        var outputOffset: Int = imageCrop.width() * imageCrop.height()

        if (uPlaneOffset == -1) {
            uPlane.buffer.get(outputArray, outputOffset, 1)
            outputOffset += 1
        }
        if (rowStride == rowLength) {
            buffer.position(0)

            val remaining = buffer.remaining()
            buffer.get(outputArray, outputOffset, remaining)
            outputOffset += remaining
        } else {
            var pos = planeCrop.top * rowStride + planeCrop.left * pixelStride
            for (row in 0 until planeHeight - 1) {
                // Move buffer position to the beginning of this row
                buffer.position(pos)
                pos += rowStride

                buffer.get(outputArray, outputOffset, rowLength)
                outputOffset += rowLength
            }

            val lastRowLength = Math.min(buffer.remaining(), outputArray.size - outputOffset)
            if (uPlaneOffset == -1) {
                assert(lastRowLength == rowLength)
            }
            buffer.get(outputArray, outputOffset, lastRowLength)
            outputOffset += lastRowLength
        }

        if (uPlaneOffset == -1) {
            assert(outputOffset == outputArray.size)
        }
        if (outputOffset < outputArray.size) {
            // add the last byte from the second plane
            assert(outputOffset == outputArray.size - 1)
            outputArray[outputOffset] = uPlane.buffer.get((planeHeight - 1 + planeCrop.top) * rowStride + planeCrop.left * 2 + rowLength - 2)
        }
    }

    private fun Image.Plane.extractChroma(
            firstOffset: Int,
            imageCrop: Rect,
            outputArray: ByteArray
    ) {
        assert(pixelStride == 1)

        var outputOffset: Int = imageCrop.width() * imageCrop.height() + firstOffset
        val planeCrop = imageCrop.halve()
        val planeWidth = planeCrop.width()
        val planeHeight = planeCrop.height()

        // Intermediate buffer used to store the bytes of each row
        val rowArray = ByteArray(planeWidth)

        for (row in 0 until planeHeight) {
            buffer.position((row + planeCrop.top) * rowStride + planeCrop.left)
            buffer.get(rowArray)
            for (col in 0 until planeWidth) {
                outputArray[outputOffset] = rowArray[col]
                outputOffset += 2
            }
        }
    }

    private fun Rect.halve(): Rect {
        return Rect(left/2,top/2, right/2, bottom/2)
    }
}