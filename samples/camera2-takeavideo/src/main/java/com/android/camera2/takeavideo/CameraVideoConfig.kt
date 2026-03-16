package com.android.camera2.takeavideo

import android.util.Size

data class CameraVideoConfig(
    val cameraId: String = "",
    val size: Size = Size(1920, 1080),
    val fps: Int = 30,
    val useMediaRecorder: Boolean = false,
    val dynamicRange: Long = 1L, // Default Standard
    val colorSpace: Long = -1L, // Default Unspecified
    val videoCodec: Int = 1, // Default H264
)
