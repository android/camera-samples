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
package com.android.ai.uicomponent

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.ai.theme.AISampleCatalogTheme

data class ChatMessage(
    val text: String,
    val timestamp: Long,
    val isIncoming: Boolean = false,
    val image: Bitmap? = null,
)

@Composable
fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState()) {
    LazyColumn(
        state = listState,
        modifier = modifier.padding(bottom = 68.dp),
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        items(items = messages, key = { it.timestamp }) { message ->
            MessageBubble(
                message = message,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private val roundCornerShapeSend = RoundedCornerShape(
    topStart = 40.dp,
    topEnd = 4.dp,
    bottomStart = 40.dp,
    bottomEnd = 40.dp,
)

private val roundCornerShapeReceive = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 40.dp,
    bottomStart = 40.dp,
    bottomEnd = 40.dp,
)

@Composable
fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    Row {
        if (message.isIncoming) {
            Icon(
                painterResource(R.drawable.ic_spark),
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp),
            )
        }
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (message.isIncoming) Alignment.Start else Alignment.End,
        ) {
            if (message.text.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .border(
                            2.dp,
                            if (message.isIncoming) Color.Transparent else MaterialTheme.colorScheme.outline,
                            shape = if (message.isIncoming) roundCornerShapeReceive else roundCornerShapeSend,
                        )
                        .clip(
                            shape = if (message.isIncoming) roundCornerShapeReceive else roundCornerShapeSend,
                        ),
                    color = if (message.isIncoming) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Column {
                        MarkdownText(
                            text = message.text,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            message.image?.let { it: Bitmap ->
                Image(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(16.dp)
                        .clip(shape = RoundedCornerShape(12.dp)),
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MessageBubbleIncomingPreview() {
    AISampleCatalogTheme {
        MessageBubble(
            message = ChatMessage(
                text = "Hi there!",
                timestamp = 124,
                isIncoming = true,
            ),
        )
    }
}

@Preview
@Composable
private fun MessageBubbleOutgoingPreview() {
    AISampleCatalogTheme {
        MessageBubble(
            message = ChatMessage(
                text = "I’m super sleepy today, what coffee drink has the most caffeine, but not too much. Also something hot.",
                timestamp = 123,
                isIncoming = false,
            ),
        )
    }
}

@Preview
@Composable
private fun MessageListPreview() {
    AISampleCatalogTheme {
        MessageList(
            messages = listOf(
                ChatMessage(
                    text = "Hi there!",
                    timestamp = 124,
                    isIncoming = true,
                ),
                ChatMessage(
                    text = "I’m super sleepy today, what coffee drink has the most caffeine, but not too much. Also something hot.",
                    timestamp = 123,
                    isIncoming = false,
                ),
            ),
        )
    }
}
