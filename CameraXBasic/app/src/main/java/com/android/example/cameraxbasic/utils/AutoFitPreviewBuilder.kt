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

package com.android.example.cameraxbasic.utils

import android.content.Context
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import java.lang.IllegalArgumentException
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builder for [Preview] that takes in a [WeakReference] of the view finder and [PreviewConfig],
 * then instantiates a [Preview] which automatically resizes and rotates reacting to config changes.
 */
class AutoFitPreviewBuilder private constructor(
        config: PreviewConfig, viewFinderRef: WeakReference<TextureView>) {

    /** Public instance of preview use-case which can be used by consumers of this adapter */
    val useCase: Preview

    /** Internal variable used to keep track of the use case's output rotation */
    private var bufferRotation: Int = 0

    /** Internal variable used to keep track of the view's rotation */
    private var viewFinderRotation: Int? = null

    /** Internal variable used to keep track of the use-case's output dimension */
    private var bufferDimens: Size = Size(0, 0)

    /** Internal variable used to keep track of the view's dimension */
    private var viewFinderDimens: Size = Size(0, 0)

    /** Internal variable used to keep track of the view's display */
    private var viewFinderDisplay: Int = -1

    /** Internal reference of the [DisplayManager] */
    private lateinit var displayManager: DisplayManager

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            val viewFinder = viewFinderRef.get() ?: return
            if (displayId == viewFinderDisplay) {
                val display = displayManager.getDisplay(displayId)
                val rotation = getDisplaySurfaceRotation(display)
                updateTransform(viewFinder, rotation, bufferDimens, viewFinderDimens)
            }
        }
    }

    init {
        // Make sure that the view finder reference is valid
        val viewFinder = viewFinderRef.get() ?:
            throw IllegalArgumentException("Invalid reference to view finder used")

        // Initialize the display and rotation from texture view information
        viewFinderDisplay = viewFinder.display.displayId
        viewFinderRotation = getDisplaySurfaceRotation(viewFinder.display) ?: 0

        // Initialize public use-case with the given config
        useCase = Preview(config)

        // Every time the view finder is updated, recompute layout
        useCase.setOnPreviewOutputUpdateListener(Preview.OnPreviewOutputUpdateListener {
            val viewFinder =
                    viewFinderRef.get() ?: return@OnPreviewOutputUpdateListener
            Log.d(TAG, "Preview output changed. " +
                    "Size: ${it.textureSize}. Rotation: ${it.rotationDegrees}")

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            // Update internal texture
            viewFinder.surfaceTexture = it.surfaceTexture

            // Apply relevant transformations
            bufferRotation = it.rotationDegrees
            val rotation = getDisplaySurfaceRotation(viewFinder.display)
            updateTransform(viewFinder, rotation, it.textureSize, viewFinderDimens)
        })

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val viewFinder = view as TextureView
            val newViewFinderDimens = Size(right - left, bottom - top)
            Log.d(TAG, "View finder layout changed. Size: $newViewFinderDimens")
            val rotation = getDisplaySurfaceRotation(viewFinder.display)
            updateTransform(viewFinder, rotation, bufferDimens, newViewFinderDimens)
        }

        // Every time the orientation of device changes, recompute layout
        // NOTE: This is unnecessary if we listen to display orientation changes in the camera
        //  fragment and call [Preview.setTargetRotation()] (like we do in this sample), which will
        //  trigger [Preview.OnPreviewOutputUpdateListener] with a new
        //  [PreviewOutput.rotationDegrees]. CameraX Preview use case will not rotate the frames for
        //  us, it will just tell us about the buffer rotation with respect to sensor orientation.
        //  In this sample, we ignore the buffer rotation and instead look at the view finder's
        //  rotation every time [updateTransform] is called, which gets triggered by
        //  [CameraFragment] display listener -- but the approach taken in this sample is not the
        //  only valid one.
        displayManager = viewFinder.context
                .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Remove the display listeners when the view is detached to avoid holding a reference to
        //  it outside of the Fragment that owns the view.
        // NOTE: Even though using a weak reference should take care of this, we still try to avoid
        //  unnecessary calls to the listener this way.
        viewFinder.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View?) =
                    displayManager.registerDisplayListener(displayListener, null)
            override fun onViewDetachedFromWindow(view: View?) =
                    displayManager.unregisterDisplayListener(displayListener)
        })
    }

    /** Helper function that fits a camera preview into the given [TextureView] */
    private fun updateTransform(textureView: TextureView?, rotation: Int?, newBufferDimens: Size,
                                newViewFinderDimens: Size) {
        // This should not happen anyway, but now the linter knows
        textureView ?: return

        if (rotation == viewFinderRotation &&
                newBufferDimens == bufferDimens &&
                newViewFinderDimens == viewFinderDimens) {
            // Nothing has changed, no need to transform output again
            return
        }

        if (rotation == null) {
            // Invalid rotation - wait for valid inputs before setting matrix
            return
        } else {
            // Update internal field with new inputs
            viewFinderRotation = rotation
        }

        if (newBufferDimens.width == 0 || newBufferDimens.height == 0) {
            // Invalid buffer dimens - wait for valid inputs before setting matrix
            return
        } else {
            // Update internal field with new inputs
            bufferDimens = newBufferDimens
        }

        if (newViewFinderDimens.width == 0 || newViewFinderDimens.height == 0) {
            // Invalid view finder dimens - wait for valid inputs before setting matrix
            return
        } else {
            // Update internal field with new inputs
            viewFinderDimens = newViewFinderDimens
        }

        val matrix = Matrix()
        Log.d(TAG, "Applying output transformation.\n" +
                "View finder size: $viewFinderDimens.\n" +
                "Preview output size: $bufferDimens\n" +
                "View finder rotation: $viewFinderRotation\n" +
                "Preview output rotation: $bufferRotation")

        // Compute the center of the view finder
        val centerX = viewFinderDimens.width / 2f
        val centerY = viewFinderDimens.height / 2f

        // Correct preview output to account for display rotation
        matrix.postRotate(-viewFinderRotation!!.toFloat(), centerX, centerY)

        // Buffers are rotated relative to the device's 'natural' orientation.
        val isNaturalPortrait = ((viewFinderRotation == 0 || viewFinderRotation == 180) &&
                viewFinderDimens.width < viewFinderDimens.height)
          || ((viewFinderRotation == 90 || viewFinderRotation == 270) &&
                viewFinderDimens.width >= viewFinderDimens.height)
        val bufferWidth: Int
        val bufferHeight: Int
        if (isNaturalPortrait) {
            bufferWidth = bufferDimens.height
            bufferHeight = bufferDimens.width
        } else {
            bufferWidth = bufferDimens.width
            bufferHeight = bufferDimens.height
        }
        // Scale back the buffers back to the original output buffer dimensions.
        var xScale = bufferWidth / viewFinderDimens.width.toFloat()
        var yScale = bufferHeight / viewFinderDimens.height.toFloat()

        val bufferRotatedWidth: Int
        val bufferRotatedHeight: Int
        if (viewFinderRotation == 0 || viewFinderRotation == 180) {
            bufferRotatedWidth = bufferWidth
            bufferRotatedHeight = bufferHeight
        } else {
            bufferRotatedWidth = bufferHeight
            bufferRotatedHeight = bufferWidth
        }

        // Scale the buffer so that it just covers the viewfinder.
        val scale = max(viewFinderDimens.width / bufferRotatedWidth.toFloat(),
                viewFinderDimens.height / bufferRotatedHeight.toFloat())
        xScale *= scale
        yScale *= scale

        // Scale input buffers to fill the view finder
        matrix.preScale(xScale, yScale, centerX, centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
    }

    companion object {
        private val TAG = AutoFitPreviewBuilder::class.java.simpleName

        /** Helper function that gets the rotation of a [Display] in degrees */
        fun getDisplaySurfaceRotation(display: Display?) = when(display?.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> null
        }

        /**
         * Main entrypoint for users of this class: instantiates the adapter and returns an instance
         * of [Preview] which automatically adjusts in size and rotation to compensate for
         * config changes.
         */
        fun build(config: PreviewConfig, viewFinder: TextureView) =
                AutoFitPreviewBuilder(config, WeakReference(viewFinder)).useCase
    }
}