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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.camera.coretheme.monoFontFamily

private val PanelSurface = Color(0xFF14160D)
private val Hairline = Color.White.copy(alpha = 0.08f)
private val LabelColor = Color.White.copy(alpha = 0.70f)
private val ChipIdleBg = Color.White.copy(alpha = 0.05f)
private val ChipIdleBorder = Color.White.copy(alpha = 0.12f)
private val ChipIdleText = Color.White.copy(alpha = 0.85f)

/**
 * A dismissible bottom panel for sample settings (resolution, fps, extension mode, …) in the console
 * style: a dark hairline-bordered surface with a drag handle and mono labels. Tapping the scrim
 * dismisses it; taps on the panel itself are swallowed.
 */
@Composable
fun SettingsOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val panelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(panelShape)
                            .background(PanelSurface)
                            .border(1.dp, Hairline, panelShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ).navigationBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 14.dp)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f)),
                    )
                    content()
                }
            }
        }
    }
}

/** A mono uppercase section title for a [SettingsOverlay] panel. */
@Composable
fun SettingsHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style =
            TextStyle(
                fontFamily = monoFontFamily,
                fontSize = 11.sp,
                letterSpacing = 0.16.em,
            ),
        color = Color.White.copy(alpha = 0.40f),
        modifier = modifier.padding(bottom = 14.dp),
    )
}

/** A single labeled settings row with a trailing control. */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 12.sp),
            color = LabelColor,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * A labeled setting whose value is chosen from [options] via inline accent chips (replacing the old
 * Material dropdown). The selected chip is filled with the accent; the rest are scrim pills.
 */
@Composable
fun <T> SettingsDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 12.sp),
            color = LabelColor,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                OptionChip(
                    label = optionLabel(option),
                    selected = option == selected,
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    val base =
        Modifier
            .clip(shape)
    val styled =
        if (selected) {
            base.background(accent)
        } else {
            base
                .background(ChipIdleBg)
                .border(1.dp, ChipIdleBorder, shape)
        }
    Box(
        modifier = styled.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else ChipIdleText,
        )
    }
}

/** Accent-themed [SwitchColors] for switches used inside a [SettingsOverlay]. */
@Composable
fun settingsSwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
        uncheckedBorderColor = Color.White.copy(alpha = 0.22f),
    )
