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

package com.example.android.cameraxextensions.viewstate

import com.example.android.cameraxextensions.adapter.CameraExtensionItem

/**
 * Represents the camera preview screen view state. The camera preview screen shows camera controls
 * and the camera preview.
 */
data class CameraPreviewScreenViewState(
    val shutterButtonViewState: ShutterButtonViewState = ShutterButtonViewState(),
    val switchLensButtonViewState: SwitchLensButtonViewState = SwitchLensButtonViewState(),
    val extensionsSelectorViewState: CameraExtensionSelectorViewState = CameraExtensionSelectorViewState()
) {
    fun hideCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = false),
            switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = false),
            extensionsSelectorViewState = extensionsSelectorViewState.copy(isVisible = false)
        )

    fun showCameraControls(): CameraPreviewScreenViewState =
        copy(
            shutterButtonViewState = shutterButtonViewState.copy(isVisible = true),
            switchLensButtonViewState = switchLensButtonViewState.copy(isVisible = true),
            extensionsSelectorViewState = extensionsSelectorViewState.copy(isVisible = true)
        )

    fun enableCameraShutter(isEnabled: Boolean): CameraPreviewScreenViewState =
        copy(shutterButtonViewState = shutterButtonViewState.copy(isEnabled = isEnabled))

    fun enableSwitchLens(isEnabled: Boolean): CameraPreviewScreenViewState =
        copy(switchLensButtonViewState = switchLensButtonViewState.copy(isEnabled = isEnabled))

    fun setAvailableExtensions(extensions: List<CameraExtensionItem>): CameraPreviewScreenViewState =
        copy(extensionsSelectorViewState = extensionsSelectorViewState.copy(extensions = extensions))
}

data class CameraExtensionSelectorViewState(
    val isVisible: Boolean = false,
    val extensions: List<CameraExtensionItem> = emptyList()
)

data class ShutterButtonViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)

data class SwitchLensButtonViewState(
    val isVisible: Boolean = false,
    val isEnabled: Boolean = false
)