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
package com.android.camera.catalog.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.camera.catalog.R
import com.android.camera.catalog.domain.SampleCatalogItem
import com.android.camera.catalog.domain.SampleCategory
import com.android.camera.catalog.domain.SampleType
import com.android.camera.catalog.domain.sampleCatalog
import kotlinx.serialization.Serializable

@Composable
fun CatalogApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                isPermanentlyDenied = activity?.let {
                    !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
                } ?: false
            }
        }

    // Check permission status when returning to the app from settings
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val currentlyGranted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED

                    hasCameraPermission = currentlyGranted
                    if (currentlyGranted) {
                        isPermanentlyDenied = false
                    }
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    if (navController.currentDestination?.route != HomeScreen::class.qualifiedName) {
                        navController.popBackStack(HomeScreen, inclusive = false)
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeScreen,
    ) {
        composable<HomeScreen> {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { innerPadding ->
                AnimatedContent(
                    targetState = hasCameraPermission,
                    label = "PermissionAnimation",
                    transitionSpec = {
                        (
                            fadeIn(animationSpec = tween(500)) +
                                slideInVertically(
                                    animationSpec = tween(500),
                                    initialOffsetY = { it / 4 },
                                )
                        ).togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                ) { hasPermission ->
                    if (hasPermission) {
                        CatalogHome(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                            onSampleClick = { navController.navigate(it.route) },
                        )
                    } else {
                        PermissionContent(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 24.dp),
                            isPermanentlyDenied = isPermanentlyDenied,
                            onAction = {
                                if (isPermanentlyDenied) {
                                    val intent =
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                    context.startActivity(intent)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        )
                    }
                }
            }
        }
        sampleCatalog.forEach {
            composable(it.route) { _ ->
                it.sampleEntryScreen()
            }
        }
    }
}

/** The Material 3 catalog: headline top bar with status pill, tonal filter pills, hero stage, and a 2-column grid. */
@Composable
private fun CatalogHome(
    modifier: Modifier = Modifier,
    onSampleClick: (SampleCatalogItem) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf<SampleCategory?>(null) }
    val filtered =
        sampleCatalog.filter { selectedFilter == null || it.category == selectedFilter }
    val featured = filtered.firstOrNull { it.isFeatured }
    val tiles = filtered.filter { !it.isFeatured }
    // CameraX samples are listed first, Camera2 below, each under its own section label.
    val cameraXTiles = tiles.filter { it.type == SampleType.CAMERAX }
    val camera2Tiles = tiles.filter { it.type == SampleType.CAMERA2 }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogHeader(count = filtered.size)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(selectedFilter = selectedFilter, onSelect = { selectedFilter = it })
        }
        if (featured != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CatalogWideCard(catalogItem = featured, onClick = { onSampleClick(featured) })
            }
        }
        if (cameraXTiles.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    text = stringResource(R.string.section_camerax),
                    count = cameraXTiles.size,
                )
            }
            items(items = cameraXTiles, key = { it.route }) { item ->
                CatalogRowCard(catalogItem = item, onClick = { onSampleClick(item) })
            }
        }
        if (camera2Tiles.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    text = stringResource(R.string.section_camera2),
                    count = camera2Tiles.size,
                )
            }
            items(items = camera2Tiles, key = { it.route }) { item ->
                CatalogRowCard(catalogItem = item, onClick = { onSampleClick(item) })
            }
        }
    }
}

@Composable
private fun CatalogHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = R.string.top_bar_title_expanded),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = R.string.top_bar_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                    text = stringResource(id = R.string.catalog_sample_count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: SampleCategory?,
    onSelect: (SampleCategory?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterPill(
            label = stringResource(id = R.string.filter_all),
            selected = selectedFilter == null,
            onClick = { onSelect(null) },
        )
        SampleCategory.entries.forEach { category ->
            FilterPill(
                label = category.label,
                selected = selectedFilter == category,
                onClick = { onSelect(category) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(id = R.string.catalog_sample_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionContent(
    isPermanentlyDenied: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = colors.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .background(colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.spark_android),
                        contentDescription = stringResource(id = R.string.camera_permission_title),
                        modifier = Modifier.size(32.dp),
                        tint = colors.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(id = R.string.camera_permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        if (isPermanentlyDenied) {
                            stringResource(id = R.string.camera_permission_denied)
                        } else {
                            stringResource(id = R.string.camera_permission_rationale)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onAction,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                ) {
                    Text(
                        text =
                            if (isPermanentlyDenied) {
                                stringResource(id = R.string.open_settings)
                            } else {
                                stringResource(id = R.string.grant_permission)
                            },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Serializable
object HomeScreen
