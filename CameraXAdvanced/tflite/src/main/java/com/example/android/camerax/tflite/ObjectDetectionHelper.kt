/*
 * Copyright 2020 Google LLC
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

package com.example.android.camerax.tflite

import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage

/**
 * Helper class used to communicate between our app and the TF object detection model
 */
class ObjectDetectionHelper(private val tflite: Interpreter, private val labels: List<String>) {

    /** Abstraction object that wraps a prediction output in an easy to parse way */
    data class ObjectPrediction(val location: RectF, val label: String, val score: Float)

    private val locations = arrayOf(Array(OBJECT_COUNT) { FloatArray(4) })
    private val labelIndices =  arrayOf(FloatArray(OBJECT_COUNT))
    private val scores =  arrayOf(FloatArray(OBJECT_COUNT))

    private val outputBuffer = mapOf(
        0 to locations,
        1 to labelIndices,
        2 to scores,
        3 to FloatArray(1)
    )

    val predictions get() = (0 until OBJECT_COUNT).map {
        ObjectPrediction(

            // The locations are an array of [0, 1] floats for [top, left, bottom, right]
            location = locations[0][it].let {
                RectF(it[1], it[0], it[3], it[2])
            },

            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes + 1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            label = labels[1 + labelIndices[0][it].toInt()],

            // Score is a single value of [0, 1]
            score = scores[0][it]
        )
    }

    fun predict(image: TensorImage): List<ObjectPrediction> {
        tflite.runForMultipleInputsOutputs(arrayOf(image.buffer), outputBuffer)
        return predictions
    }

    companion object {
        const val OBJECT_COUNT = 10
    }
}