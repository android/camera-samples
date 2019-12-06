/*
 * Copyright 2019 Google LLC
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

package com.example.android.camera2.slowmo.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import com.example.android.camera2.common.GenericListAdapter
import com.example.android.camera2.slowmo.R

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

            val cameraList = enumerateHighSpeedCameras(cameraManager)

            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(cameraList, itemLayoutId = layoutId) { view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.title
                view.setOnClickListener {
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                            .navigate(SelectorFragmentDirections.actionSelectorToCamera(
                                    item.cameraId, item.size.width, item.size.height, item.fps))
                }
            }
        }

    }

    companion object {

        private data class CameraInfo(
                val title: String,
                val cameraId: String,
                val size: Size,
                val fps: Int)

        /** Converts a lens orientation enum into a human-readable string */
        private fun lensOrientationString(value: Int) = when(value) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        /** Lists all high speed cameras and supported resolution and FPS combinations */
        @SuppressLint("InlinedApi")
        private fun enumerateHighSpeedCameras(cameraManager: CameraManager): List<CameraInfo> {
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

                // Return cameras that support constrained high video capability
                if (capabilities.contains(CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
                    // For each camera, list its compatible sizes and FPS ranges
                    cameraConfig.highSpeedVideoSizes.forEach { size ->
                        cameraConfig.getHighSpeedVideoFpsRangesFor(size).forEach { fpsRange ->
                            val fps = fpsRange.upper
                            val info = CameraInfo(
                                    "$orientation ($id) $size $fps FPS", id, size, fps)

                            // Only report the highest FPS in the range, avoid duplicates
                            if (!availableCameras.contains(info)) availableCameras.add(info)
                        }
                    }
                }

            }

            return availableCameras
        }
    }
}
