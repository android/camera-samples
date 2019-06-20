/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma version(1)
#pragma rs java_package_name(com.example.android.hdrviewfinder)
#pragma rs_fp_relaxed

rs_allocation gCurrentFrame;
rs_allocation gPrevFrame;

int gCutPointX = 0;
int gDoMerge = 0;
int gFrameCounter = 0;

uchar4 __attribute__((kernel)) mergeHdrFrames(uchar4 prevPixel, uint32_t x, uint32_t y) {

    // Read in pixel values from latest frame - YUV color space

    uchar4 curPixel;
    curPixel.r = rsGetElementAtYuv_uchar_Y(gCurrentFrame, x, y);
    curPixel.g = rsGetElementAtYuv_uchar_U(gCurrentFrame, x, y);
    curPixel.b = rsGetElementAtYuv_uchar_V(gCurrentFrame, x, y);
    curPixel.a = 255;

    uchar4 mergedPixel;
    if (gDoMerge == 1) {
        // Complex HDR fusion technique
        mergedPixel = curPixel / 2 + prevPixel / 2;

        /* Experimental color saturation boosting merge
        mergedPixel.r = curPixel.r / 2 + prevPixel.r / 2;

        uchar saturationCurrent = abs(curPixel.g - 128) + abs(curPixel.b - 128);
        uchar saturationPrev = abs(prevPixel.g - 128) + abs(prevPixel.b - 128);
        mergedPixel.g = saturationCurrent > saturationPrev ? curPixel.g : prevPixel.g;
        mergedPixel.b = saturationCurrent > saturationPrev ? curPixel.b : prevPixel.b;
        */
    } else if (gCutPointX > 0) {
        // Composite side by side
        mergedPixel = ((x < gCutPointX) ^ (gFrameCounter & 0x1)) ?
                curPixel : prevPixel;
    } else {
        // Straight passthrough
        mergedPixel = curPixel;
    }

    // Convert YUV to RGB, JFIF transform with fixed-point math
    // R = Y + 1.402 * (V - 128)
    // G = Y - 0.34414 * (U - 128) - 0.71414 * (V - 128)
    // B = Y + 1.772 * (U - 128)

    int4 rgb;
    rgb.r = mergedPixel.r +
            mergedPixel.b * 1436 / 1024 - 179;
    rgb.g = mergedPixel.r -
            mergedPixel.g * 46549 / 131072 + 44 -
            mergedPixel.b * 93604 / 131072 + 91;
    rgb.b = mergedPixel.r +
            mergedPixel.g * 1814 / 1024 - 227;
    rgb.a = 255;

    // Store current pixel for next frame
    rsSetElementAt_uchar4(gPrevFrame, curPixel, x, y);

    // Write out merged HDR result
    uchar4 out = convert_uchar4(clamp(rgb, 0, 255));

    return out;
}
