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
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
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
     * A video destination created in `DCIM/Camera` that a `MediaRecorder` can write into.
     *
     * On API 29+ the recording targets a pending [MediaStore] entry through [fileDescriptor]; the
     * underlying [ParcelFileDescriptor] must stay open from `MediaRecorder.prepare()` through
     * `MediaRecorder.stop()`. On pre-Q devices the recording targets [legacyFile] directly (covered
     * by the `WRITE_EXTERNAL_STORAGE` permission with `maxSdkVersion=28`).
     *
     * Call [finalize] after a successful `stop()` to publish the video, or [discard] to drop a
     * failed/aborted recording.
     */
    class PendingVideo internal constructor(
        val uri: Uri,
        private val pfd: ParcelFileDescriptor?,
        private val legacyFile: File?,
    ) {
        /**
         * `true` when the recording targets a [MediaStore] descriptor (Q+); the caller should use
         * [fileDescriptor]. `false` on pre-Q, where the caller should use [legacyFilePath].
         */
        val usesFileDescriptor: Boolean get() = pfd != null

        /** The file descriptor to hand to `MediaRecorder.setOutputFile(FileDescriptor)` (Q+ only). */
        val fileDescriptor: FileDescriptor get() = pfd!!.fileDescriptor

        /** The absolute path to hand to `MediaRecorder.setOutputFile(String)` (pre-Q only). */
        val legacyFilePath: String get() = legacyFile!!.absolutePath

        /**
         * Publishes the recorded video so it shows up in the gallery. On Q+ this closes the
         * descriptor and clears `IS_PENDING`; on pre-Q it triggers a media scan of [legacyFile].
         */
        fun finalize(context: Context) {
            if (pfd != null) {
                pfd.close()
                val values =
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, values, null, null)
            } else {
                scanFile(context, legacyFile!!, "video/mp4")
            }
        }

        /**
         * Drops a failed or aborted recording, closing the descriptor and deleting the entry/file.
         */
        fun discard(context: Context) {
            pfd?.close()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(uri, null, null)
            } else {
                legacyFile?.delete()
            }
        }
    }

    /**
     * Creates a pending video destination in `DCIM/Camera`, returning a [PendingVideo] the caller
     * records into, or `null` on failure. Used by Camera2 video samples that record straight to a
     * `MediaRecorder`.
     */
    fun newPendingVideo(
        context: Context,
        displayName: String = "VID_${timestamp()}",
    ): PendingVideo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "$displayName.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, CAMERA_RELATIVE_PATH)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val uri =
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
            val pfd =
                runCatching { resolver.openFileDescriptor(uri, "w") }.getOrNull()
                    ?: run {
                        resolver.delete(uri, null, null)
                        return null
                    }
            PendingVideo(uri, pfd, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$displayName.mp4")
            PendingVideo(Uri.fromFile(file), null, file)
        }
    }

    /**
     * Deletes media previously saved through this object. Handles both `content://` MediaStore URIs
     * (Q+) and `file://` URIs (pre-Q). Returns `true` if something was removed.
     */
    fun deleteMedia(
        context: Context,
        uri: Uri,
    ): Boolean =
        when (uri.scheme) {
            "content" -> context.contentResolver.delete(uri, null, null) > 0
            "file" -> uri.path?.let { File(it).delete() } ?: false
            else -> false
        }
}
