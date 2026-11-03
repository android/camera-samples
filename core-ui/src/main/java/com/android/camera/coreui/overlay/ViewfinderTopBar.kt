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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.camera.coreui.R
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.LocalCameraApi

/**
 * The shared viewfinder top bar: a status-bar-padded [Box] pinned to the top of a sample's camera
 * surface so the close button (`Alignment.CenterStart`), dead-centered API + title pill
 * (`Alignment.Center`), and trailing [actions] (`Alignment.CenterEnd`) always sit on the exact
 * same horizontal axis and true screen center across every sample.
 */
@Composable
fun BoxScope.ViewfinderTopBar(
    title: String?,
    onClose: () -> Unit,
    closeIcon: ImageVector = Icons.Rounded.Close,
    showApiBadge: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val api = LocalCameraApi.current
    Box(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.52f),
                                Color.Transparent,
                            ),
                    ),
                ).statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 44.dp),
    ) {
        ScrimIconButton(
            onClick = onClose,
            imageVector = closeIcon,
            contentDescription = stringResource(R.string.close),
            size = 44.dp,
            iconSize = 22.dp,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showApiBadge) {
                ViewfinderHeaderPill(api = api, title = title)
            } else if (title != null) {
                ViewfinderTitleChip(title)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
private fun ViewfinderHeaderPill(
    api: CameraApi,
    title: String?,
    modifier: Modifier = Modifier,
) {
    val label =
        if (!title.isNullOrBlank()) {
            stringResource(R.string.top_bar_api_and_title, api.label, title)
        } else {
            api.label
        }
    Row(
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(api.accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
