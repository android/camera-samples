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
package com.android.camera2.extensions

import android.hardware.camera2.CameraExtensionCharacteristics
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes

/**
 * Maps a Camera2 vendor extension constant (see [CameraExtensionCharacteristics]) to the string
 * resource holding its short human-readable label for the settings UI. The numeric values are
 * stable across API levels, so the mapping does not need to be guarded; only the constant
 * references require API 31+.
 */
@StringRes
fun extensionLabel(extension: Int): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        labelForApi31(extension)
    } else {
        R.string.extensions_label_none
    }

@RequiresApi(Build.VERSION_CODES.S)
@StringRes
private fun labelForApi31(extension: Int): Int =
    when (extension) {
        CameraExtensionCharacteristics.EXTENSION_HDR -> R.string.extensions_label_hdr
        CameraExtensionCharacteristics.EXTENSION_NIGHT -> R.string.extensions_label_night
        CameraExtensionCharacteristics.EXTENSION_BOKEH -> R.string.extensions_label_bokeh
        CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH -> R.string.extensions_label_face_retouch
        CameraExtensionCharacteristics.EXTENSION_AUTOMATIC -> R.string.extensions_label_auto
        else -> R.string.extensions_label_none
    }
