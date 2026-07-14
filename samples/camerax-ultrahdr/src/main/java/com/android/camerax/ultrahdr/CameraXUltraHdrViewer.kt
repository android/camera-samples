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
package com.android.camerax.ultrahdr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.android.camera.coretheme.monoFontFamily
import com.android.camera.coreui.controls.ScrimIconButton
import com.android.camera.coreui.overlay.ViewfinderTitleChip
import com.android.camera.coreui.state.LoadingView
import com.android.camera.coreui.widget.HdrWindowColorMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UltraHdrViewer"

/** The three ways to inspect a captured Ultra HDR image. */
enum class UltraHdrMode(
    @param:StringRes val label: Int,
) {
    SDR(R.string.ultrahdr_mode_sdr),
    GAIN_MAP(R.string.ultrahdr_mode_gain_map),
    ULTRA_HDR(R.string.ultrahdr_mode_ultra_hdr),
}

/**
 * Inspects a captured Ultra HDR JPEG three ways:
 * - **SDR** — the base image, rendered in the default (non-HDR) window color mode.
 * - **Gain map** — the gain map's contents alone, visualized as monochrome (where the extra
 *   brightness lives).
 * - **Ultra HDR** — the same base image plus its gain map, rendered with the window in
 *   [ActivityInfo.COLOR_MODE_HDR] so the display lifts the highlights.
 *
 * SDR and Ultra HDR draw the *same* decoded bitmap; the only difference is the window color mode,
 * which is exactly what an Ultra HDR gain map buys you. Gain-map/HDR features require Android 14+ and
 * an HDR-capable display; otherwise only SDR is shown.
 */
@Composable
fun BoxScope.UltraHdrViewer(
    uri: Uri,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val supportsHdr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    BackHandler(onBack = onBack)

    val assets by produceState<UltraHdrAssets?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeUltraHdr(context, uri) }
    }
    val hdrAvailable = supportsHdr && assets?.gainmapViz != null

    var mode by remember { mutableStateOf(UltraHdrMode.SDR) }
    // Once we know the image carries a gain map, default to the Ultra HDR view.
    LaunchedEffect(hdrAvailable) {
        if (hdrAvailable) mode = UltraHdrMode.ULTRA_HDR
    }

    // Drive the window color mode from the selected view; restores SDR when leaving (core-ui helper).
    HdrWindowColorMode(enabled = mode == UltraHdrMode.ULTRA_HDR)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        val current = assets
        if (current == null) {
            LoadingView()
        } else {
            val bitmap =
                if (mode == UltraHdrMode.GAIN_MAP) {
                    current.gainmapViz ?: current.image
                } else {
                    current.image
                }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.ultrahdr_image_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }

    ScrimIconButton(
        onClick = onBack,
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.ultrahdr_back),
        size = 34.dp,
        iconSize = 18.dp,
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
    )

    ViewfinderTitleChip(
        text = stringResource(R.string.ultrahdr_title_with_mode, stringResource(mode.label)),
        modifier =
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 44.dp),
    )

    if (assets != null && !hdrAvailable) {
        Text(
            text = stringResource(R.string.ultrahdr_sdr_only),
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 10.sp, letterSpacing = 0.04.em),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp, start = 32.dp, end = 32.dp),
        )
    }

    ModeSelector(
        mode = mode,
        hdrAvailable = hdrAvailable,
        onSelect = { mode = it },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
    )
}

@Composable
private fun ModeSelector(
    mode: UltraHdrMode,
    hdrAvailable: Boolean,
    onSelect: (UltraHdrMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UltraHdrMode.entries.forEach { entry ->
            val enabled = entry == UltraHdrMode.SDR || hdrAvailable
            ModePill(
                label = entry.label,
                selected = entry == mode,
                enabled = enabled,
                onClick = { if (enabled) onSelect(entry) },
            )
        }
    }
}

@Composable
private fun ModePill(
    @StringRes label: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected && enabled) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor =
        when {
            !enabled -> Color.White.copy(alpha = 0.25f)
            selected -> MaterialTheme.colorScheme.onPrimary
            else -> Color.White.copy(alpha = 0.7f)
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(background)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = stringResource(label),
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 11.sp, letterSpacing = 0.06.em),
            color = textColor,
        )
    }
}

private data class UltraHdrAssets(
    val image: Bitmap,
    val gainmapViz: Bitmap?,
)

/** Decodes the Ultra HDR JPEG (gain map preserved) and builds a monochrome gain-map visualization. */
private fun decodeUltraHdr(
    context: Context,
    uri: Uri,
): UltraHdrAssets? {
    val image =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Ultra HDR image", e)
            null
        } ?: return null

    val gainmapViz =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && image.hasGainmap()) {
            image.gainmap?.gainmapContents?.let { contents ->
                // gainmapContents may be a HARDWARE bitmap; copy to a software config we can draw.
                contents.copy(Bitmap.Config.ARGB_8888, false)?.let { visualizeGainmap(it) }
            }
        } else {
            null
        }
    return UltraHdrAssets(image = image, gainmapViz = gainmapViz)
}

/**
 * Routes the gain map's alpha channel (the per-pixel HDR boost) into RGB so it renders as a visible
 * monochrome image — bright where the gain map lifts the most. Matches the platform Ultra HDR sample.
 */
private fun visualizeGainmap(contents: Bitmap): Bitmap {
    val output = createBitmap(contents.width, contents.height)
    val paint =
        Paint().apply {
            colorFilter =
                ColorMatrixColorFilter(
                    floatArrayOf(
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        255f,
                    ),
                )
        }
    Canvas(output).drawBitmap(contents, 0f, 0f, paint)
    return output
}
