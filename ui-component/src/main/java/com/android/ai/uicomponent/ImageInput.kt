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

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.ai.theme.AISampleCatalogTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Represents the different states for the [ImageInput] composable.
 * This sealed class defines whether the input area is empty or contains an image,
 * and if it contains an image, what state it is in (e.g., being analyzed, displaying text).
 */
sealed class ImageInputType {
    class Empty(
        val hintText: String? = null,
        val onAddImage: (() -> Unit)? = null,
    ) : ImageInputType()

    sealed class WithImage(val imageUri: Uri) : ImageInputType() {
        class Image(imageUri: Uri) : WithImage(imageUri)
        class Analyzing(imageUri: Uri) : WithImage(imageUri)
        class GeneratingText(imageUri: Uri) : WithImage(imageUri)
        class WithText(
            imageUri: Uri,
            val text: String,
        ) : WithImage(imageUri)
    }
}

/**
 * A composable that serves as a container for displaying an image or a placeholder for one.
 * It adapts its content based on the provided [ImageInputType], showing either an empty state
 * with a prompt to add an image, or the image itself with various overlays depending on the
 * current state (e.g., analyzing, showing generated text).
 *
 * This composable is styled with a rounded corner border and can optionally display
 * additional content at the bottom.
 *
 * @param type The state of the image input, which determines the content to display. See
 * [ImageInputType] for possible states.
 * @param modifier The modifier to be applied to the container.
 * @param bottomContent An optional composable lambda that will be displayed at the bottom of the
 * container, inside the border. This will typically be used to display an input field.
 */
@Composable
fun ImageInput(type: ImageInputType, modifier: Modifier = Modifier, bottomContent: (@Composable () -> Unit)? = null) {
    val cornerShape = RoundedCornerShape(40.dp)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(cornerShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, cornerShape),
        ) {

            val bottomContainer = bottomContent?.let {
                @Composable {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.BottomCenter)
                            .imePadding(),
                    ) { it() }
                }
            }

            when (type) {
                is ImageInputType.Empty -> EmptyContent(type.hintText, type.onAddImage, bottomContainer)
                is ImageInputType.WithImage -> ImageContent(type, bottomContainer)
            }
        }
    }
}

private val FLOATY_SOFT_GLOW_SOFT_GLOW_PINK_1 = Color(0xFFFF7DD2)
private val FLOATY_SOFT_GLOW_SOFT_GLOW_BLUE_2 = Color(0xFF3271EA)
private val FLOATY_SOFT_GLOW_SOFT_GLOW_BLUE_1 = Color(0xFF4C8DF6)

@Composable
private fun ImageContent(type: ImageInputType.WithImage, bottomContent: (@Composable () -> Unit)?) {
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = type.imageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .drawWithCache {
                    val gradientBrush = Brush.radialGradient(
                        listOf(
                            bgColor.copy(alpha = 0.0f),
                            bgColor.copy(0.7f),
                        ),
                    )
                    onDrawWithContent {
                        drawContent()
                        if (type is ImageInputType.WithImage.GeneratingText || type is ImageInputType.WithImage.WithText) {
                            val aspectRatio = size.width / size.height
                            scale(maxOf(1f, aspectRatio), maxOf(1f, 1 / aspectRatio)) {
                                drawRect(gradientBrush)
                            }
                        }
                    }
                },
        )
        if (type is ImageInputType.WithImage.Analyzing) {
            val transition = rememberInfiniteTransition(label = "shimmer_transition")
            val progressAnimated by transition.animateFloat(
                initialValue = 0f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmer_progress",
            )
            val drawAnalyzingModifier = Modifier.drawWithCache {
                val angleRad = 200 / 180f * PI
                val x = cos(angleRad).toFloat() // Fractional x
                val y = sin(angleRad).toFloat() // Fractional y

                val radius = sqrt(size.width.pow(2) + size.height.pow(2)) / 2f
                val offset = Offset(size.width / 2, size.height / 2) + Offset(x * radius, y * radius)

                val exactOffset = Offset(
                    x = min(offset.x.coerceAtLeast(0f), size.width),
                    y = size.height - min(offset.y.coerceAtLeast(0f), size.height),
                )
                val floatySoftGlowGradient = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.31f to FLOATY_SOFT_GLOW_SOFT_GLOW_PINK_1,
                        0.43f to Color.White,
                        0.51f to FLOATY_SOFT_GLOW_SOFT_GLOW_BLUE_2,
                        1.00f to FLOATY_SOFT_GLOW_SOFT_GLOW_BLUE_1,
                    ),
                    start = Offset(size.width, size.height) - exactOffset,
                    end = exactOffset,
                    tileMode = TileMode.Mirror,
                )

                onDrawBehind {
                    val translation = -progressAnimated * size.width
                    translate(left = translation) {
                        drawRect(
                            brush = floatySoftGlowGradient,
                            size = Size(size.width * 4f, size.height),
                            alpha = 0.4f,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .then(drawAnalyzingModifier),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f)) {
                if (type is ImageInputType.WithImage.GeneratingText) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (type is ImageInputType.WithImage.WithText) {
                    MarkdownText(
                        text = type.text,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
            bottomContent?.invoke()
        }
    }
}

@Composable
private fun EmptyContent(hintText: String?, onAddImage: (() -> Unit)?, bottomContent: (@Composable () -> Unit)?) {
    val context = LocalContext.current
    val bgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val imageBitmap = BitmapFactory
                    .decodeResource(context.resources, R.drawable.img_fill).asImageBitmap()
                val patternBrush = ShaderBrush(
                    ImageShader(imageBitmap, TileMode.Repeated, TileMode.Repeated),
                )
                onDrawBehind { drawRect(patternBrush, size = size) }
            }
            .drawWithCache {
                val gradientBrush = Brush.radialGradient(
                    listOf(bgColor.copy(alpha = 0.0f), bgColor.copy(0.7f)),
                )
                onDrawBehind {
                    val aspectRatio = size.width / size.height
                    scale(maxOf(1f, aspectRatio), maxOf(1f, 1 / aspectRatio)) {
                        drawRect(gradientBrush)
                    }
                }
            },
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (onAddImage != null) {
                    GenerateButton(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.add_image),
                        onClick = onAddImage,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.primary,
                        icon = painterResource(R.drawable.ic_ai_img),
                    )
                }
            }
            hintText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
            bottomContent?.invoke()
        }
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_AddImageButton() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.Empty(onAddImage = {}),
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_BottomContent() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.Empty(onAddImage = {}),
            bottomContent = {
                Box(
                    Modifier
                        .background(Color.Red)
                        .fillMaxWidth()
                        .height(100.dp),
                )
            },
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_Empty() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.Empty(hintText = "Generate an image to edit"),
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_Empty_WithBottomContent() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.Empty(hintText = "Generate an image to edit"),
            bottomContent = {
                Box(
                    Modifier
                        .background(Color.Red)
                        .fillMaxWidth()
                        .height(100.dp),
                )
            },
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_Image() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.Image(Uri.EMPTY),
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_Image_WithBottomContent() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.Image(Uri.EMPTY),
            bottomContent = {
                Box(
                    Modifier
                        .background(Color.Red)
                        .fillMaxWidth()
                        .height(100.dp),
                )
            },
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_Analyzing() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.Analyzing(Uri.EMPTY),
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_Analyzing_WithBottomContent() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.Analyzing(Uri.EMPTY),
            bottomContent = {
                Box(
                    Modifier
                        .background(Color.Red)
                        .fillMaxWidth()
                        .height(100.dp),
                )
            },
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_GeneratingText() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.GeneratingText(Uri.EMPTY),
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_GeneratingText_WithBottomContent() {
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.GeneratingText(Uri.EMPTY),
            bottomContent = {
                Box(
                    Modifier
                        .background(Color.Red)
                        .fillMaxWidth()
                        .height(100.dp),
                )
            },
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_WithText() {
    val dummyText = "Painting of a duck\n" +
        "Swimming in a dark blue pond\n" +
        "wow look at that duck"
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.WithText(Uri.EMPTY, dummyText),
        )
    }
}

@Preview(widthDp = 380, heightDp = 652)
@Composable
private fun ImageInputPreview_WithImage_WithText_WithBottomContent() {
    val dummyText = "Painting of a **duck**\n" +
        "Swimming in a dark blue pond\n" +
        "wow look at that duck"
    AISampleCatalogTheme {
        ImageInput(
            type = ImageInputType.WithImage.WithText(Uri.EMPTY, dummyText),
            bottomContent = {
                Box(
                    Modifier
                        .background(Color.Red)
                        .fillMaxWidth()
                        .height(100.dp),
                )
            },
        )
    }
}
