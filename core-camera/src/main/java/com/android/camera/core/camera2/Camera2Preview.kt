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
package com.android.camera.core.camera2

import androidx.camera.viewfinder.compose.Viewfinder
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.camera.core.display.rememberDisplayRotation

/**
 * Renders a [BaseCamera2Controller]'s preview through the Compose `Viewfinder` (no Android views) and
 * wires up the boilerplate every Camera2 sample needs: display-rotation transforms, lifecycle-driven
 * open/close, and tap-to-focus. The controller publishes a `surfaceRequest`; this composable feeds
 * the resulting surface back via `onSurfaceSession`. The owning screen creates the controller and
 * calls `release()` when it leaves composition.
 *
 * @param onFocusTap invoked with the tapped offset (in view pixels) after focus is requested, so a
 * sample can show a focus indicator.
 */
@Composable
fun Camera2Preview(
    controller: BaseCamera2Controller,
    modifier: Modifier = Modifier,
    onFocusTap: ((Offset) -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val displayRotation = rememberDisplayRotation()

    LaunchedEffect(displayRotation, controller) {
        controller.updateDisplayRotation(displayRotation)
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_RESUME -> {
                        controller.openCamera()
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        controller.closeCamera()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.closeCamera()
        }
    }

    val request = controller.surfaceRequest
    if (request != null) {
        Viewfinder(
            surfaceRequest = request,
            transformationInfo = controller.transformationInfo,
            modifier =
                modifier
                    .fillMaxSize()
                    .pointerInput(controller) {
                        detectTapGestures(
                            onTap = { offset ->
                                controller.focus(offset, size.width.toFloat(), size.height.toFloat())
                                onFocusTap?.invoke(offset)
                            },
                        )
                    },
        ) {
            onSurfaceSession {
                controller.runViewfinderSession(surface)
            }
        }
    }
}
