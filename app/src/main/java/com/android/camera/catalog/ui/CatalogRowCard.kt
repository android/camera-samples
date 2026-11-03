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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.camera.catalog.R
import com.android.camera.catalog.domain.SampleCatalogItem
import com.android.camera.catalog.domain.SampleCategory
import com.android.camera.catalog.domain.SampleTags
import com.android.camera.catalog.domain.SampleType
import com.android.camera.coretheme.AISampleCatalogTheme

/**
 * A Material 3 catalog tile for the 2-column grid, modeled after the effect tiles in
 * ImageSuperResolution: a rounded `surfaceContainerHigh` card with a 36dp circular category icon,
 * a subtle API/tag label, the sample title, and a 2-line description.
 */
@Composable
fun CatalogRowCard(
    catalogItem: SampleCatalogItem,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isCameraX = catalogItem.type == SampleType.CAMERAX
    val iconContainerColor = if (isCameraX) colors.primaryContainer else colors.secondaryContainer
    val iconTint = if (isCameraX) colors.onPrimaryContainer else colors.onSecondaryContainer
    val rawTitle = stringResource(catalogItem.title)
    val displayTitle = rawTitle.substringAfter("•", rawTitle).trim()
    val badgeText = catalogItem.tags.firstOrNull()?.label ?: catalogItem.category.label

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(iconContainerColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = catalogItem.category.icon(),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCameraX) colors.primary else colors.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(catalogItem.description),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun SampleCategory.icon(): ImageVector =
    when (this) {
        SampleCategory.IMAGES -> Icons.Rounded.PhotoCamera
        SampleCategory.VIDEO -> Icons.Rounded.Videocam
        SampleCategory.ML -> Icons.Rounded.AutoAwesome
        SampleCategory.GRAPHICS -> Icons.Rounded.FilterBAndW
        SampleCategory.EXTENSIONS -> Icons.Rounded.Extension
        SampleCategory.CONTROLS -> Icons.Rounded.Tune
    }

@Preview(showBackground = true, backgroundColor = 0xFF141218)
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
                category = SampleCategory.ML,
                tags = listOf(SampleTags.ML_KIT, SampleTags.ANALYSIS),
            )

        CatalogRowCard(catalogItem = sampleItem, onClick = {})
    }
}
