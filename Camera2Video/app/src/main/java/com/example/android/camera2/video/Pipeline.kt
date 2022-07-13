/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.example.android.camera2.video.fragments

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.Surface
import com.example.android.camera.utils.AutoFitSurfaceView

import com.example.android.camera2.video.EncoderWrapper

abstract class Pipeline(width: Int, height: Int, fps: Int, filterOn: Boolean,
        characteristics: CameraCharacteristics, encoder: EncoderWrapper,
        viewFinder: AutoFitSurfaceView) {
    protected val width = width
    protected val height = height
    protected val fps = fps
    protected val filterOn = filterOn
    protected val characteristics = characteristics
    protected val encoder = encoder
    protected val viewFinder = viewFinder

    open public fun createPreviewRequest(session: CameraCaptureSession,
            previewStabilization: Boolean): CaptureRequest? {
        return null
    }

    public abstract fun createRecordRequest(session: CameraCaptureSession,
            previewStabilization: Boolean): CaptureRequest

    open public fun destroyWindowSurface() { }

    open public fun setPreviewSize(previewSize: Size) { }

    open public fun createResources(surface: Surface) { }

    public abstract fun getTargets(): List<Surface>

    open public fun actionDown(encoderSurface: Surface) { }

    open public fun clearFrameListener() { }

    open public fun cleanup() { }

    open public fun startRecording() { }

    open public fun stopRecording() { }
}