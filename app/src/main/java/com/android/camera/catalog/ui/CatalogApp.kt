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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.android.camera.catalog.domain.SampleType
import com.android.camera.catalog.domain.sampleCatalog
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
            val topAppBarState = rememberTopAppBarState()
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    TwoRowsTopAppBar(
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        navigationIcon = { AppBarPill() },
                        title = { expanded ->
                            if (expanded) {
                                Text(
                                    text = stringResource(id = R.string.top_bar_title_expanded),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            } else {
                                Row {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(id = R.string.top_bar_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { innerPadding ->
                Image(
                    painter = painterResource(id = R.drawable.img_bg_landing),
                    contentDescription = stringResource(id = R.string.background_image),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillWidth,
                )

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
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                        ) {
                            var selectedFilter by remember { mutableStateOf<SampleType?>(null) }

                            SingleChoiceSegmentedButtonRow(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                SegmentedButton(
                                    selected = selectedFilter == null,
                                    onClick = { selectedFilter = null },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                ) {
                                    Text("All")
                                }
                                SegmentedButton(
                                    selected = selectedFilter == SampleType.CAMERAX,
                                    onClick = { selectedFilter = SampleType.CAMERAX },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                ) {
                                    Text("CameraX")
                                }
                                SegmentedButton(
                                    selected = selectedFilter == SampleType.CAMERA2,
                                    onClick = { selectedFilter = SampleType.CAMERA2 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                ) {
                                    Text("Camera 2")
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val filteredSamples =
                                    sampleCatalog.filter {
                                        selectedFilter == null || it.type == selectedFilter
                                    }
                                items(items = filteredSamples, key = { it.route }) {
                                    val onClick = {
                                        navController.navigate(it.route)
                                    }
                                    Column(modifier = Modifier.animateItem()) {
                                        if (it.isFeatured) {
                                            CatalogWideCard(catalogItem = it, onClick = onClick)
                                        } else {
                                            CatalogRowCard(catalogItem = it, onClick = onClick)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .padding(horizontal = 24.dp),
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
                                        onClick = {
                                            if (isPermanentlyDenied) {
                                                val intent =
                                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data =
                                                            Uri.fromParts(
                                                                "package",
                                                                context.packageName,
                                                                null,
                                                            )
                                                    }
                                                context.startActivity(intent)
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
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

@Serializable
object HomeScreen

@Composable
fun AppBarPill() {
    Row {
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.spark_android),
            contentDescription = null,
            modifier =
                Modifier
                    .height(40.dp)
                    .width(58.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp),
                    ).padding(10.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
