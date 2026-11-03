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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.camera.coreui.R

private val RecordRed = Color(0xFFE53935)
private val ControlScrimColor = Color.Black.copy(alpha = 0.55f)
private val ControlBorderColor = Color.White.copy(alpha = 0.14f)

/**
 * The Android camera shutter button: an 80dp white outer ring around a 64dp white core that scales
 * smoothly while pressed before firing [onClick].
 */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val coreScale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "shutterScale",
    )
    Box(
        modifier =
            modifier
                .size(80.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                .border(width = 4.dp, color = Color.White, shape = CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = coreScale
                        scaleY = coreScale
                    }.background(Color.White, shape = CircleShape),
        )
    }
}

/**
 * Record toggle: a white ring whose red core morphs from a circle (idle) to a rounded square
 * (recording).
 */
@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 32.dp,
        label = "recordCorner",
    )
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 32.dp else 64.dp,
        label = "recordSize",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(80.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f), CircleShape)
                    .border(width = 4.dp, color = Color.White, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(innerSize)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(RecordRed),
            )
        }
    }
}

@Composable
fun CameraSwitchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ControlScrimColor,
        contentColor = Color.White,
        border = BorderStroke(1.dp, ControlBorderColor),
        modifier = modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Cameraswitch,
                contentDescription = stringResource(R.string.swap_camera),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A circular scrim-backed icon button (translucent dark disc + white glyph) for viewfinder and
 * media review chrome.
 */
@Composable
fun ScrimIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ControlScrimColor,
        contentColor = Color.White,
        border = BorderStroke(1.dp, ControlBorderColor),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * Standard bottom control bar: a centered [center] action (shutter/record) with optional
 * [startSlot] and [endSlot] (e.g. a camera-swap button), aligned on the same horizontal center axis
 * and padded above the gesture navigation bar.
 */
@Composable
fun CameraControlsBar(
    modifier: Modifier = Modifier,
    startSlot: @Composable () -> Unit = {},
    endSlot: @Composable () -> Unit = {},
    center: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.56f),
                            ),
                    ),
                ).navigationBarsPadding()
                .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 28.dp)
                .heightIn(min = 52.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) { startSlot() }
        Box(modifier = Modifier.align(Alignment.Center)) { center() }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) { endSlot() }
    }
}
