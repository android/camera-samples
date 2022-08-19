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

package com.example.android.cameraxextensions.ui

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.RecyclerView.SmoothScroller
import androidx.recyclerview.widget.RecyclerView.SmoothScroller.ScrollVectorProvider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Snaps the item to the center of the RecyclerView. Note that this SnapHelper ignores any
 * decorations applied to the child view. This is required since the first and last item are
 * centered by applying padding to the start or end of the view via an Item Decoration
 * @see OffsetCenterItemDecoration
 */
class CenterItemSnapHelper : LinearSnapHelper() {

    companion object {
        private const val MILLISECONDS_PER_INCH = 100f
        private const val MAX_SCROLL_ON_FLING_DURATION_MS = 1000
    }

    private var context: Context? = null
    private var recyclerView: RecyclerView? = null
    private var scroller: Scroller? = null
    private var maxScrollDistance = 0

    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (recyclerView != null) {
            context = recyclerView.context
            this.recyclerView = recyclerView
            scroller = Scroller(context, DecelerateInterpolator())
        } else {
            context = null
            this.recyclerView = null
            scroller = null
        }
        super.attachToRecyclerView(recyclerView)
    }

    override fun findSnapView(layoutManager: LayoutManager?): View? =
        findMiddleView(layoutManager)

    override fun createScroller(layoutManager: LayoutManager): SmoothScroller? {
        if (layoutManager !is ScrollVectorProvider)
            return super.createScroller(layoutManager)
        val context = context ?: return null
        return object : LinearSmoothScroller(context) {
            override fun onTargetFound(
                targetView: View,
                state: RecyclerView.State,
                action: Action
            ) {
                val snapDistance = calculateDistanceToFinalSnap(layoutManager, targetView)
                val dx = snapDistance[0]
                val dy = snapDistance[1]
                val dt = calculateTimeForDeceleration(Math.abs(dx))
                val time = max(1, min(MAX_SCROLL_ON_FLING_DURATION_MS, dt))
                action.update(dx, dy, time, mDecelerateInterpolator)
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float =
                MILLISECONDS_PER_INCH / displayMetrics.densityDpi
        }
    }

    override fun calculateDistanceToFinalSnap(
        layoutManager: LayoutManager,
        targetView: View
    ): IntArray {
        val out = IntArray(2)
        out[0] = distanceToMiddleView(layoutManager, targetView)
        return out
    }

    override fun calculateScrollDistance(velocityX: Int, velocityY: Int): IntArray {
        val out = IntArray(2)
        val layoutManager: LinearLayoutManager =
            (recyclerView?.layoutManager as? LinearLayoutManager) ?: return out

        if (maxScrollDistance == 0) {
            maxScrollDistance = (layoutManager.width) / 2
        }

        scroller?.fling(0, 0, velocityX, velocityY, -maxScrollDistance, maxScrollDistance, 0, 0)
        out[0] = scroller?.finalX ?: 0
        out[1] = scroller?.finalY ?: 0
        return out
    }

    private fun distanceToMiddleView(layoutManager: LayoutManager, targetView: View): Int {
        val middle = layoutManager.width / 2
        val targetMiddle = targetView.left + targetView.width / 2
        return targetMiddle - middle
    }

    private fun findMiddleView(layoutManager: LayoutManager?): View? {
        if (layoutManager == null) return null

        val childCount = layoutManager.childCount
        if (childCount == 0) return null

        var absClosest = Integer.MAX_VALUE
        var closestView: View? = null
        val middle = layoutManager.width / 2

        for (i in 0 until childCount) {
            val child = layoutManager.getChildAt(i) ?: continue
            val absDistanceToMiddle = abs((child.left + child.width / 2) - middle)
            if (absDistanceToMiddle < absClosest) {
                absClosest = absDistanceToMiddle
                closestView = child
            }
        }
        return closestView
    }
}