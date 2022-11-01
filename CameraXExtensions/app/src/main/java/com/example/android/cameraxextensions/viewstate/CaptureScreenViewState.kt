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

/**
 * Capture Screen is the top level view state. A capture screen contains a camera preview screen
 * and a post capture screen.
 */
data class CaptureScreenViewState(
    val cameraPreviewScreenViewState: CameraPreviewScreenViewState = CameraPreviewScreenViewState(),
    val postCaptureScreenViewState: PostCaptureScreenViewState = PostCaptureScreenViewState.PostCaptureScreenHiddenViewState
) {
    fun updateCameraScreen(block: (cameraPreviewScreenViewState: CameraPreviewScreenViewState) -> CameraPreviewScreenViewState): CaptureScreenViewState =
        copy(cameraPreviewScreenViewState = block(cameraPreviewScreenViewState))

    fun updatePostCaptureScreen(block: (postCaptureScreenViewState: PostCaptureScreenViewState) -> PostCaptureScreenViewState) =
        copy(postCaptureScreenViewState = block(postCaptureScreenViewState))
}
