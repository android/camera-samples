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

import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * An ItemDecoration used to center the first and last items within the RecyclerView.
 */
class OffsetCenterItemDecoration : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val layoutManager = parent.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.getPosition(view)
        if (position == 0 || position == layoutManager.itemCount - 1) {
            measureChild(parent, view)
            val width = view.measuredWidth
            when (position) {
                0 -> {
                    outRect.left = (parent.width - width) / 2
                    outRect.right = 0
                }
                layoutManager.itemCount - 1 -> {
                    outRect.left = 0
                    outRect.right = (parent.width - width) / 2
                }
                else -> {
                    outRect.left = 0
                    outRect.right = 0
                }
            }
        }
    }

    /**
     * Forces a measure if the view hasn't been measured yet.
     */
    private fun measureChild(parent: RecyclerView, child: View) {
        if (ViewCompat.isLaidOut(child)) return
        val layoutManager = parent.layoutManager as? LinearLayoutManager ?: return
        val lp = child.layoutParams

        val widthSpec = RecyclerView.LayoutManager.getChildMeasureSpec(
            layoutManager.width, layoutManager.widthMode,
            layoutManager.paddingLeft + layoutManager.paddingRight, lp.width,
            layoutManager.canScrollHorizontally()
        )
        val heightSpec = RecyclerView.LayoutManager.getChildMeasureSpec(
            layoutManager.height, layoutManager.heightMode,
            layoutManager.paddingTop + layoutManager.paddingBottom, lp.height,
            layoutManager.canScrollVertically()
        )
        child.measure(widthSpec, heightSpec)
    }
}