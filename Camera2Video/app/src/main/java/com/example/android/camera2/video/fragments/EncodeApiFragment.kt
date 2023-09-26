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
 * In this [Fragment] we let users pick the encoding API to use (MediaCodec or MediaRecorder)
 */
class EncodeApiFragment : Fragment() {

    private val args: EncodeApiFragmentArgs by navArgs()

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
            val modeList = enumerateModes()
            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(modeList, itemLayoutId = layoutId) { view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.name
                view.setOnClickListener {
                    navigate(item.value)
                }
            }
        }
    }

    private fun navigate(useMediaRecorder: Boolean) {
        val navController =
                Navigation.findNavController(requireActivity(), R.id.fragment_container)

        navController.navigate(
                EncodeApiFragmentDirections.actionEncodeApiToRecordMode(
                args.cameraId, args.width, args.height, args.fps,
                args.dynamicRange, args.colorSpace, args.previewStabilization, useMediaRecorder))
    }

    companion object {
        private data class ApiInfo(
                val name: String,
                val value: Boolean)

        @SuppressLint("InlinedApi")
        private fun enumerateModes(): List<ApiInfo> {
            val modeList: MutableList<ApiInfo> = mutableListOf()

            modeList.add(ApiInfo("MediaCodec", false))
            modeList.add(ApiInfo("MediaRecorder", true))

            return modeList
        }
    }
}
