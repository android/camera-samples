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
package com.android.camerax.media3effects

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val TAG = "CameraXMedia3Effects"

@Composable
fun rememberCameraXMedia3EffectsController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
): CameraXMedia3EffectsController =
    remember(context, lifecycleOwner) {
        CameraXMedia3EffectsController(context, lifecycleOwner)
    }

/**
 * Renders Media3 GPU effects onto the live CameraX preview using the CameraX-Media3 bridge. A single
 * [Media3Effect] targeting [CameraEffect.PREVIEW] is added to a [UseCaseGroup] alongside the
 * [Preview] and bound with [ProcessCameraProvider.bindToLifecycle]. Switching effects at runtime is
 * just a call to [Media3Effect.setEffects] with the selected [Media3EffectMode]'s effect list — no
 * rebind required. The effect is closed in [release].
 */
@Stable
class CameraXMedia3EffectsController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null

    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

    private val media3Effect =
        Media3Effect(appContext, CameraEffect.PREVIEW, mainExecutor) { throwable ->
            Log.e(TAG, "Media3 effect processing error", throwable)
        }

    /** Applies the GPU effects for [mode] to the running preview pipeline. */
    fun applyEffect(mode: Media3EffectMode) {
        media3Effect.setEffects(mode.effects)
    }

    fun openCamera() {
        providerScope.launch {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider
            try {
                val useCaseGroup =
                    UseCaseGroup
                        .Builder()
                        .addUseCase(preview)
                        .addEffect(media3Effect)
                        .build()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    useCaseGroup,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
        media3Effect.close()
        providerScope.cancel()
    }
}
