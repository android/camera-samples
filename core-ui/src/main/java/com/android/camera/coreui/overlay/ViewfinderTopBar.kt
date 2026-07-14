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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.camera.coreui.controls.ScrimIconButton

/**
 * The shared viewfinder top bar: a single status-bar-padded [Row] pinned to the top of a sample's
 * camera surface so the close button, centered title chip, and trailing [actions] always sit at the
 * same vertical position across every sample.
 *
 * The bar is offset below [com.android.camera.coreui.scaffold.CameraSampleScaffold]'s ApiBadge (which
 * floats at the very top center) so the badge and the title chip never collide. The title chip is
 * held at the row's vertical center inside a weighted [Box] so it stays centered even when the start
 * and end slots have different widths.
 *
 * @param title centered title; pass `null` to omit the chip.
 * @param onClose invoked by the leading [ScrimIconButton].
 * @param closeIcon defaults to [Icons.Filled.Close]; pass an arrow for screens that "go back".
 * @param actions trailing controls (torch, settings, camera-swap, record quality, etc.).
 */
@Composable
fun BoxScope.ViewfinderTopBar(
    title: String?,
    onClose: () -> Unit,
    closeIcon: ImageVector = Icons.Filled.Close,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                // Offset below the scaffold's ApiBadge (top-center) so the two never overlap.
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrimIconButton(
            onClick = onClose,
            imageVector = closeIcon,
            contentDescription = "Close",
            size = 34.dp,
            iconSize = 18.dp,
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            title?.let { ViewfinderTitleChip(it) }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
