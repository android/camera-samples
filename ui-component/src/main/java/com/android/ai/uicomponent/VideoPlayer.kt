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
@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.ai.uicomponent

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType.Companion.Uri
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberSeekBackButtonState
import androidx.media3.ui.compose.state.rememberSeekForwardButtonState
import com.android.ai.theme.AISampleCatalogTheme
import kotlinx.coroutines.delay

private const val CONTROLS_TIMEOUT_MS = 3000L

data class VideoPickerData(
    val title: String,
    val uri: Uri,
)

@OptIn(UnstableApi::class) // New Media3 Compose artifact is currently experimental
@Composable
fun VideoPlayer(
    player: Player?,
    modifier: Modifier = Modifier,
    forceShowControls: Boolean = false,
    videoPicker: (@Composable () -> Unit)? = null, // Optional video picker component
) {
    Box(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(40.dp))
            .clip(RoundedCornerShape(40.dp)),
    ) {
        val presentationState = rememberPresentationState(player)
        PlayerScaffold(
            cover = presentationState::coverSurface,
            surface = {
                PlayerSurface(
                    player,
                    modifier = Modifier.resizeWithContentScale(
                        contentScale = ContentScale.Fit,
                        presentationState.videoSizeDp,
                    ),
                )
            },
            controls = {
                player?.let { VideoControls(it, videoPicker = videoPicker) }
            },
            forceShowControls = forceShowControls,
        )
    }
}

@Composable
fun BoxScope.PlayerScaffold(
    cover: () -> Boolean,
    surface: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    forceShowControls: Boolean = false,
) {
    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls, forceShowControls) {
        if (!forceShowControls && showControls) {
            delay(CONTROLS_TIMEOUT_MS)
            showControls = false
        }
    }
    Box(
        modifier
            .matchParentSize()
            .drawWithContent {
                drawRect(Color.Black)
                drawContent()
                if (cover()) drawRect(Color.Black)
            }
            .clickable {
                if (!forceShowControls) {
                    showControls = !showControls
                }
            },
    ) { surface() }

    AnimatedVisibility(
        visible = forceShowControls || showControls,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) { controls() }
}

@Preview
@Composable
fun PlayerScaffoldPreview() {
    AISampleCatalogTheme {
        Box(modifier = Modifier.size(width = 400.dp, height = 200.dp)) {
            PlayerScaffold(
                cover = { false },
                surface = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Gray),
                    )
                },
                controls = { VideoControlsScaffoldPreview() },
            )
        }
    }
}

@Composable
fun VideoPickerDropdown(
    videoItems: List<VideoPickerData>,
    selectedVideo: Uri?,
    onVideoSelected: (VideoPickerData) -> Unit,
    isExpanded: Boolean,
    onDropdownExpandedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(percent = 100))
            .height(24.dp)
            .widthIn(max = 200.dp)
            .clickable(onClick = { onDropdownExpandedChanged(!isExpanded) }),
    ) {
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .align(Alignment.CenterVertically)
                .weight(1f, fill = false),
        ) {
            Text(
                text = videoItems.find { it.uri == selectedVideo }?.title ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        val iconBg = MaterialTheme.colorScheme.surfaceContainerHighest
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = context.getString(R.string.select_video_dropdown),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.drawBehind {
                drawCircle(iconBg)
            },
        )
    }

    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = { onDropdownExpandedChanged(false) },
        modifier = Modifier.widthIn(max = 240.dp),
    ) {
        videoItems.forEach { video ->
            DropdownMenuItem(
                text = { Text(video.title, style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    onVideoSelected(video)
                    onDropdownExpandedChanged(false)
                },
            )
        }
    }
}

const val VIDEO_BASE_URL = "https://storage.googleapis.com/androiddevelopers/samples_assets/gtv-videos-bucket/sample"

// Sample data for the picker
private val sampleVideosForPicker = listOf(
    VideoPickerData("Big Buck Bunny", "$VIDEO_BASE_URL/BigBuckBunny.mp4".toUri()),
    VideoPickerData("Tears of Steel", "$VIDEO_BASE_URL/TearsOfSteel.mp4".toUri()),
    VideoPickerData("For Bigger Blazes", "$VIDEO_BASE_URL/ForBiggerBlazes.mp4".toUri()),
)

@Preview
@Composable
private fun VideoPickerDropdownPreview() {
    var selectedVideo by remember { mutableStateOf(sampleVideosForPicker.first()) }
    AISampleCatalogTheme(darkTheme = true) {
        VideoPickerDropdown(
            videoItems = sampleVideosForPicker,
            selectedVideo = selectedVideo.uri,
            onVideoSelected = { selectedVideo = it },
            isExpanded = false,
            onDropdownExpandedChanged = {},
        )
    }
}

@Preview
@Composable
private fun VideoPickerDropdownPreview_LongTitle() {
    AISampleCatalogTheme(darkTheme = true) {
        val sampleData = listOf(
            VideoPickerData(
                "A very long video title will be cut off",
                "https://example.com".toUri(),
            ),
        )
        var selectedVideo by remember { mutableStateOf(sampleData.first()) }
        VideoPickerDropdown(
            videoItems = sampleData,
            selectedVideo = selectedVideo.uri,
            onVideoSelected = { selectedVideo = it },
            isExpanded = false,
            onDropdownExpandedChanged = {},
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.VideoControls(player: Player, modifier: Modifier = Modifier, videoPicker: (@Composable () -> Unit)? = null) {
    Box(modifier.matchParentSize()) {
        VideoControlsScaffold(
            videoPickerDropdown = videoPicker,
            centerControls = {
                CenterControls(
                    startButton = {
                        val state = rememberSeekBackButtonState(player)
                        SeekBackButton(
                            isEnabled = state::isEnabled,
                            onClick = state::onClick,
                        )
                    },
                    centerButton = {
                        val state = rememberPlayPauseButtonState(player)
                        PlayPauseButton(
                            showPlay = state::showPlay,
                            isEnabled = state::isEnabled,
                            onClick = state::onClick,
                        )
                    },
                    endButton = {
                        val state = rememberSeekForwardButtonState(player)
                        SeekForwardButton(
                            isEnabled = state::isEnabled,
                            onClick = state::onClick,
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun BoxScope.VideoControlsScaffold(
    videoPickerDropdown: (@Composable () -> Unit)?,
    centerControls: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    AISampleCatalogTheme(darkTheme = true) {
        val brush = Brush.radialGradient(
            listOf(
                Color.Black.copy(alpha = 0.0f),
                Color.Black.copy(0.2f),
            ),
        )
        Box(
            modifier
                .matchParentSize()
                .drawBehind {
                    val aspectRatio = size.width / size.height
                    scale(maxOf(1f, aspectRatio), maxOf(1f, 1 / aspectRatio)) {
                        drawRect(brush)
                    }
                },
        ) {
            videoPickerDropdown?.let { dropdown ->
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                ) {
                    dropdown()
                }
            }
            Row(
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            ) { centerControls() }
        }
    }
}

@OptIn(UnstableApi::class)
@Preview
@Composable
private fun VideoControlsScaffoldPreview() {
    AISampleCatalogTheme {
        Box(modifier = Modifier.size(width = 400.dp, height = 200.dp)) {
            VideoControlsScaffold(
                videoPickerDropdown = {
                    // Preview for the dropdown area
                    VideoPickerDropdownPreview()
                },
                centerControls = { CenterControlsPreview() },
            )
        }
    }
}

@Composable
private fun RowScope.CenterControls(
    startButton: @Composable () -> Unit,
    centerButton: @Composable () -> Unit,
    endButton: @Composable () -> Unit,
) {
    Spacer(
        Modifier
            .weight(0.1f)
            .widthIn(min = 4.dp, max = 24.dp),
    )
    Box(
        modifier = Modifier
            .padding(vertical = 20.dp)
            .weight(1f)
            .align(Alignment.CenterVertically)
            .wrapContentWidth(align = Alignment.End),
    ) { startButton() }
    Spacer(
        Modifier
            .weight(0.1f)
            .widthIn(min = 4.dp, max = 24.dp),
    )
    Box(
        Modifier
            .weight(1f)
            .padding(vertical = 8.dp)
            .align(Alignment.CenterVertically)
            .wrapContentWidth(Alignment.CenterHorizontally),
    ) { centerButton() }
    Spacer(
        Modifier
            .weight(0.1f)
            .widthIn(min = 4.dp, max = 24.dp),
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(vertical = 20.dp)
            .align(Alignment.CenterVertically)
            .wrapContentWidth(align = Alignment.Start),
    ) { endButton() }
    Spacer(
        Modifier
            .weight(0.1f)
            .widthIn(min = 4.dp, max = 24.dp),
    )
}

@Preview
@Composable
private fun CenterControlsPreview() {
    AISampleCatalogTheme {
        Row {
            CenterControls(
                startButton = {
                    SeekBackButtonPreview()
                },
                centerButton = {
                    PlayPauseButtonPreview()
                },
                endButton = {
                    SeekForwardButtonPreview()
                },
            )
        }
    }
}

@Preview(widthDp = 320)
@Composable
private fun CenterControlsPreview_Widths() {
    val widths = listOf(600.dp, 300.dp, 260.dp, 220.dp)
    AISampleCatalogTheme {
        Column {
            widths.forEach { width ->
                Row(
                    Modifier
                        .width(width)
                        .padding(8.dp)
                        .border(1.dp, Color.Red)
                        .align(Alignment.CenterHorizontally),
                ) {
                    CenterControls(
                        startButton = {
                            SeekBackButtonPreview()
                        },
                        centerButton = {
                            PlayPauseButtonPreview()
                        },
                        endButton = {
                            SeekForwardButtonPreview()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayPauseButton(showPlay: () -> Boolean, isEnabled: () -> Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = { onClick() },
        modifier = modifier.size(width = 124.dp, height = 72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
        ),
        shape = MaterialTheme.shapes.large,
        enabled = isEnabled(),
    ) {
        Icon(
            imageVector = if (showPlay()) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription =
            if (showPlay()) stringResource(R.string.playpause_button_play)
            else stringResource(R.string.playpause_button_pause),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeekForwardButton(isEnabled: () -> Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        enabled = isEnabled(),
    ) {
        Icon(
            imageVector = Icons.Default.Forward10,
            contentDescription = stringResource(
                R.string.seek_forward_button,
            ),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeekBackButton(isEnabled: () -> Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        enabled = isEnabled(),
    ) {
        Icon(
            imageVector = Icons.Default.Replay10,
            contentDescription = stringResource(
                R.string.seek_back_button,
            ),
        )
    }
}

@Preview
@Composable
private fun PlayPauseButtonPreview() {
    var showPlay by remember { mutableStateOf(true) }
    AISampleCatalogTheme {
        PlayPauseButton(
            showPlay = { showPlay },
            isEnabled = { true },
            onClick = { showPlay = !showPlay },
        )
    }
}

@Preview
@Composable
private fun SeekForwardButtonPreview() {
    AISampleCatalogTheme {
        SeekForwardButton(
            isEnabled = { true },
            onClick = { /* Handle click */ },
        )
    }
}

@Preview
@Composable
private fun SeekBackButtonPreview() {
    AISampleCatalogTheme {
        SeekBackButton(
            isEnabled = { true },
            onClick = { /* Handle click */ },
        )
    }
}
