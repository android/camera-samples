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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.camera.coretheme.monoFontFamily

private val HairlineColor = Color.White.copy(alpha = 0.07f)
private val ScrimColor = Color.Black.copy(alpha = 0.42f)
private val WarmGlow = Color(0xFFFFFADC)

/** Rule-of-thirds guide lines drawn as faint hairlines across the viewfinder. */
@Composable
fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = 1.dp.toPx()
        val w = size.width
        val h = size.height
        listOf(w / 3f, w * 2f / 3f).forEach { x ->
            drawLine(HairlineColor, Offset(x, 0f), Offset(x, h), strokeWidth = stroke)
        }
        listOf(h / 3f, h * 2f / 3f).forEach { y ->
            drawLine(HairlineColor, Offset(0f, y), Offset(w, y), strokeWidth = stroke)
        }
    }
}

/** A centered scrim pill showing the sample name in uppercase mono, for the viewfinder top area. */
@Composable
fun ViewfinderTitleChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style =
            TextStyle(
                fontFamily = monoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 0.16.em,
            ),
        color = Color.White,
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(ScrimColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Torch toggle chip: a scrim pill whose label/icon turn accent when the torch is on. */
@Composable
fun TorchChip(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f)
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(ScrimColor)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FlashOn,
            contentDescription = if (on) "Turn torch off" else "Turn torch on",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (on) "TORCH ON" else "TORCH",
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.1.em,
                ),
            color = tint,
        )
    }
}

/** A warm radial glow over the preview, shown while the torch is on. Non-interactive. */
@Composable
fun TorchGlow(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val glow by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "torchGlow",
    )
    if (glow > 0f) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.42f)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                WarmGlow.copy(alpha = 0.42f * glow),
                                WarmGlow.copy(alpha = 0f),
                            ),
                        center = center,
                        radius = maxOf(size.width, size.height) * 0.6f,
                    ),
            )
        }
    }
}
