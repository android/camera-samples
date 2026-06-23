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
package com.android.camera.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves captured media into the shared `DCIM/Camera` collection so it shows up in the system gallery
 * immediately. Centralizes the MediaStore vs. legacy-file branching that samples would otherwise
 * each re-implement.
 */
object MediaStoreSaver {
    private const val CAMERA_RELATIVE_PATH = "DCIM/Camera"

    private fun timestamp(): String = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US).format(Date())

    /**
     * Persists [bitmap] as a JPEG in `DCIM/Camera` and returns its [Uri], or `null` on failure.
     */
    suspend fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        displayName: String = "IMG_${timestamp()}",
        quality: Int = 95,
    ): Uri? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values =
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, CAMERA_RELATIVE_PATH)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                val resolver = context.contentResolver
                val uri =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: return@withContext null
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }.onFailure {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$displayName.jpg")
                runCatching {
                    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
                }.getOrElse { return@withContext null }
                scanFile(context, file, "image/jpeg")
                Uri.fromFile(file)
            }
        }

    /**
     * Notifies the media scanner about a file that was written directly (e.g. a [MediaRecorder]
     * output on pre-Q devices) so it appears in the gallery.
     */
    fun scanFile(
        context: Context,
        file: File,
        mimeType: String,
    ) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(mimeType),
            null,
        )
    }

    /**
     * Returns a `DCIM/Camera/VID_<timestamp>.mp4` file, creating the directory if needed. Used by
     * Camera2 video samples that record straight to a [File] via `MediaRecorder`.
     */
    @Suppress("DEPRECATION")
    fun newVideoFile(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "VID_${timestamp()}.mp4")
    }
}
