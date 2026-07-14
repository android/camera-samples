/*
 * Copyright 2025 The Android Open Source Project
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ReticleSize = 74.dp

/**
 * Draws an accent square focus reticle at the most recent tap [tapOffset] (in pixels). The reticle
 * glides to a new tap point, then dims (rather than disappearing) so the focus point stays visible.
 * Pass the offset reported by `Camera2Preview`/`CameraXPreview`'s `onFocusTap`.
 */
@Composable
fun FocusIndicator(
    tapOffset: Offset?,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val halfPx = with(LocalDensity.current) { ReticleSize.toPx() / 2f }

    var current by remember { mutableStateOf<Offset?>(null) }
    val opacity = remember { Animatable(0f) }
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }

    LaunchedEffect(tapOffset) {
        if (tapOffset != null) {
            // First appearance snaps to the tap; later taps glide the reticle across.
            if (current == null) {
                x.snapTo(tapOffset.x)
                y.snapTo(tapOffset.y)
            }
            current = tapOffset
            opacity.snapTo(1f)
            launch { x.animateTo(tapOffset.x, tween(durationMillis = 180)) }
            launch { y.animateTo(tapOffset.y, tween(durationMillis = 180)) }
            delay(900)
            opacity.animateTo(0.45f, tween(durationMillis = 400))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (current != null && opacity.value > 0f) {
            val tint = accent.copy(alpha = opacity.value)
            Box(
                modifier =
                    Modifier
                        .offset {
                            IntOffset((x.value - halfPx).roundToInt(), (y.value - halfPx).roundToInt())
                        }.size(ReticleSize)
                        .border(width = 1.5.dp, color = tint, shape = RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tint),
                )
            }
        }
    }
}
