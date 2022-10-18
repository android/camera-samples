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

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.min

class FocusPointDrawable : Drawable() {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private var radius: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    fun setStrokeWidth(strokeWidth: Float): Boolean =
        if (paint.strokeWidth == strokeWidth) {
            false
        } else {
            paint.strokeWidth = strokeWidth
            true
        }

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width()
        val height = bounds.height()
        radius = min(width, height) / 2f - paint.strokeWidth / 2f
        centerX = width / 2f
        centerY = height / 2f
    }

    override fun draw(canvas: Canvas) {
        if (radius == 0f) return

        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFiter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}