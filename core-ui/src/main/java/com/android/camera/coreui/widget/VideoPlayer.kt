/*
 * Copyright 2025 The Android Open Source Project
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
@file:kotlin.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.camera.coreui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState

/**
 * A Compose-first ExoPlayer review surface: Media3's [PlayerSurface] (no Android views in our code)
 * scaled to fit the video, on a black backdrop. Tap to toggle play/pause. The caller owns the
 * [Player] lifecycle and any auto-play/loop configuration.
 */
@Composable
fun VideoPlayer(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val presentationState = rememberPresentationState(player)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier =
                Modifier
                    .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        if (player.isPlaying) player.pause() else player.play()
                    },
        )
    }
}
