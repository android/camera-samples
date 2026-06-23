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
package com.android.camera.core.permissions

import android.Manifest
import android.os.Build

/**
 * Canonical permission sets used by camera samples. Pass one of these to
 * [com.android.camera.coreui.scaffold.CameraSampleScaffold] so a sample never has to
 * re-declare its permission boilerplate.
 */
object CameraPermissions {
    /** Permissions required to show a preview and capture a still image. */
    val PHOTO: List<String> = listOf(Manifest.permission.CAMERA)

    /**
     * Permissions required to record video with audio. On API <= 28, legacy external-storage write
     * is also requested so the recording can be written into the shared `DCIM/Camera` folder.
     */
    val VIDEO: List<String> =
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
}
