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

package com.example.android.hdrviewfinder;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;

/**
 * A SurfaceView that maintains its aspect ratio to be a desired target value.
 *
 * <p>Depending on the layout, the FixedAspectSurfaceView may not be able to maintain the
 * requested aspect ratio. This can happen if both the width and the height are exactly
 * determined by the layout.  To avoid this, ensure that either the height or the width is
 * adjustable by the view; for example, by setting the layout parameters to be WRAP_CONTENT for
 * the dimension that is best adjusted to maintain the aspect ratio.</p>
 */
public class FixedAspectSurfaceView extends SurfaceView {

    /**
     * Desired width/height ratio
     */
    private float mAspectRatio;

    private GestureDetector mGestureDetector;

    public FixedAspectSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Get initial aspect ratio from custom attributes
        TypedArray a =
                context.getTheme().obtainStyledAttributes(attrs,
                        R.styleable.FixedAspectSurfaceView, 0, 0);
        setAspectRatio(a.getFloat(
                R.styleable.FixedAspectSurfaceView_aspectRatio, 1.f));
        a.recycle();
    }

    /**
     * Set the desired aspect ratio for this view.
     *
     * @param aspect the desired width/height ratio in the current UI orientation. Must be a
     *               positive value.
     */
    public void setAspectRatio(float aspect) {
        if (aspect <= 0) {
            throw new IllegalArgumentException("Aspect ratio must be positive");
        }
        mAspectRatio = aspect;
        requestLayout();
    }

    /**
     * Set a gesture listener to listen for touch events
     */
    public void setGestureListener(Context context, GestureDetector.OnGestureListener listener) {
        if (listener == null) {
            mGestureDetector = null;
        } else {
            mGestureDetector = new GestureDetector(context, listener);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        // General goal: Adjust dimensions to maintain the requested aspect ratio as much
        // as possible. Depending on the measure specs handed down, this may not be possible

        // Only set one of these to true
        boolean scaleWidth = false;
        boolean scaleHeight = false;

        // Sort out which dimension to scale, if either can be. There are 9 combinations of
        // possible measure specs; a few cases below handle multiple combinations
        //noinspection StatementWithEmptyBody
        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
            // Can't adjust sizes at all, do nothing
        } else if (widthMode == MeasureSpec.EXACTLY) {
            // Width is fixed, heightMode either AT_MOST or UNSPECIFIED, so adjust height
            scaleHeight = true;
        } else if (heightMode == MeasureSpec.EXACTLY) {
            // Height is fixed, widthMode either AT_MOST or UNSPECIFIED, so adjust width
            scaleWidth = true;
        } else if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
            // Need to fit into box <= [width, height] in size.
            // Maximize the View's area while maintaining aspect ratio
            // This means keeping one dimension as large as possible and shrinking the other
            float boxAspectRatio = width / (float) height;
            if (boxAspectRatio > mAspectRatio) {
                // Box is wider than requested aspect; pillarbox
                scaleWidth = true;
            } else {
                // Box is narrower than requested aspect; letterbox
                scaleHeight = true;
            }
        } else if (widthMode == MeasureSpec.AT_MOST) {
            // Maximize width, heightSpec is UNSPECIFIED
            scaleHeight = true;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            // Maximize height, widthSpec is UNSPECIFIED
            scaleWidth = true;
        } else {
            // Both MeasureSpecs are UNSPECIFIED. This is probably a pathological layout,
            // with width == height == 0
            // but arbitrarily scale height anyway
            scaleHeight = true;
        }

        // Do the scaling
        if (scaleWidth) {
            width = (int) (height * mAspectRatio);
        } else if (scaleHeight) {
            height = (int) (width / mAspectRatio);
        }

        // Override width/height if needed for EXACTLY and AT_MOST specs
        width = View.resolveSizeAndState(width, widthMeasureSpec, 0);
        height = View.resolveSizeAndState(height, heightMeasureSpec, 0);

        // Finally set the calculated dimensions
        setMeasuredDimension(width, height);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector != null && mGestureDetector.onTouchEvent(event);
    }
}
