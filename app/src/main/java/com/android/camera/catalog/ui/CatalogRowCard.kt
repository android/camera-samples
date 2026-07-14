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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.camera.catalog.R
import com.android.camera.catalog.domain.SampleCatalogItem
import com.android.camera.catalog.domain.SampleTags
import com.android.camera.catalog.domain.SampleType
import com.android.camera.coretheme.AISampleCatalogTheme
import com.android.camera.coretheme.bodyFontFamily
import com.android.camera.coretheme.monoFontFamily

/**
 * A compact catalog tile for the 2-column grid: a mono API label (accent for CameraX, muted for
 * Camera2), a category dot from the sample's first tag, and the sample title. Clickable as a whole.
 */
@Composable
fun CatalogRowCard(
    catalogItem: SampleCatalogItem,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    val isCameraX = catalogItem.type == SampleType.CAMERAX
    val apiColor =
        if (isCameraX) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)
    val dotColor = catalogItem.tags.firstOrNull()?.backgroundColor ?: Color.White.copy(alpha = 0.18f)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(TileSurface)
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = 84.dp)
                .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = catalogItem.type.apiLabel(),
                style =
                    TextStyle(
                        fontFamily = monoFontFamily,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.08.em,
                    ),
                color = apiColor,
            )
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
        }
        Text(
            text = stringResource(catalogItem.title),
            style =
                TextStyle(
                    fontFamily = bodyFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.9.sp,
                ),
            color = TileTextColor,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0E0A)
@Composable
private fun CatalogRowCardPreview() {
    AISampleCatalogTheme {
        val sampleItem =
            SampleCatalogItem(
                title = R.string.camerax_imagelabeling_list_title,
                description = R.string.camerax_imagelabeling_list_description,
                route = "CameraXImageLabelingScreen",
                sampleEntryScreen = { },
                type = SampleType.CAMERAX,
                tags = listOf(SampleTags.ML_KIT, SampleTags.ANALYSIS),
            )

        CatalogRowCard(catalogItem = sampleItem, onClick = {})
    }
}
