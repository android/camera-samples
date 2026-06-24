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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.camera.coretheme.monoFontFamily
import java.util.Locale
import kotlin.math.abs

private val ScrimColor = Color.Black.copy(alpha = 0.42f)

/**
 * The console zoom control: a centered zoom-value pill, an accent slider, and discrete zoom-stop
 * chips kept in sync with the slider. Stops outside [valueRange] are hidden; tapping a chip jumps the
 * zoom to that ratio.
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
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = String.format(Locale.US, "%.1f×", zoomRatio),
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            color = accent,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(ScrimColor)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
        )

        Slider(
            value = zoomRatio.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            colors =
                SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stops
                .filter { it in valueRange }
                .forEach { stop ->
                    val active = abs(zoomRatio - stop) < 0.05f
                    val shape = RoundedCornerShape(15.dp)
                    val chipBase =
                        Modifier
                            .size(width = 42.dp, height = 30.dp)
                            .clip(shape)
                    val chipModifier =
                        if (active) {
                            chipBase.background(accent)
                        } else {
                            chipBase
                                .background(ScrimColor)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                        }
                    Box(
                        modifier =
                            chipModifier.clickable(enabled = enabled) {
                                onValueChange(stop.coerceIn(valueRange.start, valueRange.endInclusive))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stop.toStopLabel(),
                            style =
                                TextStyle(
                                    fontFamily = monoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color =
                                if (active) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    Color.White.copy(alpha = 0.85f)
                                },
                        )
                    }
                }
        }
    }
}

/** Renders a zoom stop as ".5×", "1×", "2×"… (sub-1 ratios drop the leading zero). */
private fun Float.toStopLabel(): String =
    if (this < 1f) {
        "${this.toString().removePrefix("0")}×"
    } else {
        "${this.toInt()}×"
    }
