package com.example.android.camera2.video.recorder

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.util.Size

class VideoRecorder(context: Context) {
    private lateinit var videoSize: Size

    /**
     * Output file for video
     */
    val videoUri: Uri?
        get() = if (nextVideoAbsolutePath != null) {
            Uri.parse(nextVideoAbsolutePath)
        } else null
    private var nextVideoAbsolutePath: String? = null
    private var mediaRecorder: MediaRecorder? = null

}
