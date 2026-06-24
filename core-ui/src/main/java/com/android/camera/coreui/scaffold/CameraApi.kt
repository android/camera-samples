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
package com.android.camera.coreui.scaffold

import androidx.compose.ui.graphics.Color

/**
 * Which camera API a sample is built on. Passed to [CameraSampleScaffold] so every sample shows a
 * consistent badge telling the user whether they're looking at CameraX or Camera2.
 */
enum class CameraApi(
    val label: String,
    val accent: Color,
) {
    CAMERA2("Camera2", Color(0xFF4285F4)),
    CAMERAX("CameraX", Color(0xFF34A853)),
}
