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
package com.android.camera.catalog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.camera.catalog.R
import com.android.camera.catalog.domain.SampleCatalogItem
import com.android.camera.catalog.domain.SampleType
import com.android.camera.coretheme.AISampleCatalogTheme
import com.android.camera.coretheme.bodyFontFamily
import com.android.camera.coretheme.monoFontFamily

/**
 * The featured "hero" card for the catalog: a bordered surface with a tall key-art region, an accent
 * API badge, the sample title/description, and an "OPEN ▸" affordance. The whole card is clickable.
 */
@Composable
fun CatalogWideCard(
    catalogItem: SampleCatalogItem,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(FeaturedSurface)
                .border(1.dp, accent.copy(alpha = 0.34f), shape)
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(130.dp),
        ) {
            Image(
                painter = painterResource(id = catalogItem.keyArt ?: R.drawable.img_keyart_multimodal),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(130.dp),
                contentScale = ContentScale.Crop,
            )
            Text(
                text = catalogItem.type.apiLabel(),
                style =
                    TextStyle(
                        fontFamily = monoFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.10.em,
                    ),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(accent)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        Column(
            modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 13.dp),
        ) {
            Text(
                text = stringResource(catalogItem.title),
                style =
                    TextStyle(
                        fontFamily = bodyFontFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(catalogItem.description),
                style =
                    TextStyle(
                        fontFamily = bodyFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 17.4.sp,
                    ),
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "OPEN ▸",
                style =
                    TextStyle(
                        fontFamily = monoFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.12.em,
                    ),
                color = accent,
            )
        }
    }
}

/** The uppercase API label shown on catalog cards/tiles. */
internal fun SampleType.apiLabel(): String =
    when (this) {
        SampleType.CAMERAX -> "CAMERAX"
        SampleType.CAMERA2 -> "CAMERA2"
    }

/** Console catalog surface tokens (see design handoff). */
internal val AppBackground = Color(0xFF0D0E0A)
internal val TileSurface = Color(0xFF14160D)
internal val FeaturedSurface = Color(0xFF13150C)
internal val TextPrimary = Color(0xFFF1F3EA)
internal val TileTextColor = Color(0xFFEEF0E6)

@Preview(showBackground = true, backgroundColor = 0xFF0D0E0A)
@Composable
private fun CatalogWideCardPreview() {
    AISampleCatalogTheme {
        val sampleItem =
            SampleCatalogItem(
                title = R.string.camerax_takeaphoto_list_title,
                description = R.string.camerax_takeaphoto_list_description,
                route = "CameraXTakeAPhotoScreen",
                sampleEntryScreen = { },
                type = SampleType.CAMERAX,
                isFeatured = true,
            )

        CatalogWideCard(catalogItem = sampleItem, onClick = {})
    }
}
