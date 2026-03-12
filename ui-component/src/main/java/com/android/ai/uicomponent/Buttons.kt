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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.ai.theme.AISampleCatalogTheme
import com.android.ai.theme.Contrast

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(
    modifier: Modifier = Modifier
        .height(40.dp)
        .requiredWidthIn(min = 40.dp),
    text: String = "",
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    icon: Painter? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            containerColor = containerColor,
        ),
        contentPadding = if (text.isEmpty())
            PaddingValues(0.dp) else
            ButtonDefaults.TextButtonWithIconContentPadding,
        onClick = { onClick() },
        enabled = enabled,
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(width = 24.dp, height = 24.dp),
            )
        }
        AnimatedContent(text.isNotEmpty()) { hasText ->
            if (hasText) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Preview
@Composable
fun PrimaryButtonLightPreview() {
    AISampleCatalogTheme(
        darkTheme = false,
        contrast = Contrast.HIGH,
    ) {
        PrimaryButton(
            text = "Primary button",
            icon = rememberVectorPainter(Icons.Default.AccountBox),
            onClick = {},
        )
    }
}

@Preview
@Composable
fun PrimaryButtonDarkPreview() {
    AISampleCatalogTheme(
        darkTheme = true,
        contrast = Contrast.DEFAULT,
    ) {
        PrimaryButton(
            text = "Primary button",
            icon = rememberVectorPainter(Icons.Default.AccountBox),
            onClick = {},
        )
    }
}

@Preview
@Composable
fun PrimaryButtonWithIconPreview() {
    AISampleCatalogTheme {
        PrimaryButton(
            icon = rememberVectorPainter(Icons.Filled.Code),
            onClick = {},
        )
    }
}

@Composable
fun GenerateButton(
    modifier: Modifier = Modifier,
    text: String = "",
    contentColor: Color = MaterialTheme.colorScheme.onTertiary,
    containerColor: Color = MaterialTheme.colorScheme.tertiary,
    enabled: Boolean = true,
    icon: Painter? = painterResource(id = R.drawable.ic_ai_edit),
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier
            .height(56.dp)
            .border(
                if (enabled) {
                    BorderStroke(0.dp, Color.Transparent)
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                },
                shape = RoundedCornerShape(30.dp),
            ),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            containerColor = containerColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Transparent,
        ),
        onClick = { onClick() },
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (enabled) contentColor else MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.size(width = 24.dp, height = 24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview
@Composable
fun GenerateButtonPreview() {
    AISampleCatalogTheme {
        GenerateButton(
            text = "Generate",
            onClick = {},
        )
    }
}

@Preview
@Composable
fun GenerateButtonDisabledPreview() {
    AISampleCatalogTheme {
        GenerateButton(
            text = "Generate",
            enabled = false,
            onClick = {},
        )
    }
}

@Composable
fun SecondaryButton(
    modifier: Modifier = Modifier,
    text: String = "",
    icon: Painter? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.height(48.dp),
        colors = colors,
        enabled = enabled,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
        contentPadding = if (text.isEmpty()) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
        onClick = { onClick() },
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.contentColor),
                modifier = Modifier.size(24.dp),
            )
            if (text.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview
@Composable
fun SecondaryButtonPreview() {
    AISampleCatalogTheme {
        SecondaryButton(
            text = "Outlined button",
            icon = painterResource(id = R.drawable.ic_ai_img),
            onClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackButton(modifier: Modifier = Modifier, imageVector: ImageVector = Icons.AutoMirrored.Filled.ArrowBack, onClick: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.outline) {
        OutlinedIconButton(
            shape = IconButtonDefaults.smallSquareShape,
            onClick = { onClick() },
            modifier = modifier,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                )
            }
        }
    }
}

@Preview
@Composable
fun BackButtonPreview() {
    AISampleCatalogTheme {
        BackButton(
            onClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UndoButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.outline) {
        OutlinedIconButton(
            shape = IconButtonDefaults.smallRoundShape,
            onClick = { onClick() },
            modifier = modifier,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_redo),
                    contentDescription = stringResource(R.string.undo),
                )
            }
        }
    }
}

@Preview
@Composable
fun UndoButtonPreview() {
    AISampleCatalogTheme {
        UndoButton(
            onClick = {},
        )
    }
}
