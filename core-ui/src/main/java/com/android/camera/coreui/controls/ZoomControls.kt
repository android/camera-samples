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
package com.android.camera.coreui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

private val CardScrimColor = Color.Black.copy(alpha = 0.6f)
private val CardBorderColor = Color.White.copy(alpha = 0.14f)
private val CardShape = RoundedCornerShape(24.dp)

/**
 * The Android camera zoom control: a cohesive Material 3 card containing circular zoom stop pills,
 * a live zoom-ratio readout badge, and a smooth slider kept in sync. Stops outside [valueRange]
 * are hidden; tapping a chip jumps the zoom to that ratio.
 */
@Composable
fun ZoomControls(
    zoomRatio: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stops: List<Float> = listOf(0.5f, 1f, 2f, 5f),
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardScrimColor, CardShape)
                .border(1.dp, CardBorderColor, CardShape)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stops
                    .filter { it in valueRange }
                    .forEach { stop ->
                        val active = abs(zoomRatio - stop) < 0.05f
                        Surface(
                            onClick = {
                                onValueChange(
                                    stop.coerceIn(valueRange.start, valueRange.endInclusive),
                                )
                            },
                            enabled = enabled,
                            shape = CircleShape,
                            color = if (active) colors.primaryContainer else Color.Transparent,
                            contentColor = if (active) colors.onPrimaryContainer else Color.White,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stop.toStopLabel(),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontFeatureSettings = "tnum",
                                        ),
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
            }

            Text(
                text = String.format(Locale.US, "%.1f×", zoomRatio),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier =
                    Modifier
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        Slider(
            value = zoomRatio.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors =
                SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Renders a zoom stop as ".5×", "1×", "2×"… (sub-1 ratios drop the leading zero). */
private fun Float.toStopLabel(): String =
    if (this < 1f) {
        "${this.toString().removePrefix("0")}×"
    } else {
        "${this.toInt()}×"
    }
