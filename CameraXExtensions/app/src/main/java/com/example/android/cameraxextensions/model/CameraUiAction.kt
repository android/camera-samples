/*
 * Copyright 2022 The Android Open Source Project
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

package com.example.android.cameraxextensions.model

import androidx.camera.core.MeteringPoint
import androidx.camera.extensions.ExtensionMode

/**
 * User initiated actions related to camera operations.
 */
sealed class CameraUiAction {
    data object RequestPermissionClick : CameraUiAction()
    data object SwitchCameraClick : CameraUiAction()
    data object ShutterButtonClick : CameraUiAction()
    data object ClosePhotoPreviewClick : CameraUiAction()
    data object ProcessProgressComplete : CameraUiAction()
    data class SelectCameraExtension(@ExtensionMode.Mode val extension: Int) : CameraUiAction()
    data class Focus(val meteringPoint: MeteringPoint) : CameraUiAction()
    data class Scale(val scaleFactor: Float) : CameraUiAction()
}