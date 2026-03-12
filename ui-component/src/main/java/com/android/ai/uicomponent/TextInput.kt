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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.ai.theme.AISampleCatalogTheme

@Composable
fun TextInput(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 2,
    placeholder: String = "",
    primaryButton: @Composable () -> Unit = {},
    secondaryButton: @Composable () -> Unit = {},
) {
    val roundCornerShape = RoundedCornerShape(30.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline,
                shape = roundCornerShape,
            )
            .clip(
                shape = roundCornerShape,
            )
            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        TextField(
            state = state,
            enabled = enabled,
            placeholder = {
                Text(
                    text = placeholder,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                )
            },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = maxLines),
            textStyle = MaterialTheme.typography.bodyLarge
                .copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .align(Alignment.CenterVertically)
                .padding(start = 12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        secondaryButton()
        primaryButton()
    }
}

@Composable
@Preview
fun TextInputPreview() {
    AISampleCatalogTheme {
        TextInput(
            state = TextFieldState("Message hint"),
            placeholder = "Placeholder",
            primaryButton = {
                GenerateButton(
                    text = "",
                    icon = painterResource(id = R.drawable.ic_ai_send),
                    modifier = Modifier
                        .width(72.dp)
                        .padding(4.dp),
                    onClick = {},
                )
            },
            secondaryButton = {
                SecondaryButton(
                    icon = painterResource(id = R.drawable.ic_add),
                    modifier = Modifier
                        .width(45.dp)
                        .height(56.dp),
                    onClick = {},
                )
            },
        )
    }
}
