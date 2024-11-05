package com.example.camerax_mlkit

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.text.Text

class TextViewModel(line: Text.Line) {
    var boundingRect: Rect? = line.boundingBox
    var lineContent: String = ""
    var lineTouchCallback = { v: View, e: MotionEvent -> false }

    init {
        lineContent = line.text
        lineTouchCallback = { v: View, e: MotionEvent ->
            true // return true from the callback to signify the event was handled
        }
    }
}