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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** The standard white circular shutter button used by photo samples. */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White, shape = CircleShape),
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
        targetValue = if (isRecording) 8.dp else 28.dp,
        label = "recordCorner",
    )
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 32.dp else 56.dp,
        label = "recordSize",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(width = 4.dp, color = Color.White, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(innerSize)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(Color.Red),
            )
        }
    }
}

/** White camera-swap (front/back) button. */
@Composable
fun CameraSwitchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = "Swap Camera",
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** White overlay back button, used at the top-start of a camera screen. */
@Composable
fun CameraOverlayButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** White overlay back arrow. */
@Composable
fun CameraBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CameraOverlayButton(
        onClick = onClick,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        modifier = modifier,
    )
}

/** White overlay close button. */
@Composable
fun CameraCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CameraOverlayButton(
        onClick = onClick,
        imageVector = Icons.Filled.Close,
        contentDescription = "Close",
        modifier = modifier,
    )
}

/**
 * Standard bottom control bar: a centered [center] action (shutter/record) with optional
 * [startSlot] and [endSlot] (e.g. a camera-swap button), matching the layout shared by every
 * capture sample.
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
                .padding(bottom = 48.dp, start = 32.dp, end = 48.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) { startSlot() }
        Box(modifier = Modifier.align(Alignment.Center)) { center() }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) { endSlot() }
    }
}
