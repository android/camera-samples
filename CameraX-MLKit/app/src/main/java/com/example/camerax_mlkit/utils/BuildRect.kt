package com.example.camerax_mlkit.utils

import android.graphics.*
import android.graphics.drawable.Drawable

class BuildRect(private val boundingRect: Rect?, private val content: String) : Drawable() {

    private val boundingRectPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.YELLOW
        strokeWidth = 5F
        alpha = 200
    }

    private val contentRectPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.YELLOW
        alpha = 255
    }

    private val contentTextPaint = Paint().apply {
        color = Color.DKGRAY
        alpha = 255
        textSize = 36F
    }

    private val contentPadding = 25
    private var textWidth = contentTextPaint.measureText(content).toInt()

    override fun draw(canvas: Canvas) {
        boundingRect?.let { rect ->
            canvas.drawRect(rect, boundingRectPaint)
            canvas.drawRect(
                Rect(
                    rect.left,
                    rect.bottom + contentPadding / 2,
                    rect.left + textWidth + contentPadding * 2,
                    rect.bottom + contentTextPaint.textSize.toInt() + contentPadding
                ),
                contentRectPaint
            )
            canvas.drawText(
                content,
                (rect.left + contentPadding).toFloat(),
                (rect.bottom + contentPadding * 2).toFloat(),
                contentTextPaint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        boundingRectPaint.alpha = alpha
        contentRectPaint.alpha = alpha
        contentTextPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        boundingRectPaint.colorFilter = colorFilter
        contentRectPaint.colorFilter = colorFilter
        contentTextPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}