/*
 * Copyright 2020 The Android Open Source Project
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

package com.example.android.camera2.video.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.DynamicRangeProfiles
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.camera.utils.GenericListAdapter
import com.example.android.camera2.video.R

/**
 * In this [Fragment] we let users pick a camera, size and FPS to use for high
 * speed video recording
 */
class SelectorFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = RecyclerView(requireContext())

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view as RecyclerView
        view.apply {
            layoutManager = LinearLayoutManager(requireContext())

            val cameraManager =
                    requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val cameraList = enumerateVideoCameras(cameraManager)

            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(cameraList, itemLayoutId = layoutId) { view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.name
                view.setOnClickListener {
                    var dynamicRangeProfiles: DynamicRangeProfiles? = null
                    var supportsPreviewStabilization = false

                    // DynamicRangeProfiles is introduced in android Tiramisu. If the SDK residing on
                    // our device is older, do not call the non-existant paths.
                    if (android.os.Build.VERSION.SDK_INT >=
                            android.os.Build.VERSION_CODES.TIRAMISU) {
                        val characteristics = cameraManager.getCameraCharacteristics(item.cameraId)
                        dynamicRangeProfiles = characteristics.get(
                                CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
                        val previewStabilizationModes = characteristics.get(
                                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)!!
                        supportsPreviewStabilization = previewStabilizationModes.contains(
                                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
                    }

                    val navController =
                        Navigation.findNavController(requireActivity(), R.id.fragment_container)

                    // If possible, navigate to a second selector for picking a dynamic range.
                    // Otherwise continue on to video recording.
                    if (dynamicRangeProfiles != null) {
                        navController.navigate(
                            SelectorFragmentDirections.actionSelectorToDynamicRange(
                            item.cameraId, item.size.width, item.size.height, item.fps))
                    } else if (supportsPreviewStabilization) {
                        navController.navigate(
                            SelectorFragmentDirections.actionSelectorToPreviewStabilization(
                            item.cameraId, item.size.width, item.size.height, item.fps,
                            DynamicRangeProfiles.STANDARD)
                        )
                    } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                        navController.navigate(
                            SelectorFragmentDirections.actionSelectorToRecordMode(
                            item.cameraId, item.size.width, item.size.height, item.fps,
                            DynamicRangeProfiles.STANDARD, /*previewStabilization*/ false))
                    } else {
                        navController.navigate(
                            SelectorFragmentDirections.actionSelectorToPreview(
                            item.cameraId, item.size.width, item.size.height, item.fps,
                            DynamicRangeProfiles.STANDARD, /*previewStabilization*/ false,
                            false, false))
                    }
                }
            }
        }
    }

    companion object {

        private data class CameraInfo(
                val name: String,
                val cameraId: String,
                val size: Size,
                val fps: Int)

        /** Converts a lens orientation enum into a human-readable string */
        private fun lensOrientationString(value: Int) = when (value) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        /** Lists all video-capable cameras and supported resolution and FPS combinations */
        @SuppressLint("InlinedApi")
        private fun enumerateVideoCameras(cameraManager: CameraManager): List<CameraInfo> {
            val availableCameras: MutableList<CameraInfo> = mutableListOf()

            // Iterate over the list of cameras and add those with high speed video recording
            //  capability to our output. This function only returns those cameras that declare
            //  constrained high speed video recording, but some cameras may be capable of doing
            //  unconstrained video recording with high enough FPS for some use cases and they will
            //  not necessarily declare constrained high speed video capability.
            cameraManager.cameraIdList.forEach { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val orientation = lensOrientationString(
                        characteristics.get(CameraCharacteristics.LENS_FACING)!!)

                // Query the available capabilities and output formats
                val capabilities = characteristics.get(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
                val cameraConfig = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

                // Return cameras that declare to be backward compatible
                if (capabilities.contains(CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                    // Recording should always be done in the most efficient format, which is
                    //  the format native to the camera framework
                    val targetClass = MediaRecorder::class.java

                    // For each size, list the expected FPS
                    cameraConfig.getOutputSizes(targetClass).forEach { size ->
                        // Get the number of seconds that each frame will take to process
                        val secondsPerFrame =
                                cameraConfig.getOutputMinFrameDuration(targetClass, size) /
                                        1_000_000_000.0
                        // Compute the frames per second to let user select a configuration
                        val fps = if (secondsPerFrame > 0) (1.0 / secondsPerFrame).toInt() else 0
                        val fpsLabel = if (fps > 0) "$fps" else "N/A"
                        availableCameras.add(CameraInfo(
                                "$orientation ($id) $size $fpsLabel FPS", id, size, fps))
                    }
                }
            }

            return availableCameras
        }
    }
}
