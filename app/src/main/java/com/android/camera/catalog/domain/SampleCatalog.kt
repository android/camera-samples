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
package com.android.camera.catalog.domain

import android.Manifest
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.android.camera.catalog.R
import com.android.ai.samples.geminimultimodal.ui.GeminiMultimodalScreen
import com.android.ai.theme.extendedColorScheme
import com.google.firebase.ai.type.PublicPreviewAPI

@OptIn(PublicPreviewAPI::class)
@RequiresPermission(Manifest.permission.RECORD_AUDIO)
val sampleCatalog = listOf(
    SampleCatalogItem(
        title = R.string.gemini_multimodal_sample_list_title,
        description = R.string.gemini_multimodal_sample_list_description,
        route = "GeminiMultimodalScreen",
        sampleEntryScreen = { GeminiMultimodalScreen() },
        tags = listOf(SampleTags.GEMINI_FLASH, SampleTags.FIREBASE),
        isFeatured = false,
        keyArt = R.drawable.img_keyart_multimodal,
    ),

    // To create a new sample entry, add a new SampleCatalogItem here.
)

data class SampleCatalogItem(
  @param:StringRes val title: Int,
  @param:StringRes val description: Int,
  val route: String,
  val sampleEntryScreen: @Composable () -> Unit,
  val tags: List<SampleTags> = emptyList(),
  val isFeatured: Boolean = false,
  @param:DrawableRes val keyArt: Int? = null,
)

enum class SampleTags(
    val label: String,
    val backgroundColor: Color,
) {
    FIREBASE("Firebase", extendedColorScheme.firebase),
    GEMINI_FLASH("Gemini Flash", extendedColorScheme.geminiProFlash),
    GEMINI_NANO("Gemini Nano", extendedColorScheme.geminiNano),
    IMAGEN("Imagen", extendedColorScheme.imagen),
    MEDIA3("Media3", extendedColorScheme.media3),
    ML_KIT("ML Kit", extendedColorScheme.mLKit),
}
