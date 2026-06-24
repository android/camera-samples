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
package com.android.camera.coreui.preview

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.camera.coretheme.bodyFontFamily
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.widget.VideoPlayer

/**
 * Full-screen review of a captured still in the console style: a scrim back button, the real image
 * dimensions as mono metadata, and a Retake / Done action bar. [onRetake] returns to the viewfinder;
 * [onDone] leaves the sample.
 */
@Composable
fun CapturedImagePreview(
    bitmap: Bitmap,
    onRetake: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured Photo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        ScrimIconButton(
            onClick = onDone,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            size = 34.dp,
            iconSize = 18.dp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
        )

        Text(
            text = "${bitmap.width} × ${bitmap.height}",
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.06.em,
                ),
            color = Color.White.copy(alpha = 0.55f),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, bottom = 100.dp),
        )

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReviewActionPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Refresh,
                label = "Retake",
                filled = false,
                onClick = onRetake,
            )
            ReviewActionPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Check,
                label = "Done",
                filled = true,
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun ReviewActionPill(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(26.dp)
    val contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else Color.White
    val base =
        modifier
            .height(52.dp)
            .clip(shape)
    val styled =
        if (filled) {
            base.background(accent)
        } else {
            base
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
        }
    Row(
        modifier = styled.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style =
                TextStyle(
                    fontFamily = bodyFontFamily,
                    fontSize = 14.sp,
                    fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                ),
            color = contentColor,
        )
    }
}

/**
 * Full-screen review of a captured video using the shared [VideoPlayer]. The player is created for
 * the supplied [uri], loops, and is released automatically when this leaves composition.
 */
@Composable
fun CapturedVideoPreview(
    uri: Uri,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        VideoPlayer(player = player)
        ScrimIconButton(
            onClick = onDismiss,
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            size = 34.dp,
            iconSize = 18.dp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
        )
    }
}
