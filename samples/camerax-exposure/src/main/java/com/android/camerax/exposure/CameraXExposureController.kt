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
package com.android.camerax.exposure

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXExposureController"

@Composable
fun rememberCameraXExposureController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    isFrontCamera: Boolean,
    onCameraReady: (supported: Boolean, minIndex: Int, maxIndex: Int, stepEv: Float) -> Unit,
): CameraXExposureController =
    remember(context, isFrontCamera, onCameraReady) {
        CameraXExposureController(context, lifecycleOwner, isFrontCamera, onCameraReady)
    }

@Stable
class CameraXExposureController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val isFrontCamera: Boolean,
    private val onCameraReady: (supported: Boolean, minIndex: Int, maxIndex: Int, stepEv: Float) -> Unit,
) {
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                surfaceRequest = request
            }
        }

    fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val lensFacing =
                if (isFrontCamera) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }

            val cameraSelector =
                CameraSelector
                    .Builder()
                    .requireLensFacing(lensFacing)
                    .build()

            try {
                provider.unbindAll()
                val camera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                    )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                reportExposureCapabilities(camera.cameraInfo)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun reportExposureCapabilities(info: CameraInfo) {
        val exposureState = info.exposureState
        val supported = exposureState.isExposureCompensationSupported
        val range = exposureState.exposureCompensationRange
        // exposureCompensationStep is the EV change per index unit, expressed as a Rational.
        val stepEv = exposureState.exposureCompensationStep.toFloat()
        onCameraReady(supported, range.lower, range.upper, stepEv)
    }

    fun setExposureIndex(index: Int) {
        cameraControl?.setExposureCompensationIndex(index)
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        cameraControl = null
        cameraInfo = null
        cameraExecutor.shutdown()
    }
}
