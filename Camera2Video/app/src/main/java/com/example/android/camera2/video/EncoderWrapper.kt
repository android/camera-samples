/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.example.android.camera2.video

import android.hardware.camera2.params.DynamicRangeProfiles
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Surface

import com.example.android.camera2.video.fragments.VideoCodecFragment

import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

/**
 * Encodes video by streaming to disk.
 */
class EncoderWrapper(width: Int,
                     height: Int,
                     bitRate: Int,
                     frameRate: Int,
                     dynamicRange: Long,
                     orientationHint: Int,
                     outputFile: File,
                     useMediaRecorder: Boolean,
                     videoCodec: Int) {
    companion object {
        val TAG = "EncoderWrapper"
        val VERBOSE = false
        val IFRAME_INTERVAL = 1 // sync one frame every second
    }

    private val mWidth = width
    private val mHeight = height
    private val mBitRate = bitRate
    private val mFrameRate = frameRate
    private val mDynamicRange = dynamicRange
    private val mOrientationHint = orientationHint
    private val mOutputFile = outputFile
    private val mUseMediaRecorder = useMediaRecorder
    private val mVideoCodec = videoCodec

    private val mMimeType = VideoCodecFragment.idToType(mVideoCodec)

    private val mEncoderThread: EncoderThread? by lazy {
        if (useMediaRecorder) {
            null
        } else {
            EncoderThread(mEncoder!!, outputFile, mOrientationHint)
        }
    }

    private val mEncoder: MediaCodec? by lazy {
        if (useMediaRecorder) {
            null
        } else {
            MediaCodec.createEncoderByType(mMimeType)
        }
    }

    private val mInputSurface: Surface by lazy {
        if (useMediaRecorder) {
            // Get a persistent Surface from MediaCodec, don't forget to release when done
            val surface = MediaCodec.createPersistentInputSurface()

            // Prepare and release a dummy MediaRecorder with our new surface
            // Required to allocate an appropriately sized buffer before passing the Surface as the
            //  output target to the capture session
            createRecorder(surface).apply {
                prepare()
                release()
            }

            surface
        } else {
            mEncoder!!.createInputSurface()
        }
    }

    private var mMediaRecorder: MediaRecorder? = null

    private fun createRecorder(surface: Surface): MediaRecorder {
        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(mOutputFile.absolutePath)
            setVideoEncodingBitRate(mBitRate)
            if (mFrameRate > 0) setVideoFrameRate(mFrameRate)
            setVideoSize(mWidth, mHeight)

            val videoEncoder = when (mVideoCodec) {
                VideoCodecFragment.VIDEO_CODEC_ID_H264 ->
                        MediaRecorder.VideoEncoder.H264
                VideoCodecFragment.VIDEO_CODEC_ID_HEVC ->
                        MediaRecorder.VideoEncoder.HEVC
                VideoCodecFragment.VIDEO_CODEC_ID_AV1 ->
                        MediaRecorder.VideoEncoder.AV1
                else -> throw IllegalArgumentException("Unknown video codec id")
            }

            setVideoEncoder(videoEncoder)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(16)
            setAudioSamplingRate(44100)
            setInputSurface(surface)
            setOrientationHint(mOrientationHint)
        }
    }

    /**
     * Configures encoder
     */
    init {
        if (useMediaRecorder) {
            mMediaRecorder = createRecorder(mInputSurface)
        } else {
            val codecProfile = when (mVideoCodec) {
                VideoCodecFragment.VIDEO_CODEC_ID_HEVC -> when {
                    dynamicRange == DynamicRangeProfiles.HLG10 ->
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
                    dynamicRange == DynamicRangeProfiles.HDR10 ->
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                    dynamicRange == DynamicRangeProfiles.HDR10_PLUS ->
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                    else -> -1
                }
                VideoCodecFragment.VIDEO_CODEC_ID_AV1 -> when {
                    dynamicRange == DynamicRangeProfiles.HLG10 ->
                            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10
                    dynamicRange == DynamicRangeProfiles.HDR10 ->
                            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
                    dynamicRange == DynamicRangeProfiles.HDR10_PLUS ->
                            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
                    else -> -1
                }
                else -> -1
            }

            val format = MediaFormat.createVideoFormat(mMimeType, width, height)

            // Set some properties.  Failing to specify some of these can cause the MediaCodec
            // configure() call to throw an unhelpful exception.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

            if (codecProfile != -1) {
                format.setInteger(MediaFormat.KEY_PROFILE, codecProfile)
                format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, getTransferFunction())
                format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing, true)
            }

            if (VERBOSE) Log.d(TAG, "format: " + format)

            // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
            // we can use for input and wrap it with a class that handles the EGL work.
            mEncoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun getTransferFunction() = when (mDynamicRange) {
        DynamicRangeProfiles.HLG10 -> MediaFormat.COLOR_TRANSFER_HLG
        DynamicRangeProfiles.HDR10 -> MediaFormat.COLOR_TRANSFER_ST2084
        DynamicRangeProfiles.HDR10_PLUS -> MediaFormat.COLOR_TRANSFER_ST2084
        else -> MediaFormat.COLOR_TRANSFER_SDR_VIDEO
    }

    /**
     * Returns the encoder's input surface.
     */
    public fun getInputSurface(): Surface {
        return mInputSurface
    }

    public fun start() {
        if (mUseMediaRecorder) {
            mMediaRecorder!!.apply {
                prepare()
                start()
            }
        } else {
            mEncoder!!.start()

            // Start the encoder thread last.  That way we're sure it can see all of the state
            // we've initialized.
            mEncoderThread!!.start()
            mEncoderThread!!.waitUntilReady()
        }
    }

    /**
     * Shuts down the encoder thread, and releases encoder resources.
     * <p>
     * Does not return until the encoder thread has stopped.
     */
    public fun shutdown(): Boolean {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")

        if (mUseMediaRecorder) {
            try {
                mMediaRecorder!!.stop()
            } catch (e: RuntimeException) {
                // https://developer.android.com/reference/android/media/MediaRecorder.html#stop()
                // RuntimeException will be thrown if no valid audio/video data has been received
                // when stop() is called, it usually happens when stop() is called immediately after
                // start(). In this case the output file is not properly constructed ans should be
                // deleted.
                Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                //noinspection ResultOfMethodCallIgnored
                mOutputFile.delete()
                return false
            }
        } else {
            val handler = mEncoderThread!!.getHandler()
            handler.sendMessage(handler.obtainMessage(EncoderThread.EncoderHandler.MSG_SHUTDOWN))
            try {
                mEncoderThread!!.join()
            } catch (ie: InterruptedException ) {
                Log.w(TAG, "Encoder thread join() was interrupted", ie)
            }

            mEncoder!!.stop()
            mEncoder!!.release()
        }
        return true
    }

    /**
     * Notifies the encoder thread that a new frame is available to the encoder.
     */
    public fun frameAvailable() {
        if (!mUseMediaRecorder) {
            val handler = mEncoderThread!!.getHandler()
            handler.sendMessage(handler.obtainMessage(
                    EncoderThread.EncoderHandler.MSG_FRAME_AVAILABLE))
        }
    }

    public fun waitForFirstFrame() {
        if (!mUseMediaRecorder) {
            mEncoderThread!!.waitForFirstFrame()
        }
    }

    /**
     * Object that encapsulates the encoder thread.
     * <p>
     * We want to sleep until there's work to do.  We don't actually know when a new frame
     * arrives at the encoder, because the other thread is sending frames directly to the
     * input surface.  We will see data appear at the decoder output, so we can either use
     * an infinite timeout on dequeueOutputBuffer() or wait() on an object and require the
     * calling app wake us.  It's very useful to have all of the buffer management local to
     * this thread -- avoids synchronization -- so we want to do the file muxing in here.
     * So, it's best to sleep on an object and do something appropriate when awakened.
     * <p>
     * This class does not manage the MediaCodec encoder startup/shutdown.  The encoder
     * should be fully started before the thread is created, and not shut down until this
     * thread has been joined.
     */
    private class EncoderThread(mediaCodec: MediaCodec,
                                outputFile: File,
                                orientationHint: Int): Thread() {
        val mEncoder = mediaCodec
        var mEncodedFormat: MediaFormat? = null
        val mBufferInfo = MediaCodec.BufferInfo()
        val mMuxer = MediaMuxer(outputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val mOrientationHint = orientationHint
        var mVideoTrack: Int = -1

        var mHandler: EncoderHandler? = null
        var mFrameNum: Int = 0

        val mLock: Object = Object()

        @Volatile
        var mReady: Boolean = false

        /**
         * Thread entry point.
         * <p>
         * Prepares the Looper, Handler, and signals anybody watching that we're ready to go.
         */
        public override fun run() {
            Looper.prepare()
            mHandler = EncoderHandler(this)    // must create on encoder thread
            Log.d(TAG, "encoder thread ready")
            synchronized (mLock) {
                mReady = true
                mLock.notify()    // signal waitUntilReady()
            }

            Looper.loop()

            synchronized (mLock) {
                mReady = false
                mHandler = null
            }
            Log.d(TAG, "looper quit")
        }

        /**
         * Waits until the encoder thread is ready to receive messages.
         * <p>
         * Call from non-encoder thread.
         */
        public fun waitUntilReady() {
            synchronized (mLock) {
                while (!mReady) {
                    try {
                        mLock.wait()
                    } catch (ie: InterruptedException) { /* not expected */ }
                }
            }
        }

        /**
         * Waits until the encoder has processed a single frame.
         * <p>
         * Call from non-encoder thread.
         */
        public fun waitForFirstFrame() {
            synchronized (mLock) {
                while (mFrameNum < 1) {
                    try {
                        mLock.wait()
                    } catch (ie: InterruptedException) {
                        ie.printStackTrace();
                    }
                }
            }
            Log.d(TAG, "Waited for first frame");
        }

        /**
         * Returns the Handler used to send messages to the encoder thread.
         */
        public fun getHandler(): EncoderHandler {
            synchronized (mLock) {
                // Confirm ready state.
                if (!mReady) {
                    throw RuntimeException("not ready")
                }
            }
            return mHandler!!
        }

        /**
         * Drains all pending output from the encoder, and adds it to the circular buffer.
         */
        public fun drainEncoder(): Boolean {
            val TIMEOUT_USEC: Long = 0     // no timeout -- check for buffers, bail if none
            var encodedFrame = false

            while (true) {
                var encoderStatus: Int = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC)
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    break;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Should happen before receiving buffers, and should only happen once.
                    // The MediaFormat contains the csd-0 and csd-1 keys, which we'll need
                    // for MediaMuxer.  It's unclear what else MediaMuxer might want, so
                    // rather than extract the codec-specific data and reconstruct a new
                    // MediaFormat later, we just grab it here and keep it around.
                    mEncodedFormat = mEncoder.getOutputFormat()
                    Log.d(TAG, "encoder output format changed: " + mEncodedFormat)
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                            encoderStatus)
                    // let's ignore it
                } else {
                    var encodedData: ByteBuffer? = mEncoder.getOutputBuffer(encoderStatus)
                    if (encodedData == null) {
                        throw RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null");
                    }

                    if ((mBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // The codec config data was pulled out when we got the
                        // INFO_OUTPUT_FORMAT_CHANGED status.  The MediaMuxer won't accept
                        // a single big blob -- it wants separate csd-0/csd-1 chunks --
                        // so simply saving this off won't work.
                        if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                        mBufferInfo.size = 0
                    }

                    if (mBufferInfo.size != 0) {
                        // adjust the ByteBuffer values to match BufferInfo (not needed?)
                        encodedData.position(mBufferInfo.offset)
                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size)

                        if (mVideoTrack == -1) {
                            mVideoTrack = mMuxer.addTrack(mEncodedFormat!!)
                            mMuxer.setOrientationHint(mOrientationHint)
                            mMuxer.start()
                            Log.d(TAG, "Started media muxer")
                        }

                        // mEncBuffer.add(encodedData, mBufferInfo.flags,
                        //         mBufferInfo.presentationTimeUs)
                        mMuxer.writeSampleData(mVideoTrack, encodedData, mBufferInfo)
                        encodedFrame = true

                        if (VERBOSE) {
                            Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                                    mBufferInfo.presentationTimeUs)
                        }
                    }

                    mEncoder.releaseOutputBuffer(encoderStatus, false)

                    if ((mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.w(TAG, "reached end of stream unexpectedly")
                        break      // out of while
                    }
                }
            }

            return encodedFrame
        }

        /**
         * Drains the encoder output.
         * <p>
         * See notes for {@link EncoderWrapper#frameAvailable()}.
         */
        fun frameAvailable() {
            if (VERBOSE) Log.d(TAG, "frameAvailable")
            if (drainEncoder()) {
                synchronized (mLock) {
                    mFrameNum++
                    mLock.notify()
                }
            }
        }

        /**
         * Tells the Looper to quit.
         */
        fun shutdown() {
            if (VERBOSE) Log.d(TAG, "shutdown")
            Looper.myLooper()!!.quit()
            mMuxer.stop()
            mMuxer.release()
        }

        /**
         * Handler for EncoderThread.  Used for messages sent from the UI thread (or whatever
         * is driving the encoder) to the encoder thread.
         * <p>
         * The object is created on the encoder thread.
         */
        public class EncoderHandler(et: EncoderThread): Handler() {
            companion object {
                val MSG_FRAME_AVAILABLE: Int = 0
                val MSG_SHUTDOWN: Int = 1
            }

            // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
            // but no real harm in it.
            private val mWeakEncoderThread = WeakReference<EncoderThread>(et)

            // runs on encoder thread
            public override fun handleMessage(msg: Message) {
                val what: Int = msg.what
                if (VERBOSE) {
                    Log.v(TAG, "EncoderHandler: what=" + what)
                }

                val encoderThread: EncoderThread? = mWeakEncoderThread.get()
                if (encoderThread == null) {
                    Log.w(TAG, "EncoderHandler.handleMessage: weak ref is null")
                    return
                }

                when (what) {
                    MSG_FRAME_AVAILABLE -> encoderThread.frameAvailable()
                    MSG_SHUTDOWN -> encoderThread.shutdown()
                    else -> throw RuntimeException("unknown message " + what)
                }
            }
        }
    }
}
