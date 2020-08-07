package com.example.android.camera2.video.recorder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera2.video.BuildConfig
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoRecorder(
    private val context: Context,
    private val handler: Handler,
    private val videoSize: Size,
    private val videoFps: Int
) {
    private lateinit var session: CameraCaptureSession
    private lateinit var previewSurface: Surface
    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData


    /**
     * Setup a persistent [Surface] for the recorder so we can use it as an output target for the
     * camera session without preparing the recorder
     */
    val recorderSurface: Surface by lazy {

        // Get a persistent Surface from MediaCodec, don't forget to release when done
        val surface = MediaCodec.createPersistentInputSurface()

        // Prepare and release a dummy MediaRecorder with our new surface
        // Required to allocate an appropriately sized buffer before passing the Surface as the
        //  output target to the capture session
        createRecorder(surface, dummy = true)

        surface
    }

    /**
     * Output file for video
     */
    val videoUri: Uri?
        get() = if (nextVideoAbsolutePath != null) {
            Uri.parse(nextVideoAbsolutePath)
        } else null
    private var nextVideoAbsolutePath: String? = null
    private var mediaRecorder: MediaRecorder? = null
    var recordingStartMillis: Long = 0L


    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    val cameraManager: CameraManager by lazy {
        val context = context.applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val recorder: MediaRecorder by lazy { createRecorder(recorderSurface) }

    /** File where the recording will be saved */
    private val outputFile: File by lazy { createFile(context, "mp4") }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(previewSurface)
            addTarget(recorderSurface)
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(videoFps, videoFps))
        }.build()
    }
    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    fun createRecorder(surface: Surface, dummy: Boolean = false) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(VideoRecorder.RECORDER_VIDEO_BITRATE)
        if (videoFps > 0) setVideoFrameRate(videoFps)
        setVideoSize(videoSize.width, videoSize.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setInputSurface(surface)

        if (dummy) {
            prepare()
            release()
        }
    }

    fun startRecording() {
        // Start recording repeating requests, which will stop the ongoing preview
        //  repeating requests without having to explicitly call `session.stopRepeating`
        session.setRepeatingRequest(recordRequest, null, handler)

        // Finalizes recorder setup and starts recording
        recorder.apply {
            Log.d(TAG, "inomata Recorder started.")
            // Sets output orientation based on current sensor value at start time
            relativeOrientation.value?.let { setOrientationHint(it) }
            prepare()
            start()
        }
        recordingStartMillis = System.currentTimeMillis()
        Log.d(TAG, "Recording started")
    }

    suspend fun stopRecording(): File {
        // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
        val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
        if (elapsedTimeMillis < VideoRecorder.MIN_REQUIRED_RECORDING_TIME_MILLIS) {
            delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
        }

        Log.d(TAG, "Recording stopped. Output file: $outputFile")
        recorder.stop()

        // Broadcasts the media file to the rest of the system
        MediaScannerConnection.scanFile(
                context, arrayOf(outputFile.absolutePath), null, null)

        return outputFile
    }

    fun release() {
        recorder.release()
        recorderSurface.release()
    }

    fun prepare(session: CameraCaptureSession, previewSurface: Surface, relativeOrientation: OrientationLiveData) {
        this.previewSurface = previewSurface
        this.session = session
        this.relativeOrientation = relativeOrientation
    }

    companion object {
        val TAG = VideoRecorder::class.java.simpleName
        const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 5000L

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }
    }
}
