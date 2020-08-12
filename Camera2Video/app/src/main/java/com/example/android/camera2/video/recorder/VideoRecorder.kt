package com.example.android.camera2.video.recorder

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Range
import android.util.Size
import android.view.Surface
import com.example.android.camera.utils.OrientationLiveData
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VideoRecorder(
    private val context: Context,
    /** ビデオ録画サイズ */
    private val videoSize: Size,
    /** ビデオ録画FPS */
    private val videoFps: Int,
    /** 出力ファイルパス。省略時は自動で生成します。 */
    outputFile: File? = null
) {
    /** 利用側でプレビューなどに使うsurface群 */
    private var extraSurfaces = ArrayList<Surface>()
    private lateinit var session: CameraCaptureSession
    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    private val outputFile: File = outputFile ?: createFile(context)

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

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the recording surface and extra surfaces targets
            extraSurfaces.forEach { addTarget(it) }
            addTarget(recorderSurface)
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(videoFps, videoFps))
        }.build()
    }
    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    private fun createRecorder(surface: Surface, dummy: Boolean = false) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setAudioSamplingRate(AUDIO_SAMPLING_RATE)
        setVideoEncodingBitRate(VideoRecorder.VIDEO_BITRATE)
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
        // camera-samples サンプルでは setRepeatingRequest は listener=nullでもhandlerを指定していますが、
        // ドキュメントからもAOSPソースからもhandlerはlistenerの反応スレッドを制御するためだけに使われるようなので、handlerは使わないことにしました。
        // 参考: https://github.com/android/camera-samples/blob/master/Camera2Video/app/src/main/java/com/example/android/camera2/video/fragments/CameraFragment.kt#L254
        // 参考: http://gerrit.aospextended.com/plugins/gitiles/AospExtended/platform_frameworks_base/+/25df673b849de374cf1de40250dfd8a48b7ac28b/core/java/android/hardware/camera2/impl/CameraDevice.java#269
        // Start recording repeating requests, which will stop the ongoing preview
        // repeating requests without having to explicitly call `session.stopRepeating`
        // session.setRepeatingRequest(recordRequest, null, handler)
        session.setRepeatingRequest(recordRequest, null, null)

        // Finalizes recorder setup and starts recording
        recorder.apply {
            // Sets output orientation based on current sensor value at start time
            relativeOrientation.value?.let { setOrientationHint(it) }
            prepare()
            start()
        }
        recordingStartMillis = System.currentTimeMillis()
    }

    /**
     * レコーディングを停止して、出力ファイルを返す。
     * インスタンスあたりのoutputFileは固定(コンストラクタでの指定またはインスタンス化時の自動生成)なので、このメソッドは常に同一ファイルインスタンスを返します。
     */
    suspend fun stopRecording(): File {
        // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
        val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
        if (elapsedTimeMillis < VideoRecorder.MIN_REQUIRED_RECORDING_TIME_MILLIS) {
            delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
        }

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

    fun prepare(session: CameraCaptureSession, extraSurfaces: List<Surface> = emptyList(), relativeOrientation: OrientationLiveData) {
        this.extraSurfaces = ArrayList(extraSurfaces)
        this.session = session
        this.relativeOrientation = relativeOrientation
    }

    companion object {
        const val VIDEO_BITRATE: Int = 10_000_000
        const val AUDIO_SAMPLING_RATE: Int = 16_000
        const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 5000L

        /** Creates a [File] named with the current date and time */
        fun createFile(context: Context): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$FILE_EXT")
        }

        /**
         * outputFile で使う拡張子。
         */
        @SuppressWarnings
        const val FILE_EXT = "mp4"
    }
}
