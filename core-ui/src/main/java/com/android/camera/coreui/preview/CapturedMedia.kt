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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.camera.coreui.R
import com.android.camera.coreui.overlay.ViewfinderTopBar
import com.android.camera.coreui.widget.VideoPlayer

/**
 * Full-screen Material 3 review of a captured still: a circular back button, a translucent pill
 * showing the image dimensions, and a Material 3 Retake / Done action bar. [onRetake] returns to
 * the viewfinder; [onDone] leaves the sample.
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
            contentDescription = stringResource(R.string.captured_photo_description),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        ViewfinderTopBar(
            title = stringResource(R.string.review_dimensions, bitmap.width, bitmap.height),
            onClose = onDone,
            closeIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            showApiBadge = false,
        )

        ReviewActionsBar(
            onRetake = onRetake,
            onDone = onDone,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun ReviewActionsBar(
    onRetake: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onRetake,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.review_retake),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Button(
            onClick = onDone,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.review_done),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * Full-screen review of a captured video using the shared [VideoPlayer]. The player is created for
 * the supplied [uri], loops, and is released automatically when this leaves composition.
 */
@Composable
fun CapturedVideoPreview(
    uri: Uri,
    onRetake: () -> Unit,
    onDone: () -> Unit,
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

        ViewfinderTopBar(
            title = null,
            onClose = onDone,
            closeIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            showApiBadge = false,
        )

        ReviewActionsBar(
            onRetake = onRetake,
            onDone = onDone,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        )
    }
}
