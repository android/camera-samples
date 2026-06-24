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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.android.camera.catalog.R
import com.android.camera.coretheme.extendedColorScheme
import com.android.camera2.extensions.Camera2ExtensionsScreen
import com.android.camera2.hdrviewfinder.Camera2HdrViewfinderScreen
import com.android.camera2.manualcontrols.Camera2ManualControlsScreen
import com.android.camera2.qrscanner.Camera2QrScannerScreen
import com.android.camera2.rawcapture.Camera2RawCaptureScreen
import com.android.camera2.slowmotion.Camera2SlowMotionScreen
import com.android.camera2.takeaphoto.Camera2TakeAPhotoScreen
import com.android.camera2.takeavideo.Camera2TakeAVideoScreen
import com.android.camera2.zoomandtorch.Camera2ZoomAndTorchScreen
import com.android.camerax.concurrentcamera.CameraXConcurrentCameraScreen
import com.android.camerax.effects.CameraXEffectsScreen
import com.android.camerax.exposure.CameraXExposureScreen
import com.android.camerax.extensions.CameraXExtensionsScreen
import com.android.camerax.greenscreen.CameraXGreenScreenScreen
import com.android.camerax.imagelabeling.CameraXImageLabelingScreen
import com.android.camerax.luminosity.CameraXLuminosityScreen
import com.android.camerax.qrscanner.CameraXQrScannerScreen
import com.android.camerax.takeaphoto.CameraXTakeAPhotoScreen
import com.android.camerax.takeavideo.CameraXTakeAVideoScreen
import com.android.camerax.ultrahdr.CameraXUltraHdrScreen
import com.android.camerax.videopauseresume.CameraXVideoPauseResumeScreen
import com.android.camerax.zoomandtorch.CameraXZoomAndTorchScreen

val sampleCatalog =
    listOf(
        SampleCatalogItem(
            title = R.string.camerax_takeaphoto_list_title,
            description = R.string.camerax_takeaphoto_list_description,
            route = "CameraXTakeAPhotoScreen",
            sampleEntryScreen = { CameraXTakeAPhotoScreen() },
            type = SampleType.CAMERAX,
            isFeatured = true,
        ),
        SampleCatalogItem(
            title = R.string.camera2_takeaphoto_list_title,
            description = R.string.camera2_takeaphoto_list_description,
            route = "Camera2TakeAPhotoScreen",
            sampleEntryScreen = { Camera2TakeAPhotoScreen() },
            type = SampleType.CAMERA2,
        ),
        SampleCatalogItem(
            title = R.string.camerax_takeavideo_list_title,
            description = R.string.camerax_takeavideo_list_description,
            route = "CameraXTakeAVideoScreen",
            sampleEntryScreen = { CameraXTakeAVideoScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.VIDEO),
        ),
        SampleCatalogItem(
            title = R.string.camera2_takeavideo_list_title,
            description = R.string.camera2_takeavideo_list_description,
            route = "Camera2TakeAVideoScreen",
            sampleEntryScreen = { Camera2TakeAVideoScreen() },
            type = SampleType.CAMERA2,
            tags = listOf(SampleTags.VIDEO),
        ),
        SampleCatalogItem(
            title = R.string.camera2_slowmotion_list_title,
            description = R.string.camera2_slowmotion_list_description,
            route = "Camera2SlowMotionScreen",
            sampleEntryScreen = { Camera2SlowMotionScreen() },
            type = SampleType.CAMERA2,
            tags = listOf(SampleTags.VIDEO),
        ),
        SampleCatalogItem(
            title = R.string.camerax_qrscanner_list_title,
            description = R.string.camerax_qrscanner_list_description,
            route = "CameraXQrScannerScreen",
            sampleEntryScreen = { CameraXQrScannerScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.ML_KIT),
        ),
        SampleCatalogItem(
            title = R.string.camerax_imagelabeling_list_title,
            description = R.string.camerax_imagelabeling_list_description,
            route = "CameraXImageLabelingScreen",
            sampleEntryScreen = { CameraXImageLabelingScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.ML_KIT, SampleTags.ANALYSIS),
        ),
        SampleCatalogItem(
            title = R.string.camera2_hdrviewfinder_list_title,
            description = R.string.camera2_hdrviewfinder_list_description,
            route = "Camera2HdrViewfinderScreen",
            sampleEntryScreen = { Camera2HdrViewfinderScreen() },
            type = SampleType.CAMERA2,
            tags = listOf(SampleTags.ANALYSIS),
        ),
        SampleCatalogItem(
            title = R.string.camerax_extensions_list_title,
            description = R.string.camerax_extensions_list_description,
            route = "CameraXExtensionsScreen",
            sampleEntryScreen = { CameraXExtensionsScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.EXTENSIONS),
        ),
        SampleCatalogItem(
            title = R.string.camera2_extensions_list_title,
            description = R.string.camera2_extensions_list_description,
            route = "Camera2ExtensionsScreen",
            sampleEntryScreen = { Camera2ExtensionsScreen() },
            type = SampleType.CAMERA2,
            tags = listOf(SampleTags.EXTENSIONS),
        ),
        SampleCatalogItem(
            title = R.string.camerax_zoomandtorch_list_title,
            description = R.string.camerax_zoomandtorch_list_description,
            route = "CameraXZoomAndTorchScreen",
            sampleEntryScreen = { CameraXZoomAndTorchScreen() },
            type = SampleType.CAMERAX,
        ),
        SampleCatalogItem(
            title = R.string.camera2_zoomandtorch_list_title,
            description = R.string.camera2_zoomandtorch_list_description,
            route = "Camera2ZoomAndTorchScreen",
            sampleEntryScreen = { Camera2ZoomAndTorchScreen() },
            type = SampleType.CAMERA2,
        ),
        SampleCatalogItem(
            title = R.string.camerax_exposure_list_title,
            description = R.string.camerax_exposure_list_description,
            route = "CameraXExposureScreen",
            sampleEntryScreen = { CameraXExposureScreen() },
            type = SampleType.CAMERAX,
        ),
        SampleCatalogItem(
            title = R.string.camera2_manualcontrols_list_title,
            description = R.string.camera2_manualcontrols_list_description,
            route = "Camera2ManualControlsScreen",
            sampleEntryScreen = { Camera2ManualControlsScreen() },
            type = SampleType.CAMERA2,
        ),
        SampleCatalogItem(
            title = R.string.camerax_luminosity_list_title,
            description = R.string.camerax_luminosity_list_description,
            route = "CameraXLuminosityScreen",
            sampleEntryScreen = { CameraXLuminosityScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.ANALYSIS),
        ),
        SampleCatalogItem(
            title = R.string.camera2_qrscanner_list_title,
            description = R.string.camera2_qrscanner_list_description,
            route = "Camera2QrScannerScreen",
            sampleEntryScreen = { Camera2QrScannerScreen() },
            type = SampleType.CAMERA2,
            tags = listOf(SampleTags.ML_KIT),
        ),
        SampleCatalogItem(
            title = R.string.camerax_ultrahdr_list_title,
            description = R.string.camerax_ultrahdr_list_description,
            route = "CameraXUltraHdrScreen",
            sampleEntryScreen = { CameraXUltraHdrScreen() },
            type = SampleType.CAMERAX,
        ),
        SampleCatalogItem(
            title = R.string.camerax_concurrentcamera_list_title,
            description = R.string.camerax_concurrentcamera_list_description,
            route = "CameraXConcurrentCameraScreen",
            sampleEntryScreen = { CameraXConcurrentCameraScreen() },
            type = SampleType.CAMERAX,
        ),
        SampleCatalogItem(
            title = R.string.camerax_videopauseresume_list_title,
            description = R.string.camerax_videopauseresume_list_description,
            route = "CameraXVideoPauseResumeScreen",
            sampleEntryScreen = { CameraXVideoPauseResumeScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.VIDEO),
        ),
        SampleCatalogItem(
            title = R.string.camera2_rawcapture_list_title,
            description = R.string.camera2_rawcapture_list_description,
            route = "Camera2RawCaptureScreen",
            sampleEntryScreen = { Camera2RawCaptureScreen() },
            type = SampleType.CAMERA2,
        ),
        SampleCatalogItem(
            title = R.string.camerax_effects_list_title,
            description = R.string.camerax_effects_list_description,
            route = "CameraXEffectsScreen",
            sampleEntryScreen = { CameraXEffectsScreen() },
            type = SampleType.CAMERAX,
        ),
        SampleCatalogItem(
            title = R.string.camerax_greenscreen_list_title,
            description = R.string.camerax_greenscreen_list_description,
            route = "CameraXGreenScreenScreen",
            sampleEntryScreen = { CameraXGreenScreenScreen() },
            type = SampleType.CAMERAX,
            tags = listOf(SampleTags.ML_KIT, SampleTags.ANALYSIS),
        ),
        // To create a new sample entry, add a new SampleCatalogItem here.
    )

data class SampleCatalogItem(
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val route: String,
    val sampleEntryScreen: @Composable () -> Unit,
    val type: SampleType,
    val tags: List<SampleTags> = emptyList(),
    val isFeatured: Boolean = false,
    @param:DrawableRes val keyArt: Int? = null,
)

enum class SampleTags(
    val label: String,
    val backgroundColor: Color,
) {
    MEDIA3("Media3", extendedColorScheme.media3),
    ML_KIT("ML Kit", extendedColorScheme.mLKit),
    VIDEO("Video", extendedColorScheme.media3),
    EXTENSIONS("Extensions", extendedColorScheme.imagen),
    ANALYSIS("Analysis", extendedColorScheme.geminiProFlash),
}

enum class SampleType {
    CAMERAX,
    CAMERA2,
}
