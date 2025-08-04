package com.example.android.camera.utils

import android.graphics.ImageFormat
import android.media.Image
import androidx.annotation.IntDef
import java.nio.ByteBuffer

/*
This file is converted from part of https://github.com/gordinmitya/yuv2buf.
Follow the link to find demo app, performance benchmarks and unit tests.

Intro to YUV image formats:
YUV_420_888 - is a generic format that can be represented as I420, YV12, NV21, and NV12.
420 means that for each 4 luminosity pixels we have 2 chroma pixels: U and V.

* I420 format represents an image as Y plane followed by U then followed by V plane
    without chroma channels interleaving.
    For example:
    Y Y Y Y
    Y Y Y Y
    U U V V

* NV21 format represents an image as Y plane followed by V and U interleaved. First V then U.
    For example:
    Y Y Y Y
    Y Y Y Y
    V U V U

* YV12 and NV12 are the same as previous formats but with swapped order of V and U. (U then V)

Visualization of these 4 formats:
https://user-images.githubusercontent.com/9286092/89119601-4f6f8100-d4b8-11ea-9a51-2765f7e513c2.jpg

It's guaranteed that image.getPlanes() always returns planes in order Y U V for YUV_420_888.
https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888

Because I420 and NV21 are more widely supported (RenderScript, OpenCV, MNN)
the conversion is done into these formats.

More about each format: https://www.fourcc.org/yuv.php
*/

@kotlin.annotation.Retention(AnnotationRetention.SOURCE)
@IntDef(ImageFormat.NV21, ImageFormat.YUV_420_888)
annotation class YuvType

class YuvByteBuffer(image: Image, dstBuffer: ByteBuffer? = null) {
    @YuvType
    val type: Int
    val buffer: ByteBuffer

    init {
        val wrappedImage = ImageWrapper(image)

        type = if (wrappedImage.u.pixelStride == 1) {
            ImageFormat.YUV_420_888
        } 
        }
        val size = image.width * image.height * 3 / 2
        buffer = if (
            dstBuffer == null || dstBuffer.capacity() < size ||
            dstBuffer.isReadOnly || !dstBuffer.isDirect
        ) {
            ByteBuffer.allocateDirect(size) }
        else {
            dstBuffer
        }
        buffer.rewind()

        removePadding(wrappedImage)
    }

    // Input buffers are always direct as described in
    // https://developer.android.com/reference/android/media/Image.Plane#getBuffer()
    private fun removePadding(image: ImageWrapper) {
        val sizeLuma = image.y.width * image.y.height
        val sizeChroma = image.u.width * image.u.height
        if (image.y.rowStride > image.y.width) {
            removePaddingCompact(image.y, buffer, 0)
        } else {
            buffer.position(0)
            buffer.put(image.y.buffer)
        }
        if (type == ImageFormat.YUV_420_888) {
            if (image.u.rowStride > image.u.width) {
                removePaddingCompact(image.u, buffer, sizeLuma)
                removePaddingCompact(image.v, buffer, sizeLuma + sizeChroma)
            } else {
                buffer.position(sizeLuma)
                buffer.put(image.u.buffer)
                buffer.position(sizeLuma + sizeChroma)
                buffer.put(image.v.buffer)
            }
        } else {
            if (image.u.rowStride > image.u.width * 2) {
                removePaddingNotCompact(image, buffer, sizeLuma)
            } else {
                buffer.position(sizeLuma)
                var uv = image.v.buffer
                val properUVSize = image.v.height * image.v.rowStride - 1
                if (uv.capacity() > properUVSize) {
                    uv = clipBuffer(image.v.buffer, 0, properUVSize)
                }
                buffer.put(uv)
                val lastOne = image.u.buffer[image.u.buffer.capacity() - 1]
                buffer.put(buffer.capacity() - 1, lastOne)
            }
        }
        buffer.rewind()
    }

    private fun removePaddingCompact(
        plane: PlaneWrapper,
        dst: ByteBuffer,
        offset: Int
    ) {
        require(plane.pixelStride == 1) {
            "use removePaddingCompact with pixelStride == 1"
        }

        val src = plane.buffer
        val rowStride = plane.rowStride
        var row: ByteBuffer
        dst.position(offset)
        for (i in 0 until plane.height) {
            row = clipBuffer(src, i * rowStride, plane.width)
            dst.put(row)
        }
    }

    private fun removePaddingNotCompact(
        image: ImageWrapper,
        dst: ByteBuffer,
        offset: Int
    ) {
        require(image.u.pixelStride == 2) {
            "use removePaddingNotCompact pixelStride == 2"
        }
        val width = image.u.width
        val height = image.u.height
        val rowStride = image.u.rowStride
        var row: ByteBuffer
        dst.position(offset)
        for (i in 0 until height - 1) {
            row = clipBuffer(image.v.buffer, i * rowStride, width * 2)
            dst.put(row)
        }
        row = clipBuffer(image.u.buffer, (height - 1) * rowStride - 1, width * 2)
        dst.put(row)
    }

    private fun clipBuffer(buffer: ByteBuffer, start: Int, size: Int): ByteBuffer {
        val duplicate = buffer.duplicate()
        duplicate.position(start)
        duplicate.limit(start + size)
        return duplicate.slice()
    }

    private class ImageWrapper(image:Image) {
        val width= image.width
        val height = image.height
        val y = PlaneWrapper(width, height, image.planes[0])
        val u = PlaneWrapper(width / 2, height / 2, image.planes[1])
        val v = PlaneWrapper(width / 2, height / 2, image.planes[2])

        // Check this is a supported image format
        // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
        init {
            require(y.pixelStride == 1) {
                "Pixel stride for Y plane must be 1 but got ${y.pixelStride} instead."
            }
            require(u.pixelStride == v.pixelStride && u.rowStride == v.rowStride) {
                "U and V planes must have the same pixel and row strides " +
                "but got pixel=${u.pixelStride} row=${u.rowStride} for U " +
                "and pixel=${v.pixelStride} and row=${v.rowStride} for V"
            }
            require(u.pixelStride == 1 || u.pixelStride == 2) {
                "Supported" + " pixel strides for U and V planes are 1 and 2"
            }
        }
    }

    private class PlaneWrapper(width: Int, height: Int, plane: Image.Plane) {
        val width = width
        val height = height
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
    }
}
