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
package com.android.camerax.lowlightboost

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val TAG = "CameraXLowLightController"

@Composable
fun rememberCameraXLowLightBoostController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onCameraReady: (supported: Boolean) -> Unit,
): CameraXLowLightBoostController {
    val latestOnCameraReady by rememberUpdatedState(onCameraReady)
    return remember(context, lifecycleOwner) {
        CameraXLowLightBoostController(
            context,
            lifecycleOwner,
            onCameraReady = { supported -> latestOnCameraReady(supported) },
        )
    }
}

/**
 * Back-camera preview controller demonstrating CameraX low-light boost. After binding it reports
 * [CameraInfo.isLowLightBoostSupported] via [onCameraReady]; the toggle drives
 * [CameraControl.enableLowLightBoostAsync], which brightens dark scenes (the camera only actually
 * activates the boost when the scene is dark enough — observe [CameraInfo.getLowLightBoostState] for
 * that live state).
 */
@Stable
class CameraXLowLightBoostController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onCameraReady: (supported: Boolean) -> Unit,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

    fun openCamera() {
        providerScope.launch {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider
            try {
                provider.unbindAll()
                val camera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                onCameraReady(camera.cameraInfo.isLowLightBoostSupported)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                onCameraReady(false)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            cameraControl?.enableLowLightBoostAsync(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle low-light boost", e)
        }
    }

    fun focus(
        surfaceCoords: Offset,
        width: Float,
        height: Float,
    ) {
        val factory = SurfaceOrientedMeteringPointFactory(width, height)
        val point = factory.createPoint(surfaceCoords.x, surfaceCoords.y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
        cameraControl?.startFocusAndMetering(action)
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        providerScope.cancel()
    }
}
