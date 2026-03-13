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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.camera.catalog.R
import com.android.camera.catalog.domain.SampleCatalogItem
import com.android.camera.catalog.domain.SampleTags
import com.android.ai.theme.AISampleCatalogTheme
import com.android.ai.uicomponent.Tag
import com.android.camera.catalog.domain.SampleType

@Composable
fun CatalogWideCard(catalogItem: SampleCatalogItem, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
        ).widthIn(max = 646.dp),
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomEnd = 12.dp,
            bottomStart = 12.dp,
        ),
    ) {
        Column {
            Image(
                painter = painterResource(id = catalogItem.keyArt ?: R.drawable.img_keyart_multimodal),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(182.dp),
                contentScale = ContentScale.FillWidth,
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                text = stringResource(catalogItem.title),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                catalogItem.tags.forEach {
                    Tag(text = it.label, color = it.backgroundColor)
                }
            }
            Text(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                text = stringResource(catalogItem.description),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CatalogWideCardPreview() {
    AISampleCatalogTheme {
        val sampleItem = SampleCatalogItem(
            title = R.string.camera2_sample_takeaphoto_list_title,
            description = R.string.camera2_sample_takeaphoto_list_description,
            route = "GeminiMultimodalScreen",
            sampleEntryScreen = { },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.MEDIA3, SampleTags.ML_KIT),
        )

        CatalogWideCard(
            catalogItem = sampleItem,
            onClick = {},
        )
    }
}
