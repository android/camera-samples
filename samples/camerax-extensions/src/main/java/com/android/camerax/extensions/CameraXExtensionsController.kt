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
package com.android.camerax.extensions

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
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
import com.android.camera.core.image.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXExtensionsController"

/** Candidate extension modes, in display order. NONE is always available; the rest are filtered. */
private val CANDIDATE_MODES =
    listOf(
        ExtensionMode.NONE,
        ExtensionMode.AUTO,
        ExtensionMode.BOKEH,
        ExtensionMode.HDR,
        ExtensionMode.NIGHT,
        ExtensionMode.FACE_RETOUCH,
    )

@Composable
fun rememberCameraXExtensionsController(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    initialMode: Int,
    onPhotoCaptured: (Bitmap) -> Unit,
    onModesReady: (List<Int>) -> Unit,
): CameraXExtensionsController {
    val latestOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)
    val latestOnModesReady by rememberUpdatedState(onModesReady)
    return remember(context, lifecycleOwner) {
        CameraXExtensionsController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            initialMode = initialMode,
            onPhotoCaptured = { bitmap -> latestOnPhotoCaptured(bitmap) },
            onModesReady = { modes -> latestOnModesReady(modes) },
        )
    }
}

@Stable
class CameraXExtensionsController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    initialMode: Int,
    private val onPhotoCaptured: (Bitmap) -> Unit,
    private val onModesReady: (List<Int>) -> Unit,
) {
    private val appContext = context.applicationContext

    private val providerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraControl: CameraControl? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null

    private var currentMode: Int = initialMode

    // Vendor extensions only operate on a single, fixed lens; this sample uses the back camera.
    private val baseSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val imageCapture =
        ImageCapture
            .Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request ->
                surfaceRequest = request
            }
        }

    fun openCamera() {
        providerScope.launch {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            cameraProvider = provider

            // ExtensionsManager initializes asynchronously against the resolved camera provider.
            try {
                val manager = ExtensionsManager.getInstanceAsync(appContext, provider).await()
                extensionsManager = manager

                val available =
                    CANDIDATE_MODES.filter { mode ->
                        mode == ExtensionMode.NONE ||
                            manager.isExtensionAvailable(baseSelector, mode)
                    }
                onModesReady(available)

                // Fall back to NONE if the previously selected mode is unsupported here.
                if (currentMode !in available) {
                    currentMode = ExtensionMode.NONE
                }
                bind(currentMode)
            } catch (exc: Exception) {
                Log.e(TAG, "ExtensionsManager init failed; binding without extensions", exc)
                extensionsManager = null
                onModesReady(listOf(ExtensionMode.NONE))
                currentMode = ExtensionMode.NONE
                bind(currentMode)
            }
        }
    }

    private fun bind(mode: Int) {
        val provider = cameraProvider ?: return
        val manager = extensionsManager

        val selector =
            if (mode == ExtensionMode.NONE || manager == null) {
                baseSelector
            } else {
                manager.getExtensionEnabledCameraSelector(baseSelector, mode)
            }

        try {
            provider.unbindAll()
            val camera =
                provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture,
                )
            cameraControl = camera.cameraControl
            currentMode = mode
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    /** Switches the active vendor extension mode and rebinds the camera. */
    fun setExtension(mode: Int) {
        if (mode == currentMode) return
        bind(mode)
    }

    fun updateTargetRotation(rotation: Int) {
        imageCapture.targetRotation = rotation
        preview.targetRotation = rotation
    }

    fun takePicture() {
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Convert synchronously on the camera executor thread so the proxy is always
                    // closed (toBitmap() closes it), even if the calling scope is gone.
                    val bitmap = image.toBitmap()
                    onPhotoCaptured(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            },
        )
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
        cameraExecutor.shutdown()
    }
}
