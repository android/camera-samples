package com.example.camerax_mlkit

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * A Drawable that handles displaying a QR Code's data and a bounding box around the QR code.
 */
class QrCodeDrawable(qrCodeViewModel: QrCodeViewModel) : Drawable() {
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

    private val qrCodeViewModel = qrCodeViewModel
    private val contentPadding = 25
    private var textWidth = contentTextPaint.measureText(qrCodeViewModel.qrContent).toInt()

    override fun draw(canvas: Canvas) {
        canvas.drawRect(qrCodeViewModel.boundingRect, boundingRectPaint)
        canvas.drawRect(
            Rect(
                qrCodeViewModel.boundingRect.left,
                qrCodeViewModel.boundingRect.bottom + contentPadding/2,
                qrCodeViewModel.boundingRect.left + textWidth + contentPadding*2,
                qrCodeViewModel.boundingRect.bottom + contentTextPaint.textSize.toInt() + contentPadding),
            contentRectPaint
        )
        canvas.drawText(
            qrCodeViewModel.qrContent,
            (qrCodeViewModel.boundingRect.left + contentPadding).toFloat(),
            (qrCodeViewModel.boundingRect.bottom + contentPadding*2).toFloat(),
            contentTextPaint
        )
    }

    override fun setAlpha(alpha: Int) {
        boundingRectPaint.alpha = alpha
        contentRectPaint.alpha = alpha
        contentTextPaint.alpha = alpha
    }

    override fun setColorFilter(colorFiter: ColorFilter?) {
        boundingRectPaint.colorFilter = colorFilter
        contentRectPaint.colorFilter = colorFilter
        contentTextPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}