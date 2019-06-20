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

import android.graphics.ImageFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;

/**
 * Renderscript-based merger for an HDR viewfinder
 */
public class ViewfinderProcessor {

    private Allocation mInputHdrAllocation;
    private Allocation mInputNormalAllocation;
    private Allocation mPrevAllocation;
    private Allocation mOutputAllocation;

    private Handler mProcessingHandler;
    private ScriptC_hdr_merge mHdrMergeScript;

    public ProcessingTask mHdrTask;
    public ProcessingTask mNormalTask;

    private int mMode;

    public final static int MODE_NORMAL = 0;
    public final static int MODE_HDR = 2;

    public ViewfinderProcessor(RenderScript rs, Size dimensions) {
        Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
        yuvTypeBuilder.setX(dimensions.getWidth());
        yuvTypeBuilder.setY(dimensions.getHeight());
        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
        mInputHdrAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(),
                Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);
        mInputNormalAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(),
                Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

        Type.Builder rgbTypeBuilder = new Type.Builder(rs, Element.RGBA_8888(rs));
        rgbTypeBuilder.setX(dimensions.getWidth());
        rgbTypeBuilder.setY(dimensions.getHeight());
        mPrevAllocation = Allocation.createTyped(rs, rgbTypeBuilder.create(),
                Allocation.USAGE_SCRIPT);
        mOutputAllocation = Allocation.createTyped(rs, rgbTypeBuilder.create(),
                Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);

        HandlerThread processingThread = new HandlerThread("ViewfinderProcessor");
        processingThread.start();
        mProcessingHandler = new Handler(processingThread.getLooper());

        mHdrMergeScript = new ScriptC_hdr_merge(rs);

        mHdrMergeScript.set_gPrevFrame(mPrevAllocation);

        mHdrTask = new ProcessingTask(mInputHdrAllocation, dimensions.getWidth()/2, true);
        mNormalTask = new ProcessingTask(mInputNormalAllocation, 0, false);

        setRenderMode(MODE_NORMAL);
    }

    public Surface getInputHdrSurface() {
        return mInputHdrAllocation.getSurface();
    }

    public Surface getInputNormalSurface() {
        return mInputNormalAllocation.getSurface();
    }

    public void setOutputSurface(Surface output) {
        mOutputAllocation.setSurface(output);
    }

    public void setRenderMode(int mode) {
        mMode = mode;
    }

    /**
     * Simple class to keep track of incoming frame count,
     * and to process the newest one in the processing thread
     */
    class ProcessingTask implements Runnable, Allocation.OnBufferAvailableListener {
        private int mPendingFrames = 0;
        private int mFrameCounter = 0;
        private int mCutPointX;
        private boolean mCheckMerge;

        private Allocation mInputAllocation;

        public ProcessingTask(Allocation input, int cutPointX, boolean checkMerge) {
            mInputAllocation = input;
            mInputAllocation.setOnBufferAvailableListener(this);
            mCutPointX = cutPointX;
            mCheckMerge = checkMerge;
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            synchronized(this) {
                mPendingFrames++;
                mProcessingHandler.post(this);
            }
        }

        @Override
        public void run() {

            // Find out how many frames have arrived
            int pendingFrames;
            synchronized(this) {
                pendingFrames = mPendingFrames;
                mPendingFrames = 0;

                // Discard extra messages in case processing is slower than frame rate
                mProcessingHandler.removeCallbacks(this);
            }

            // Get to newest input
            for (int i = 0; i < pendingFrames; i++) {
                mInputAllocation.ioReceive();
            }

            mHdrMergeScript.set_gFrameCounter(mFrameCounter++);
            mHdrMergeScript.set_gCurrentFrame(mInputAllocation);
            mHdrMergeScript.set_gCutPointX(mCutPointX);
            if (mCheckMerge && mMode == MODE_HDR) {
                mHdrMergeScript.set_gDoMerge(1);
            } else {
                mHdrMergeScript.set_gDoMerge(0);
            }

            // Run processing pass
            mHdrMergeScript.forEach_mergeHdrFrames(mPrevAllocation, mOutputAllocation);
            mOutputAllocation.ioSend();
        }
    }

}
