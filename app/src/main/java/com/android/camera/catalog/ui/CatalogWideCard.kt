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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.camera.catalog.R
import com.android.camera.catalog.domain.SampleCatalogItem
import com.android.camera.catalog.domain.SampleType
import com.android.camera.coretheme.AISampleCatalogTheme

internal val ReadyGreen = Color(0xFF6DD58C)

/**
 * The featured hero card for the catalog: a Material 3 rounded stage (`surfaceContainerLow`) with a
 * framed key-art preview, a translucent pill badge, the sample title/description, and a filled
 * action pill.
 */
@Composable
fun CatalogWideCard(
    catalogItem: SampleCatalogItem,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rawTitle = stringResource(catalogItem.title)
    val displayTitle = rawTitle.substringAfter("•", rawTitle).trim()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(18.dp)),
            ) {
                Image(
                    painter = painterResource(id = catalogItem.keyArt ?: R.drawable.img_keyart_multimodal),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(ReadyGreen, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.featured_badge, catalogItem.type.apiLabel()),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(catalogItem.description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = CircleShape,
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                ) {
                    Text(
                        text = stringResource(R.string.open_sample_button),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** The human-readable API label shown on catalog cards and tiles. */
@Composable
internal fun SampleType.apiLabel(): String =
    when (this) {
        SampleType.CAMERAX -> stringResource(R.string.section_camerax)
        SampleType.CAMERA2 -> stringResource(R.string.section_camera2)
    }

@Preview(showBackground = true, backgroundColor = 0xFF141218)
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
