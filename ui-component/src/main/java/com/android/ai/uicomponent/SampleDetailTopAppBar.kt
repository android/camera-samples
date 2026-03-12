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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.ai.theme.AISampleCatalogTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SampleDetailTopAppBar(
    sampleName: String,
    sampleDescription: String,
    sourceCodeUrl: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    topAppBarState: TopAppBarState = rememberTopAppBarState(),
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState),
) {
    val hasScrolled by remember { derivedStateOf { scrollBehavior.state.heightOffset != 0F } }
    TwoRowsTopAppBar(
        colors = topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.primary,
            scrolledContainerColor = Color.Transparent,
            subtitleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = { expanded ->
            Text(
                text = sampleName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = { expanded ->
            Text(
                text = sampleDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = { BackButton(onClick = onBackClick) },
        actions = {
            SeeCodeButton(
                sourceCodeUrl = sourceCodeUrl,
                withText = !hasScrolled,
            )
        },
        scrollBehavior = scrollBehavior,
        modifier = modifier.padding(bottom = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(backgroundColor = 0XFF000000, showBackground = true)
@Composable
fun SampleDetailTopAppBarPreview() {
    AISampleCatalogTheme {
        SampleDetailTopAppBar(
            sampleName = "Sample Name",
            sampleDescription = "Sample Description",
            sourceCodeUrl = "https://example.com/source-code",
            onBackClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun SampleDetailTopAppBarPreview_CollapseWhenContentIsScrolled() {
    AISampleCatalogTheme {
        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                SampleDetailTopAppBar(
                    sampleName = "Sample Name",
                    sampleDescription = "Sample Description",
                    sourceCodeUrl = "https://example.com/source-code",
                    topAppBarState = topAppBarState,
                    scrollBehavior = scrollBehavior,
                    onBackClick = {},
                )
            },
        ) { innerPadding ->
            val gradient = Brush.verticalGradient(listOf(Color.LightGray, Color.DarkGray))
            Box(
                Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
                    .requiredHeight(1000.dp)
                    .background(brush = gradient),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun SampleDetailTopAppBarPreview_CollapseWhenToolbarIsScrolled() {
    AISampleCatalogTheme {
        Scaffold(
            topBar = {
                SampleDetailTopAppBar(
                    sampleName = "Sample Name",
                    sampleDescription = "Sample Description",
                    sourceCodeUrl = "https://example.com/source-code",
                    onBackClick = {},
                )
            },
        ) { innerPadding ->
            val gradient = Brush.verticalGradient(listOf(Color.LightGray, Color.DarkGray))
            Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(brush = gradient),
            )
        }
    }
}
