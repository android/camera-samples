/*
 * Copyright 2023 The Android Open Source Project
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
import android.graphics.ColorSpace
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.ColorSpaceProfiles
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
 * In this [Fragment] we let users pick a color space profile for the camera.
 */
class ColorSpaceFragment : Fragment() {

    private val args: ColorSpaceFragmentArgs by navArgs()

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

            val colorSpaceList = enumerateColorSpaces(cameraManager, args.cameraId,
                    args.dynamicRange)

            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(colorSpaceList, itemLayoutId = layoutId) {
                    view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.name
                view.setOnClickListener {
                    navigate(item.value, cameraManager)
                }
            }
        }
    }

    private fun navigate(colorSpace: ColorSpace.Named, cameraManager: CameraManager) {
        val navController =
            Navigation.findNavController(requireActivity(), R.id.fragment_container)

        if (supportsPreviewStabilization(cameraManager)) {
            navController.navigate(
                    ColorSpaceFragmentDirections.actionColorSpaceToPreviewStabilization(
                            args.cameraId, args.width, args.height, args.fps, args.dynamicRange,
                            colorSpace.ordinal))
        } else if (args.dynamicRange == DynamicRangeProfiles.STANDARD) {
            navController.navigate(
                    ColorSpaceFragmentDirections.actionColorSpaceToEncodeApi(
                            args.cameraId, args.width, args.height, args.fps, args.dynamicRange,
                            colorSpace.ordinal, false))
        }
    }

    private fun supportsPreviewStabilization(cameraManager: CameraManager) : Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(args.cameraId)
        val previewStabilizationModes = characteristics.get(
            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)!!
        return previewStabilizationModes.contains(
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
    }

    companion object {

        private data class ColorSpaceInfo(
                val name: String,
                val value: ColorSpace.Named)

        @SuppressLint("NewApi")
        private fun colorSpaceString(value: ColorSpace.Named) = when (value) {
            ColorSpace.Named.ACES -> "ACES"
            ColorSpace.Named.ACESCG -> "ACESCG"
            ColorSpace.Named.ADOBE_RGB -> "ADOBE_RGB"
            ColorSpace.Named.BT2020 -> "BT2020"
            ColorSpace.Named.BT2020_HLG -> "BT2020_HLG"
            ColorSpace.Named.BT2020_PQ -> "BT2020_PQ"
            ColorSpace.Named.BT709 -> "BT709"
            ColorSpace.Named.CIE_LAB -> "CIE_LAB"
            ColorSpace.Named.CIE_XYZ -> "CIE_XYZ"
            ColorSpace.Named.DCI_P3 -> "DCI_P3"
            ColorSpace.Named.DISPLAY_P3 -> "DISPLAY_P3"
            ColorSpace.Named.EXTENDED_SRGB -> "EXTENDED_SRGB"
            ColorSpace.Named.LINEAR_EXTENDED_SRGB -> "LINEAR_EXTENDED_SRGB"
            ColorSpace.Named.LINEAR_SRGB -> "LINEAR_SRGB"
            ColorSpace.Named.NTSC_1953 -> "NTSC_1953"
            ColorSpace.Named.PRO_PHOTO_RGB -> "PRO_PHOTO_RGB"
            ColorSpace.Named.SMPTE_C -> "SMPTE_C"
            ColorSpace.Named.SRGB -> "SRGB"
            else -> "UNKNOWN"
        }

        @SuppressLint("InlinedApi")
        private fun enumerateColorSpaces(cameraManager: CameraManager,
                                         cameraId: String,
                                         dynamicRange: Long): List<ColorSpaceInfo> {
            val colorSpaceList: MutableList<ColorSpaceInfo> = mutableListOf()

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val colorSpaceProfiles = characteristics.get(
                    CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES)!!
            colorSpaceProfiles.getSupportedColorSpacesForDynamicRange(ImageFormat.UNKNOWN,
                    dynamicRange).forEach { colorSpace ->
                val colorSpaceName = colorSpaceString(colorSpace)
                colorSpaceList.add(ColorSpaceInfo(colorSpaceName, colorSpace))
            }

            return colorSpaceList
        }
    }
}