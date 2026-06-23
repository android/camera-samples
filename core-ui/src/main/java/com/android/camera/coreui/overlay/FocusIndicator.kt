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
package com.android.camera.coreui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Draws an animated focus ring at the most recent tap [tapOffset] (in pixels), fading out after a
 * short delay. Pass the offset reported by `Camera2Preview`/`CameraXPreview`'s `onFocusTap`.
 */
@Composable
fun FocusIndicator(
    tapOffset: Offset?,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<Offset?>(null) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(tapOffset) {
        if (tapOffset != null) {
            current = tapOffset
            alpha.snapTo(1f)
            alpha.animateTo(0f, tween(durationMillis = 800))
        }
    }

    val point = current
    if (point != null && alpha.value > 0f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val radius = 36.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = alpha.value),
                radius = radius,
                center = point,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
