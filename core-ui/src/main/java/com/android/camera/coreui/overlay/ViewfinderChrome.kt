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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.camera.coreui.R

private val HairlineColor = Color.White.copy(alpha = 0.08f)
private val ScrimColor = Color.Black.copy(alpha = 0.55f)
private val ScrimBorderColor = Color.White.copy(alpha = 0.14f)
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

/** A translucent Material 3 pill showing a title or readout over the viewfinder. */
@Composable
fun ViewfinderTitleChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier =
            modifier
                .background(ScrimColor, CircleShape)
                .border(1.dp, ScrimBorderColor, CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Torch toggle chip: a Material 3 pill that highlights with the primary container when the torch is on. */
@Composable
fun TorchChip(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = if (on) colors.primaryContainer else ScrimColor,
        contentColor = if (on) colors.onPrimaryContainer else Color.White,
        border = if (on) null else BorderStroke(1.dp, ScrimBorderColor),
        modifier = modifier.heightIn(min = 44.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FlashOn,
                contentDescription =
                    if (on) {
                        stringResource(R.string.torch_turn_off)
                    } else {
                        stringResource(R.string.torch_turn_on)
                    },
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text =
                    if (on) {
                        stringResource(R.string.torch_label_on)
                    } else {
                        stringResource(R.string.torch_label_off)
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
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
