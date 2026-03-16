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
package com.android.camera2.takeavideo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class CameraVideoConfig(
    val cameraId: String = "",
    val size: Size = Size(1920, 1080),
    val fps: Int = 30,
    val dynamicRange: Long = 1L, // DynamicRangeProfiles.STANDARD
    val colorSpace: Int = -1, // ColorSpaceProfiles.UNSPECIFIED
    val previewStabilization: Boolean = false,
    val useMediaRecorder: Boolean = true,
    val videoCodec: Int = 1, // H264
    val useHardware: Boolean = true,
    val filterOn: Boolean = false,
    val transfer: Int = 0
)

@SuppressLint("NewApi")
@Composable
fun Camera2TakeAVideoSetup(
    onConfigurationComplete: (CameraVideoConfig) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(SetupStep.SELECTOR) }
    var config by remember { mutableStateOf(CameraVideoConfig()) }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Configure Video Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (currentStep) {
                SetupStep.SELECTOR -> {
                    SelectorComposable(context) { cameraId, size, fps ->
                        config = config.copy(cameraId = cameraId, size = size, fps = fps)
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                        var hasDynamicRange = false
                        var hasColorSpace = false
                        var hasPreviewStabilization = false

                        if (Build.VERSION.SDK_INT >= 33) {
                            val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                            if (caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT) == true) {
                                hasDynamicRange = true
                            }
                            val stabModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                            if (stabModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true) {
                                hasPreviewStabilization = true
                            }
                        }

                        if (Build.VERSION.SDK_INT >= 34) {
                            val colorSpaces = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)
                            if (colorSpaces != null) hasColorSpace = true
                        }

                        currentStep = when {
                            hasDynamicRange -> SetupStep.DYNAMIC_RANGE
                            hasColorSpace -> SetupStep.COLOR_SPACE
                            hasPreviewStabilization -> SetupStep.PREVIEW_STABILIZATION
                            else -> SetupStep.ENCODE_API
                        }
                    }
                }
                SetupStep.DYNAMIC_RANGE -> {
                    DynamicRangeComposable(context, config.cameraId) { dynamicRange ->
                        config = config.copy(dynamicRange = dynamicRange)
                        
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val characteristics = cameraManager.getCameraCharacteristics(config.cameraId)
                        
                        var hasColorSpace = false
                        var hasPreviewStabilization = false

                        if (Build.VERSION.SDK_INT >= 34) {
                            val colorSpaces = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)
                            if (colorSpaces != null) hasColorSpace = true
                        }
                        
                        if (Build.VERSION.SDK_INT >= 33) {
                            val stabModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                            if (stabModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true) {
                                hasPreviewStabilization = true
                            }
                        }

                        currentStep = when {
                            hasColorSpace -> SetupStep.COLOR_SPACE
                            hasPreviewStabilization -> SetupStep.PREVIEW_STABILIZATION
                            else -> SetupStep.ENCODE_API
                        }
                    }
                }
                SetupStep.COLOR_SPACE -> {
                    ColorSpaceComposable(context, config.cameraId, config.dynamicRange) { colorSpace ->
                        config = config.copy(colorSpace = colorSpace)
                        
                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                        val characteristics = cameraManager.getCameraCharacteristics(config.cameraId)
                        
                        var hasPreviewStabilization = false
                        if (Build.VERSION.SDK_INT >= 33) {
                            val stabModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                            if (stabModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true) {
                                hasPreviewStabilization = true
                            }
                        }

                        currentStep = if (hasPreviewStabilization) SetupStep.PREVIEW_STABILIZATION else SetupStep.ENCODE_API
                    }
                }
                SetupStep.PREVIEW_STABILIZATION -> {
                    PreviewStabilizationComposable { previewStabilization ->
                        config = config.copy(previewStabilization = previewStabilization)
                        currentStep = SetupStep.ENCODE_API
                    }
                }
                SetupStep.ENCODE_API -> {
                    EncodeApiComposable(config.dynamicRange) { useMediaRecorder ->
                        config = config.copy(useMediaRecorder = useMediaRecorder)
                        currentStep = SetupStep.VIDEO_CODEC
                    }
                }
                SetupStep.VIDEO_CODEC -> {
                    VideoCodecComposable(config.dynamicRange) { videoCodec ->
                        config = config.copy(videoCodec = videoCodec)
                        currentStep = SetupStep.RECORD_MODE
                    }
                }
                SetupStep.RECORD_MODE -> {
                    RecordModeComposable { useHardware ->
                        config = config.copy(useHardware = useHardware)
                        currentStep = if (useHardware) {
                            SetupStep.DONE
                        } else {
                            SetupStep.FILTER
                        }
                    }
                }
                SetupStep.FILTER -> {
                    FilterComposable { filterOn ->
                        config = config.copy(filterOn = filterOn)
                        currentStep = if (config.dynamicRange == 1L) {
                            SetupStep.DONE
                        } else {
                            SetupStep.TRANSFER
                        }
                    }
                }
                SetupStep.TRANSFER -> {
                    TransferComposable { transfer ->
                        config = config.copy(transfer = transfer)
                        currentStep = SetupStep.DONE
                    }
                }
                SetupStep.DONE -> {
                    LaunchedEffect(Unit) {
                        onConfigurationComplete(config)
                    }
                }
            }
        }
    }
}

enum class SetupStep {
    SELECTOR, DYNAMIC_RANGE, COLOR_SPACE, PREVIEW_STABILIZATION, ENCODE_API,
    VIDEO_CODEC, RECORD_MODE, FILTER, TRANSFER, DONE
}

@Composable
private fun SimpleListSelector(
    title: String,
    items: List<Pair<String, () -> Unit>>
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
        LazyColumn {
            items(items) { (label, onClick) ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, modifier = Modifier.weight(1f))
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Select")
                    }
                }
            }
        }
    }
}

@SuppressLint("InlinedApi", "NewApi")
@Composable
fun SelectorComposable(context: Context, onSelect: (String, Size, Int) -> Unit) {
    var cameraItems by remember { mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val items = mutableListOf<Pair<String, () -> Unit>>()
        
        cameraManager.cameraIdList.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val orientation = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }

            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            if (capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true) {
                val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                config?.getOutputSizes(MediaRecorder::class.java)?.forEach { size ->
                    val secondsPerFrame = config.getOutputMinFrameDuration(MediaRecorder::class.java, size) / 1_000_000_000.0
                    val fps = if (secondsPerFrame > 0) (1.0 / secondsPerFrame).toInt() else 0
                    val fpsLabel = if (fps > 0) "$fps" else "N/A"
                    val label = "$orientation ($id) $size $fpsLabel FPS"
                    
                    items.add(label to { onSelect(id, size, fps) })
                }
            }
        }
        cameraItems = items
    }

    SimpleListSelector("Select Camera & Resolution", cameraItems)
}

@SuppressLint("NewApi")
@Composable
fun DynamicRangeComposable(context: Context, cameraId: String, onSelect: (Long) -> Unit) {
    var dynamicRangeItems by remember { mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val items = mutableListOf<Pair<String, () -> Unit>>()
        
        if (Build.VERSION.SDK_INT >= 33) {
            val profiles = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
            profiles?.supportedProfiles?.forEach { profile ->
                val name = when (profile) {
                    1L -> "SDR"
                    2L -> "HLG10"
                    4L -> "HDR10"
                    8L -> "HDR10+"
                    16L -> "Dolby Vision OEM"
                    32L -> "Dolby Vision OEM PO"
                    64L -> "Dolby Vision Ref"
                    128L -> "Dolby Vision Ref PO"
                    256L -> "Dolby Vision 8B OEM"
                    512L -> "Dolby Vision 8B OEM PO"
                    1024L -> "Dolby Vision 8B Ref"
                    2048L -> "Dolby Vision 8B Ref PO"
                    else -> "Unknown"
                }
                items.add(name to { onSelect(profile) })
            }
        } else {
            items.add("SDR" to { onSelect(1L) })
        }
        if (items.isEmpty()) {
            items.add("SDR" to { onSelect(1L) })
        }
        dynamicRangeItems = items
    }

    SimpleListSelector("Select Dynamic Range", dynamicRangeItems)
}

@SuppressLint("NewApi")
@Composable
fun ColorSpaceComposable(context: Context, cameraId: String, dynamicRange: Long, onSelect: (Int) -> Unit) {
    var colorSpaceItems by remember { mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val items = mutableListOf<Pair<String, () -> Unit>>()

        if (Build.VERSION.SDK_INT >= 34) {
            val profiles = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)
            val supportedProfiles = profiles?.getSupportedColorSpacesForDynamicRange(ImageFormat.UNKNOWN, dynamicRange)
            
            supportedProfiles?.forEach { colorSpaceNamed ->
                items.add(colorSpaceNamed.name to { onSelect(colorSpaceNamed.ordinal) })
            }
        }
        
        if (items.isEmpty()) {
            items.add("UNSPECIFIED" to { onSelect(-1) })
        }
        colorSpaceItems = items
    }

    SimpleListSelector("Select Color Space", colorSpaceItems)
}

@Composable
fun PreviewStabilizationComposable(onSelect: (Boolean) -> Unit) {
    SimpleListSelector("Enable Preview Stabilization?", listOf(
        "Preview Stabilization On" to { onSelect(true) },
        "Preview Stabilization Off" to { onSelect(false) }
    ))
}

@Composable
fun EncodeApiComposable(dynamicRange: Long, onSelect: (Boolean) -> Unit) {
    val items = mutableListOf<Pair<String, () -> Unit>>()
    items.add("MediaCodec" to { onSelect(false) })
    
    if (dynamicRange == 1L) {
        items.add("MediaRecorder" to { onSelect(true) })
    }
    
    SimpleListSelector("Select Encode API", items)
}

@Composable
fun VideoCodecComposable(dynamicRange: Long, onSelect: (Int) -> Unit) {
    var items by remember { mutableStateOf<List<Pair<String, () -> Unit>>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val videoCodecIdList = when {
            dynamicRange == 1L -> listOf(1) // H264
            dynamicRange < 4096L -> listOf(0, 2) // HEVC, AV1 (PUBLIC_MAX is 4096)
            else -> listOf(1)
        }

        val supportedVideoCodecIdSet = mutableSetOf<Int>()
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in mediaCodecList.codecInfos) {
            if (!codecInfo.isEncoder) continue
            val types = codecInfo.supportedTypes
            for (type in types) {
                for (id in videoCodecIdList) {
                    val mimeType = when (id) {
                        0 -> MediaFormat.MIMETYPE_VIDEO_HEVC
                        1 -> MediaFormat.MIMETYPE_VIDEO_AVC
                        2 -> "video/av01" // MediaFormat.MIMETYPE_VIDEO_AV1 value
                        else -> ""
                    }
                    if (type.equals(mimeType, ignoreCase = true)) {
                        supportedVideoCodecIdSet.add(id)
                    }
                }
            }
        }

        val finalItems = mutableListOf<Pair<String, () -> Unit>>()
        for (id in supportedVideoCodecIdSet) {
            val name = when (id) {
                0 -> "HEVC"
                1 -> "H264"
                2 -> "AV1"
                else -> "Unknown"
            }
            finalItems.add(name to { onSelect(id) })
        }
        if (finalItems.isEmpty()) {
             finalItems.add("H264" to { onSelect(1) })
        }
        items = finalItems
    }
    
    SimpleListSelector("Select Video Codec", items)
}

@Composable
fun RecordModeComposable(onSelect: (Boolean) -> Unit) {
    SimpleListSelector("Select Record Mode", listOf(
        "Multi-stream (Hardware)" to { onSelect(true) },
        "Single-stream (Software)" to { onSelect(false) }
    ))
}

@Composable
fun FilterComposable(onSelect: (Boolean) -> Unit) {
    SimpleListSelector("Enable Portrait Filter?", listOf(
        "Portrait Filter On" to { onSelect(true) },
        "Portrait Filter Off" to { onSelect(false) }
    ))
}

@Composable
fun TransferComposable(onSelect: (Int) -> Unit) {
    SimpleListSelector("Select Color Transfer Characteristics", listOf(
        "SDR" to { onSelect(0) },
        "HLG" to { onSelect(1) },
        "PQ" to { onSelect(2) }
    ))
}
