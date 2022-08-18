/*
 * Copyright 2022 The Android Open Source Project
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
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.camera.utils.GenericListAdapter
import com.example.android.camera2.video.R

/**
 * In this [Fragment] we let users pick a dynamic range profile for the camera.
 */
class DynamicRangeFragment : Fragment() {

    private val args: DynamicRangeFragmentArgs by navArgs()

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

            val dynamicRangeList = enumerateDynamicRangeProfiles(cameraManager, args.cameraId)

            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(dynamicRangeList, itemLayoutId = layoutId) {
                    view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.name
                view.setOnClickListener {
                    navigate(item.value, cameraManager)
                }
            }
        }
    }

    private fun navigate(dynamicRangeProfile: Long, cameraManager: CameraManager) {
        val navController =
            Navigation.findNavController(requireActivity(), R.id.fragment_container)

        if (supportsPreviewStabilization(args.cameraId, cameraManager)) {
            navController.navigate(
                    DynamicRangeFragmentDirections.actionDynamicRangeToPreviewStabilization(
                            args.cameraId, args.width, args.height, args.fps, dynamicRangeProfile))
        } else if (dynamicRangeProfile == DynamicRangeProfiles.STANDARD) {
            navController.navigate(
                    DynamicRangeFragmentDirections.actionDynamicRangeToRecordMode(
                            args.cameraId, args.width, args.height, args.fps, dynamicRangeProfile,
                            false))
        } else {
            navController.navigate(DynamicRangeFragmentDirections.actionDynamicRangeToPreview(
                    args.cameraId, args.width, args.height, args.fps, dynamicRangeProfile, false,
                    false, false))
        }
    }

    private fun supportsPreviewStabilization(cameraId: String,
            cameraManager: CameraManager) : Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(args.cameraId)
        val previewStabilizationModes = characteristics.get(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)!!
        return previewStabilizationModes.contains(
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
    }

    companion object {

        private data class DynamicRangeInfo(
                val name: String,
                val value: Long)

        private fun dynamicRangeProfileString(value: Long) = when (value) {
            DynamicRangeProfiles.STANDARD -> "STANDARD"
            DynamicRangeProfiles.HLG10 -> "HLG10"
            DynamicRangeProfiles.HDR10 -> "HDR10"
            DynamicRangeProfiles.HDR10_PLUS -> "HDR10_PLUS"
            DynamicRangeProfiles.DOLBY_VISION_10B_HDR_REF -> "DOLBY_VISION_10B_HDR_REF"
            DynamicRangeProfiles.DOLBY_VISION_10B_HDR_REF_PO -> "DOLBY_VISION_10B_HDR_REF_PO"
            DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM -> "DOLBY_VISION_10B_HDR_OEM"
            DynamicRangeProfiles.DOLBY_VISION_10B_HDR_OEM_PO -> "DOLBY_VISION_10B_HDR_OEM_PO"
            DynamicRangeProfiles.DOLBY_VISION_8B_HDR_REF -> "DOLBY_VISION_8B_HDR_REF"
            DynamicRangeProfiles.DOLBY_VISION_8B_HDR_REF_PO -> "DOLBY_VISION_8B_HDR_REF_PO"
            DynamicRangeProfiles.DOLBY_VISION_8B_HDR_OEM -> "DOLBY_VISION_8B_HDR_OEM"
            DynamicRangeProfiles.DOLBY_VISION_8B_HDR_OEM_PO -> "DOLBY_VISION_8B_HDR_OEM_PO"
            else -> "UNKNOWN"
        }

        @SuppressLint("InlinedApi")
        private fun enumerateDynamicRangeProfiles(cameraManager: CameraManager,
                                                  cameraId: String): List<DynamicRangeInfo> {
            val dynamicRangeList: MutableList<DynamicRangeInfo> = mutableListOf()

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val dynamicRangeProfiles = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)!!
            dynamicRangeProfiles.getSupportedProfiles().forEach { profile ->
                val profileName = dynamicRangeProfileString(profile)
                dynamicRangeList.add(DynamicRangeInfo(profileName, profile))
            }

            return dynamicRangeList
        }
    }
}
