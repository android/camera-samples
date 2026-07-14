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
package com.android.camera.core.camerax

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Renders a CameraX [SurfaceRequest] into a [CameraXViewfinder] with tap-to-focus support.
 *
 * @param onTapToFocus receives the tap point transformed into surface coordinates together with the
 * surface width/height, ready to hand to `CameraControl.startFocusAndMetering`.
 * @param onFocusTap receives the raw tap offset (in view pixels) so a sample can show a focus ring.
 */
@Composable
fun CameraXPreview(
    surfaceRequest: SurfaceRequest,
    modifier: Modifier = Modifier,
    onTapToFocus: ((surfaceCoords: Offset, width: Float, height: Float) -> Unit)? = null,
    onFocusTap: ((Offset) -> Unit)? = null,
) {
    val coordinateTransformer = remember { MutableCoordinateTransformer() }
    CameraXViewfinder(
        surfaceRequest = surfaceRequest,
        coordinateTransformer = coordinateTransformer,
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(surfaceRequest) {
                    detectTapGestures(
                        onTap = { offset ->
                            val surfaceCoords = with(coordinateTransformer) { offset.transform() }
                            onTapToFocus?.invoke(
                                surfaceCoords,
                                surfaceRequest.resolution.width.toFloat(),
                                surfaceRequest.resolution.height.toFloat(),
                            )
                            onFocusTap?.invoke(offset)
                        },
                    )
                },
    )
}
