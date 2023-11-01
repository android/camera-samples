package com.example.android.camera2.video

import android.util.Range
import android.util.Size

class FeatureCombination (resolution: Size, dynamicRangeProfile: Long, fpsRange: Range<Int>, videoStabilization: Int) {
    private val resolution = resolution;
    private val dynamicRangeProfile = dynamicRangeProfile;
    private val fpsRange = fpsRange;
    private val videoStabilization = videoStabilization;

    public fun getResolution() : Size {
        return resolution;
    }

    public fun getDynamicRangeProfile()  : Long {
        return dynamicRangeProfile;
    }

    public fun getFpsRange() : Range<Int> {
        return fpsRange;
    }

    public fun getVideoStabilization() : Int {
        return videoStabilization;
    }

    override fun toString(): String {
        return "Combination(resolution=${resolution.toString()}, " +
                "dynamicRangeProfile=${dynamicRangeProfile.toString()}, " +
                "fpsRange=${fpsRange.toString()}, videoStabilization=${videoStabilization.toString()})"
    }

}
