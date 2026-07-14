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
package com.android.camera2.rawcapture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.controls.ValueSlider
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.state.LoadingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MAX_EDIT_DIMENSION = 2048

/**
 * Views and lightly edits the captured DNG. The RAW is decoded by the platform's Skia RAW decoder,
 * then exposure / white balance / contrast / saturation are applied live as a single GPU
 * [ColorMatrix] color filter. The point of the sample: exposure and white balance are things you get
 * to decide *after* the shot with RAW, because the full sensor data was preserved — a baked JPEG has
 * already committed to them.
 */
@Composable
fun BoxScope.RawEditorContent(
    dngUri: Uri,
    rotationDegrees: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    val decoded by produceState<DecodeResult>(DecodeResult.Loading, dngUri, rotationDegrees) {
        value = decodeDng(context, dngUri, rotationDegrees)
    }

    var exposure by remember { mutableFloatStateOf(0f) }
    var temperature by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    val colorFilter =
        remember(exposure, temperature, contrast, saturation) {
            ColorFilter.colorMatrix(
                ColorMatrix(adjustmentMatrix(exposure, temperature, contrast, saturation)),
            )
        }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (val result = decoded) {
                DecodeResult.Loading -> {
                    LoadingView()
                }

                DecodeResult.Failed -> {
                    Text(
                        text = stringResource(R.string.rawcapture_decode_failed),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                is DecodeResult.Success -> {
                    Image(
                        bitmap = result.image,
                        contentDescription = stringResource(R.string.rawcapture_captured_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter,
                    )
                }
            }
        }

        EditPanel(
            exposure = exposure,
            onExposure = { exposure = it },
            temperature = temperature,
            onTemperature = { temperature = it },
            contrast = contrast,
            onContrast = { contrast = it },
            saturation = saturation,
            onSaturation = { saturation = it },
            onReset = {
                exposure = 0f
                temperature = 0f
                contrast = 1f
                saturation = 1f
            },
        )
    }

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.rawcapture_back_to_camera),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
    )

    ViewfinderTitleChip(
        text = stringResource(R.string.rawcapture_editor_title),
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 44.dp),
    )
}

@Composable
private fun EditPanel(
    exposure: Float,
    onExposure: (Float) -> Unit,
    temperature: Float,
    onTemperature: (Float) -> Unit,
    contrast: Float,
    onContrast: (Float) -> Unit,
    saturation: Float,
    onSaturation: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.rawcapture_editor_caption),
            color = Color.White.copy(alpha = 0.7f),
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 11.sp,
                    letterSpacing = 0.04.em,
                ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        ValueSlider(
            label = stringResource(R.string.rawcapture_exposure),
            value = exposure,
            onValueChange = onExposure,
            valueRange = -2f..2f,
            valueLabel = stringResource(R.string.rawcapture_exposure_value, "%+.1f".format(exposure)),
        )
        ValueSlider(
            label = stringResource(R.string.rawcapture_white_balance),
            value = temperature,
            onValueChange = onTemperature,
            valueRange = -1f..1f,
            valueLabel =
                if (temperature >= 0f) {
                    stringResource(R.string.rawcapture_warm_value, (temperature * 100).roundToInt())
                } else {
                    stringResource(R.string.rawcapture_cool_value, (-temperature * 100).roundToInt())
                },
        )
        ValueSlider(
            label = stringResource(R.string.rawcapture_contrast),
            value = contrast,
            onValueChange = onContrast,
            valueRange = 0.5f..1.5f,
            valueLabel = "${(contrast * 100).roundToInt()}%",
        )
        ValueSlider(
            label = stringResource(R.string.rawcapture_saturation),
            value = saturation,
            onValueChange = onSaturation,
            valueRange = 0f..2f,
            valueLabel = "${(saturation * 100).roundToInt()}%",
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = stringResource(R.string.rawcapture_reset),
                color = Color.White,
                style =
                    TextStyle(
                        fontFamily = monoFontFamily,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.em,
                    ),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onReset)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private sealed interface DecodeResult {
    data object Loading : DecodeResult

    data object Failed : DecodeResult

    data class Success(
        val image: androidx.compose.ui.graphics.ImageBitmap,
    ) : DecodeResult
}

/** Decodes the DNG (downsampled for editing) and orients it upright. */
private suspend fun decodeDng(
    context: Context,
    uri: Uri,
    rotationDegrees: Int,
): DecodeResult =
    withContext(Dispatchers.IO) {
        val decoded = decodeDownsampled(context, uri) ?: return@withContext DecodeResult.Failed
        val upright =
            if (rotationDegrees == 0) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap
                    .createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    .also { if (it !== decoded) decoded.recycle() }
            }
        DecodeResult.Success(upright.asImageBitmap())
    }

private fun decodeDownsampled(
    context: Context,
    uri: Uri,
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_EDIT_DIMENSION) {
        sampleSize *= 2
    }
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    return runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

/**
 * Builds a single 4x5 [ColorMatrix] combining the adjustments:
 * - exposure multiplies RGB by 2^EV,
 * - white balance scales red up / blue down (warm) or the reverse (cool),
 * - contrast pivots around mid-gray,
 * - saturation is applied first.
 */
private fun adjustmentMatrix(
    exposureEv: Float,
    temperature: Float,
    contrast: Float,
    saturation: Float,
): FloatArray {
    val combined = android.graphics.ColorMatrix()
    combined.setSaturation(saturation)

    val exposureScale = 2f.pow(exposureEv)
    val redScale = exposureScale * (1f + temperature * 0.4f)
    val greenScale = exposureScale
    val blueScale = exposureScale * (1f - temperature * 0.4f)
    combined.postConcat(
        android.graphics.ColorMatrix().apply { setScale(redScale, greenScale, blueScale, 1f) },
    )

    val offset = 128f * (1f - contrast)
    combined.postConcat(
        android.graphics.ColorMatrix(
            floatArrayOf(
                contrast,
                0f,
                0f,
                0f,
                offset,
                0f,
                contrast,
                0f,
                0f,
                offset,
                0f,
                0f,
                contrast,
                0f,
                offset,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        ),
    )
    return combined.array.copyOf()
}
