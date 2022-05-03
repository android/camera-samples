/*
 * Copyright 2020 The Android Open Source Project
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

package com.example.android.camera2.video.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.camera2.params.OutputConfiguration
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.BuildConfig
import com.example.android.camera2.video.CameraActivity
import com.example.android.camera2.video.R
import com.example.android.camera2.video.databinding.FragmentSurfaceViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.RuntimeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

import com.example.android.camera2.video.EncoderWrapper

class SurfaceViewFragment : Fragment() {

    /** Android ViewBinding */
    private var _fragmentBinding: FragmentSurfaceViewBinding? = null

    private val fragmentBinding get() = _fragmentBinding!!

    /** AndroidX navigation arguments */
    private val args: SurfaceViewFragmentArgs by navArgs()

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(args.cameraId)
    }

    /** File where the recording will be saved */
    private val outputFile: File by lazy { createFile(requireContext(), "mp4") }

    /**
     * Setup a [Surface] for the encoder
     */
    private val encoderSurface: Surface by lazy {
        encoder.getInputSurface()
    }

    /** [EncoderWrapper] utility class */
    private val encoder: EncoderWrapper by lazy { createEncoder() }

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)

    /** Performs recording animation of flashing screen */
    private val animationTask: Runnable by lazy {
        Runnable {
            // Flash white animation
            fragmentBinding.overlay.foreground = Color.argb(150, 255, 255, 255).toDrawable()
            // Wait for ANIMATION_FAST_MILLIS
            fragmentBinding.overlay.postDelayed({
                if (currentlyRecording) {
                    // Remove white flash animation
                    fragmentBinding.overlay.foreground = null
                    // Restart animation recursively
                    if (currentlyRecording) {
                        fragmentBinding.overlay.postDelayed(animationTask,
                                CameraActivity.ANIMATION_FAST_MILLIS)
                    }
                }
            }, CameraActivity.ANIMATION_FAST_MILLIS)
        }
    }

    /** Captures frames from a [CameraDevice] for our video recording */
    private lateinit var session: CameraCaptureSession

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Requests used for preview only in the [CameraCaptureSession] */
    private val previewRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            // Add the preview surface target
            addTarget(fragmentBinding.viewFinder.holder.surface)

            if (args.previewStabilization) {
                set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
            }
        }.build()
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(fragmentBinding.viewFinder.holder.surface)
            addTarget(encoderSurface)
            // Sets user requested FPS for all targets
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(args.fps, args.fps))

            if (args.previewStabilization) {
                set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
            }
        }.build()
    }

    private var recordingStartMillis: Long = 0L

    /** Orientation of the camera as 0, 90, 180, or 270 degrees */
    private val orientation: Int by lazy {
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    @Volatile
    private var currentlyRecording = false

    /** Condition variable for blocking until the recording completes */
    private val cvRecordingComplete = ConditionVariable(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBinding = FragmentSurfaceViewBinding.inflate(inflater, container, false)
        return fragmentBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentBinding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                        fragmentBinding.viewFinder.display, characteristics, SurfaceHolder::class.java)
                Log.d(TAG, "View finder size: ${fragmentBinding.viewFinder.width} x ${fragmentBinding.viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                fragmentBinding.viewFinder.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                fragmentBinding.viewFinder.post { initializeCamera() }
            }
        })
    }

    private fun createEncoder(): EncoderWrapper {
        val videoEncoder = when {
            args.dynamicRange == DynamicRangeProfiles.STANDARD -> MediaFormat.MIMETYPE_VIDEO_AVC
            args.dynamicRange < DynamicRangeProfiles.PUBLIC_MAX -> MediaFormat.MIMETYPE_VIDEO_HEVC
            else -> throw IllegalArgumentException("Unknown dynamic range format")
        }

        val codecProfile = when {
            args.dynamicRange == DynamicRangeProfiles.HLG10 ->
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            args.dynamicRange == DynamicRangeProfiles.HDR10 ->
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            args.dynamicRange == DynamicRangeProfiles.HDR10_PLUS ->
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
            else -> -1
        }

        return EncoderWrapper(args.width, args.height, RECORDER_VIDEO_BITRATE, args.fps,
                orientation, videoEncoder, codecProfile, outputFile)
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating request
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {

        // Open the selected camera
        camera = openCamera(cameraManager, args.cameraId, cameraHandler)

        // Creates list of Surfaces where the camera will output frames
        val targets = listOf(fragmentBinding.viewFinder.holder.surface, encoderSurface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        // Sends the capture request as frequently as possible until the session is torn down or
        //  session.stopRepeating() is called
        session.setRepeatingRequest(previewRequest, null, cameraHandler)

        // React to user touching the capture button
        fragmentBinding.captureButton.setOnTouchListener { view, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> lifecycleScope.launch(Dispatchers.IO) {

                    // Prevents screen rotation during the video recording
                    requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_LOCKED

                    // Finalizes encoder setup and starts recording
                    encoder.apply {
                        start()
                    }

                    currentlyRecording = true

                    // Start recording repeating requests, which will stop the ongoing preview
                    //  repeating requests without having to explicitly call `session.stopRepeating`
                    session.setRepeatingRequest(recordRequest,
                            object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(session: CameraCaptureSession,
                                                        request: CaptureRequest,
                                                        result: TotalCaptureResult) {
                            if (currentlyRecording) {
                                encoder.frameAvailable()
                            }
                        }
                    }, cameraHandler)

                    recordingStartMillis = System.currentTimeMillis()
                    Log.d(TAG, "Recording started")

                    // Starts recording animation
                    fragmentBinding.overlay.post(animationTask)
                }

                MotionEvent.ACTION_UP -> lifecycleScope.launch(Dispatchers.IO) {
                    session.stopRepeating()

                    fragmentBinding.captureButton.setOnTouchListener(null)

                    /* Wait until the session signals onReady */
                    cvRecordingComplete.block()

                    // Unlocks screen rotation after recording finished
                    requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                    // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
                    val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
                    if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                        delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
                    }

                    delay(CameraActivity.ANIMATION_SLOW_MILLIS)

                    try {
                        Log.d(TAG, "Recording stopped. Output file: $outputFile")
                        encoder.shutdown()
                    } catch (e: IllegalStateException) {
                        // Avoid crash, do nothing
                    }

                    // Broadcasts the media file to the rest of the system
                    MediaScannerConnection.scanFile(
                            requireView().context, arrayOf(outputFile.absolutePath), null, null)

                    // Launch external activity via intent to play video recorded using our provider
                    startActivity(Intent().apply {
                        action = Intent.ACTION_VIEW
                        type = MimeTypeMap.getSingleton()
                                .getMimeTypeFromExtension(outputFile.extension)
                        val authority = "${BuildConfig.APPLICATION_ID}.provider"
                        data = FileProvider.getUriForFile(view.context, authority, outputFile)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })

                    navController.popBackStack()
                }
            }

            true
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
            manager: CameraManager,
            cameraId: String,
            handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when(error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Creates a [CameraCaptureSession] with the dynamic range profile set.
     */
    private fun setupSessionWithDynamicRangeProfile(
            device: CameraDevice,
            targets: List<Surface>,
            handler: Handler? = null,
            stateCallback: CameraCaptureSession.StateCallback
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val outputConfigs = mutableListOf<OutputConfiguration>()
            for (target in targets) {
                val outputConfig = OutputConfiguration(target)
                outputConfig.setDynamicRangeProfile(args.dynamicRange)
                outputConfigs.add(outputConfig)
            }

            device.createCaptureSessionByOutputConfigurations(
                    outputConfigs, stateCallback, handler)
            return true
        } else {
            device.createCaptureSession(targets, stateCallback, handler)
            return false
        }
    }

    /**
     * Creates a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine)
     */
    private suspend fun createCaptureSession(
            device: CameraDevice,
            targets: List<Surface>,
            handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->
        val stateCallback = object: CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }

            /** Called after all captures have completed - shut down the encoder */
            override fun onReady(session: CameraCaptureSession) {
                if (!currentlyRecording) {
                    return
                }

                currentlyRecording = false
                cvRecordingComplete.open()
            }
        }

        setupSessionWithDynamicRangeProfile(device, targets, handler, stateCallback)
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        encoderSurface.release()
    }

    override fun onDestroyView() {
        _fragmentBinding = null
        super.onDestroyView()
    }

    companion object {
        private val TAG = SurfaceViewFragment::class.java.simpleName

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }
    }
}
