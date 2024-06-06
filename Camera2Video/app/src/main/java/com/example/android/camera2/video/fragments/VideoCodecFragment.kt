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
import android.hardware.camera2.params.DynamicRangeProfiles
import android.media.MediaCodecList
import android.media.MediaFormat
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
 * In this [Fragment] we let users pick a video codec to use for the encoded video.
 */
class VideoCodecFragment : Fragment() {

    private val args: VideoCodecFragmentArgs by navArgs()

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
            val videoCodecList = enumerateVideoCodecs(args.dynamicRange)

            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(videoCodecList, itemLayoutId = layoutId) { view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.name
                view.setOnClickListener {
                    val navController =
                        Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    val direction = VideoCodecFragmentDirections.actionVideoCodecToRecordMode(
                            args.cameraId, args.width, args.height, args.fps,
                            args.dynamicRange, args.colorSpace, args.previewStabilization,
                            args.useMediaRecorder, item.id)
                    navController.navigate(direction)
                }
            }
        }
    }

    companion object {

        private data class VideoCodecInfo(
                val name: String,
                val id: Int)

        public const val VIDEO_CODEC_ID_HEVC: Int = 0
        public const val VIDEO_CODEC_ID_H264: Int = 1
        public const val VIDEO_CODEC_ID_AV1: Int = 2

        public fun idToStr(videoCodecId: Int): String = when (videoCodecId) {
            VIDEO_CODEC_ID_HEVC -> "HEVC"
            VIDEO_CODEC_ID_H264 -> "H264"
            VIDEO_CODEC_ID_AV1 -> "AV1"
            else -> throw RuntimeException("Unexpected video codec id " + videoCodecId)
        }

        public fun idToType(videoCodecId: Int): String = when (videoCodecId) {
            VIDEO_CODEC_ID_H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
            VIDEO_CODEC_ID_HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
            VIDEO_CODEC_ID_AV1 -> MediaFormat.MIMETYPE_VIDEO_AV1
            else -> throw RuntimeException("Unexpected video codec id " + videoCodecId)
        }

        /** Lists all video-capable cameras and supported resolution and FPS combinations */
        @SuppressLint("InlinedApi")
        private fun enumerateVideoCodecs(dynamicRange: Long): List<VideoCodecInfo> {
            val videoCodecIdList = when {
                dynamicRange == DynamicRangeProfiles.STANDARD -> listOf(VIDEO_CODEC_ID_H264)
                dynamicRange < DynamicRangeProfiles.PUBLIC_MAX ->
                        listOf(VIDEO_CODEC_ID_HEVC, VIDEO_CODEC_ID_AV1)
                else -> throw RuntimeException("Unrecognized dynamic range $dynamicRange")
            }

            val supportedVideoCodecIdSet = mutableSetOf<Int>()
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (codecInfo in mediaCodecList.getCodecInfos()) {
                if (!codecInfo.isEncoder()) {
                    continue
                }

                val types = codecInfo.getSupportedTypes()
                for (type in types) {
                    for (videoCodecId in videoCodecIdList) {
                        if (type.equals(idToType(videoCodecId), ignoreCase = true)) {
                            supportedVideoCodecIdSet.add(videoCodecId)
                        }
                    }
                }
            }

            val videoCodecList: MutableList<VideoCodecInfo> = mutableListOf()
            for (id in supportedVideoCodecIdSet) {
                videoCodecList.add(VideoCodecInfo(idToStr(id), id))
            }
            return videoCodecList
        }
    }
}
