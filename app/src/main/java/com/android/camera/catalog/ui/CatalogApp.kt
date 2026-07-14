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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import com.android.camera.coretheme.bodyFontFamily
import com.android.camera.coretheme.monoFontFamily
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
            Scaffold(containerColor = AppBackground) { innerPadding ->
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

/** The console catalog: mono header, filter pills, featured hero, and a 2-column tile grid. */
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                SectionLabel(text = "CAMERAX")
            }
            items(items = cameraXTiles, key = { it.route }) { item ->
                CatalogRowCard(catalogItem = item, onClick = { onSampleClick(item) })
            }
        }
        if (camera2Tiles.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(text = "CAMERA2")
            }
            items(items = camera2Tiles, key = { it.route }) { item ->
                CatalogRowCard(catalogItem = item, onClick = { onSampleClick(item) })
            }
        }
    }
}

@Composable
private fun CatalogHeader(count: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(id = R.string.top_bar_title_expanded),
                style =
                    TextStyle(
                        fontFamily = bodyFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = count.toString(),
                style = TextStyle(fontFamily = monoFontFamily, fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CAMERA2 · CAMERAX · COMPOSE",
            style =
                TextStyle(
                    fontFamily = monoFontFamily,
                    fontSize = 9.sp,
                    letterSpacing = 0.12.em,
                ),
            color = Color.White.copy(alpha = 0.40f),
        )
    }
}

@Composable
private fun FilterRow(
    selectedFilter: SampleCategory?,
    onSelect: (SampleCategory?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        FilterPill(label = "All", selected = selectedFilter == null) { onSelect(null) }
        SampleCategory.entries.forEach { category ->
            FilterPill(label = category.label, selected = selectedFilter == category) {
                onSelect(category)
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = if (selected) accent else Color.White.copy(alpha = 0.14f)
    val textColor = if (selected) accent else Color.White.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier =
            Modifier
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = monoFontFamily, fontSize = 10.sp),
            color = textColor,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style =
            TextStyle(
                fontFamily = monoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 0.16.em,
            ),
        color = Color.White.copy(alpha = 0.35f),
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun PermissionContent(
    isPermanentlyDenied: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.spark_android),
                    contentDescription = stringResource(id = R.string.camera_permission_title),
                    modifier =
                        Modifier
                            .height(64.dp)
                            .width(92.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.camera_permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text =
                            if (isPermanentlyDenied) {
                                stringResource(id = R.string.open_settings)
                            } else {
                                stringResource(id = R.string.grant_permission)
                            },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Serializable
object HomeScreen
