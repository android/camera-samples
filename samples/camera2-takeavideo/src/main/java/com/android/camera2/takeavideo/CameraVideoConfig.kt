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
