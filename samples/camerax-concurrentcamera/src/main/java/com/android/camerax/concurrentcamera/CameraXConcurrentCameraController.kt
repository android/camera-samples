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
package com.android.camerax.concurrentcamera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

private const val TAG = "CameraXConcurrent"

@Composable
fun rememberCameraXConcurrentCameraController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onUnsupported: () -> Unit,
): CameraXConcurrentCameraController =
    remember(context, lifecycleOwner, onUnsupported) {
        CameraXConcurrentCameraController(context, lifecycleOwner, onUnsupported)
    }

/**
 * Drives two cameras simultaneously with CameraX [ConcurrentCamera][androidx.camera.core.ConcurrentCamera].
 * It gates on [ProcessCameraProvider.getAvailableConcurrentCameraInfos] and binds two
 * [SingleCameraConfig]s (back + front), each with its own [Preview], via the
 * `bindToLifecycle(List)` overload — surfacing each camera as its own [SurfaceRequest]. Concurrent
 * mode caps each stream's resolution, so the previews are intentionally low-res.
 */
@Stable
class CameraXConcurrentCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onUnsupported: () -> Unit,
) {
    var backSurfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    var frontSurfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null

    private val backPreview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> backSurfaceRequest = request }
        }

    private val frontPreview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> frontSurfaceRequest = request }
        }

    fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            if (provider.availableConcurrentCameraInfos.isEmpty()) {
                onUnsupported()
                return@addListener
            }

            try {
                val backConfig =
                    SingleCameraConfig(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        UseCaseGroup.Builder().addUseCase(backPreview).build(),
                        lifecycleOwner,
                    )
                val frontConfig =
                    SingleCameraConfig(
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        UseCaseGroup.Builder().addUseCase(frontPreview).build(),
                        lifecycleOwner,
                    )
                provider.unbindAll()
                provider.bindToLifecycle(listOf(backConfig, frontConfig))
            } catch (e: Exception) {
                Log.e(TAG, "Concurrent camera binding failed", e)
                onUnsupported()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
    }
}
