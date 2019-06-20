package com.example.android.camera2basic

import android.media.Image
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Saves a JPEG [Image] into the specified [File].
 */
internal class ImageSaver(
        /**
         * The JPEG image
         */
        private val image: Image,

        /**
         * The file we save the image into.
         */
        private val file: File
) : Runnable {

    override fun run() {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(file).apply {
                write(bytes)
            }
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        } finally {
            image.close()
            output?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    Log.e(TAG, e.toString())
                }
            }
        }
    }

    companion object {
        /**
         * Tag for the [Log].
         */
        private val TAG = "ImageSaver"
    }
}
