/*
 * Copyright 2021 The Android Open Source Project
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

// Simple VideoView to display the just captured video

package com.example.android.camerax.video.fragments

import android.content.ContentResolver
import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.navigation.fragment.navArgs
import com.example.android.camerax.video.databinding.FragmentVideoViewerBinding
import android.util.TypedValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.example.android.camerax.video.R
import kotlinx.coroutines.launch
import java.lang.RuntimeException

/**
 * VideoViewerFragment:
 *      Accept MediaStore URI and play it with VideoView (Also displaying file size and location)
 *      Note: Might be good to retrieve the encoded file mime type (not based on file type)
 */
class VideoViewerFragment : androidx.fragment.app.Fragment() {
    private val args: VideoViewerFragmentArgs by navArgs()

    // This property is only valid between onCreateView and onDestroyView.
    private var _binding: FragmentVideoViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoViewerBinding.inflate(inflater, container, false)
        // UI adjustment + hacking to display VideoView use tips / capture result
        val tv = TypedValue()
        if (requireActivity().theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            val actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
            binding.videoViewerTips.y  = binding.videoViewerTips.y - actionBarHeight
        }

        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            showVideo(args.uri)
        } else {
            // force MediaScanner to re-scan the media file.
            val path = getAbsolutePathFromUri(args.uri) ?: return
            MediaScannerConnection.scanFile(
                context, arrayOf(path), null
            ) { _, uri ->
                // playback video on main thread with VideoView
                if (uri != null) {
                    lifecycleScope.launch {
                        showVideo(uri)
                    }
                }
            }
        }

        // Handle back button press
        binding.backButton.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    /**
     * A helper function to play the recorded video. Note that VideoView/MediaController auto-hides
     * the play control menus, touch on the video area would bring it back for 3 second.
     * This functionality not really related to capture, provided here for convenient purpose to view:
     *   - the captured video
     *   - the file size and location
     */
    private fun showVideo(uri : Uri) {
        val fileSize = getFileSizeFromUri(uri)
        if (fileSize == null || fileSize <= 0) {
            Log.e("VideoViewerFragment", "Failed to get recorded file size, could not be played!")
            return
        }

        val filePath = getAbsolutePathFromUri(uri) ?: return
        val fileInfo = "FileSize: $fileSize\n $filePath"
        Log.i("VideoViewerFragment", fileInfo)
        binding.videoViewerTips.text = fileInfo

        val mc = MediaController(requireContext())
        binding.videoViewer.apply {
            setVideoURI(uri)
            setMediaController(mc)
            requestFocus()
        }.start()
        mc.show(0)
    }

    /**
     * A helper function to get the captured file location.
     */
    private fun getAbsolutePathFromUri(contentUri: Uri): String? {
        var cursor:Cursor? = null
        return try {
            cursor = requireContext()
                .contentResolver
                .query(contentUri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
            if (cursor == null) {
                return null
            }
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(columnIndex)
        } catch (e: RuntimeException) {
            Log.e("VideoViewerFragment", String.format(
                "Failed in getting absolute path for Uri %s with Exception %s",
                contentUri.toString(), e.toString()
            )
            )
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * A helper function to retrieve the captured file size.
     */
    private fun getFileSizeFromUri(contentUri: Uri): Long? {
        val cursor = requireContext()
            .contentResolver
            .query(contentUri, null, null, null, null)
            ?: return null

        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        cursor.moveToFirst()

        cursor.use {
            return it.getLong(sizeIndex)
        }
    }
}
