/**
 * Copyright 2021 The Android Open Source Project
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

/**
 * Simple app to demonstrate CameraX Video capturing with Recorder ( to local files ), with the
 * following simple control follow:
 *   - user starts capture.
 *   - this app disables all UI selections.
 *   - this app enables capture run-time UI (pause/resume/stop).
 *   - user controls recording with run-time UI, eventually tap "stop" to end.
 *   - this app informs CameraX recording to stop with recording.stop() (or recording.close()).
 *   - CameraX notify this app that the recording is indeed stopped, with the Finalize event.
 *   - this app starts VideoViewer fragment to view the captured result.
*/

package com.example.android.camerax.video.fragments

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.ActivityInfo
import java.text.SimpleDateFormat
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.camera.core.AspectRatio
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.android.camerax.video.R
import com.example.android.camerax.video.databinding.FragmentCameraBinding
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.video.*
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.whenCreated
import kotlinx.coroutines.*
import java.util.*

class CameraFragment : Fragment() {

    // UI with ViewBinding
    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    /** Host's navigation controller */
    private val navController: NavController by lazy {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
    }

    private val cameraCapabilities = mutableListOf<CameraCapability>()

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var activeRecording: ActiveRecording? = null
    private lateinit var recordingState:VideoRecordEvent

    /// Camera UI inputs
    private var cameraIndex = 0
    private var qualitySelectorIndex = DEFAULT_QUALITY_SELECTOR_IDX
    private var audioEnabled = false

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    private var enumerationDeferred:Deferred<Unit>? = null

    // main cameraX capture functions
    /**
     *   Always bind preview + video capture use case combinations in this sample
     *   (VideoCapture can work on its own).
     */
    private fun bindCaptureUsecase() {
        lifecycleScope.launch {
            val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(getCameraLensFacing(cameraIndex))
                .build()
            val preview = Preview.Builder().setTargetAspectRatio(DEFAULT_ASPECT_RATIO)
                .build().apply {
                    setSurfaceProvider(fragmentCameraBinding.previewView.surfaceProvider)
                }

            // create the user required QualitySelector (video resolution): we know this is
            // supported, a valid qualitySelector will be created.
            val qualitySelector = QualitySelector.of(
                cameraCapabilities[cameraIndex].selector[qualitySelectorIndex])

            // build a recorder, which can:
            //   - record video/audio to MediaStore(only use here), File, ParcelFileDescriptor
            //   - be used create recording(s) (the recording performs recording)
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    requireParentFragment(),
                    cameraSelector,
                    videoCapture,
                    preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                resetUIandState()
            }
        }
    }

    /**
     * Kick start the video recording
     *   - config Recorder to capture to MediaStoreOutput
     *   - register RecordEvent Listener
     *   - apply audio request from user
     *   - start recording!
     * After this function, user could start/pause/resume/stop recording and application listens
     * to VideoRecordEvent for the current recording status.
     */
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            requireActivity().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        activeRecording =
               videoCapture.output.prepareRecording(requireActivity(), mediaStoreOutput)
               .withEventListener(
                    mainThreadExecutor,
                    captureListener
               )
               .apply { if (audioEnabled) withAudioEnabled() }
               .start()

        Log.i(TAG, "Recording started")
    }

    /**
     * CaptureEvent listener.
     */
    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status)
            recordingState = event

        updateUI(event)

        if (event is VideoRecordEvent.Finalize) {
            showVideo(event)
        }
    }

    /**
     * Retrieve the asked camera's type(lens facing type). In this sample, only 2 types:
     *   idx is even number:  CameraSelector.LENS_FACING_BACK
     *          odd number:   CameraSelector.LENS_FACING_FRONT
     */
    private fun getCameraLensFacing(idx: Int) : Int {
        if (cameraCapabilities.size == 0) {
            Log.i(TAG, "Error: This device does not have any camera, bailing out")
            requireActivity().finish()
        }
        return (cameraCapabilities[idx % cameraCapabilities.size].lensFacing)
    }

    /**
     * Query and cache for the platform's camera capabilities
     * The function is destructive as it unbinds all already bound use cases
     */
    data class CameraCapability(var lensFacing:Int, var selector:List<Int>)
    init {
        enumerationDeferred = lifecycleScope.async {
            whenCreated {
                val provider = ProcessCameraProvider.getInstance(requireContext()).await()

                provider.unbindAll()
                for (lens in arrayOf(CameraSelector.LENS_FACING_BACK, CameraSelector.LENS_FACING_FRONT)){
                    val cameraSelector = CameraSelector.Builder().requireLensFacing(lens).build()
                    try {
                        // just want to get the camera.cameraInfo to query capabilities
                        // we are not binding anything here.
                        if (provider.hasCamera(cameraSelector)) {
                            val camera = provider.bindToLifecycle(requireParentFragment(), cameraSelector)
                            val qualityCap = QualitySelector.getSupportedQualities(camera.cameraInfo)
                                .filter { quality ->
                                    listOf(QualitySelector.QUALITY_UHD,
                                        QualitySelector.QUALITY_FHD,
                                        QualitySelector.QUALITY_HD,
                                    ).contains(quality)
                                }
                            cameraCapabilities.add(CameraCapability(lens, qualityCap))
                        }
                    } catch (exc: java.lang.Exception) {
                        Log.e(TAG, "Camera Face $lens is not supported")
                    }
                }
            }
        }
    }

    /**
     * Initialize UI. Preview and Capture actions are configured in this function.
     * Note that preview and capture are both initialized either by UI or CameraX callbacks
     * (only except the very 1st time upon entering to this fragment in onViewCreated()
     */
    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun initializeUI() {
        fragmentCameraBinding.cameraButton.setOnClickListener{
            cameraIndex = (cameraIndex + 1) % cameraCapabilities.size
            // camera device change is instant:
            //   - preview needs to be restarted
            //   - qualitySelector selection is invalidated
            //   - camera selector UI need to update
            qualitySelectorIndex = DEFAULT_QUALITY_SELECTOR_IDX
            initializeQualitySectionsUI()
            bindCaptureUsecase()
        }

        fragmentCameraBinding.audioSelection.isChecked = audioEnabled
        fragmentCameraBinding.audioSelection.setOnClickListener {
            audioEnabled = fragmentCameraBinding.audioSelection.isChecked
        }

        // React to user touching the capture button
        fragmentCameraBinding.captureButton.setOnClickListener {
            if (!this::recordingState.isInitialized || recordingState is VideoRecordEvent.Finalize) {
                fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_pause)
                fragmentCameraBinding.stopButton.visibility = View.VISIBLE
                enableUI(false)
                startRecording()
            } else {
                when (recordingState) {
                    is VideoRecordEvent.Start -> {
                        activeRecording?.pause()
                        fragmentCameraBinding.stopButton.visibility = View.VISIBLE
                    }
                    is VideoRecordEvent.Pause -> {
                        activeRecording?.resume()
                    }
                    is VideoRecordEvent.Resume -> {
                        activeRecording?.pause()
                    }
                    else -> {
                        Log.e(TAG, "Unknown State ($recordingState) when Capture Button is pressed ")
                    }
                }
            }
        }
        fragmentCameraBinding.stopButton.setOnClickListener {
            // stopping
            fragmentCameraBinding.stopButton.visibility = View.INVISIBLE
            if (activeRecording == null || recordingState is VideoRecordEvent.Finalize) {
                return@setOnClickListener
            }

            val recording = activeRecording
            if (recording != null) {
                recording.stop()
                activeRecording = null
            }
            fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_start)
        }
        fragmentCameraBinding.captureStatus.text = getString(R.string.Idle)
    }

    /**
     * UpdateUI according to CameraX VideoRecordEvent type:
     *   - user starts capture.
     *   - this app disables all UI selections.
     *   - this app enables capture run-time UI (pause/resume/stop).
     *   - user controls recording with run-time UI, eventually tap "stop" to end.
     *   - this app informs CameraX recording to stop with recording.stop() (or recording.close()).
     *   - CameraX notify this app that the recording is indeed stopped, with the Finalize event.
     *   - this app starts VideoViewer fragment to view the captured result.
     */
    private fun updateUI(event: VideoRecordEvent) {
        val state = if (event is VideoRecordEvent.Status) recordingState.getName()
                    else event.getName()
        when (event) {
                is VideoRecordEvent.Status -> {
                    // placeholder: we update the UI with new status after this when() block,
                    // nothing needs to do here.
                }
                is VideoRecordEvent.Start -> {
                    fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_pause)
                    fragmentCameraBinding.captureButton.isEnabled = true
                    fragmentCameraBinding.stopButton.isEnabled = true
                }
                is VideoRecordEvent.Finalize-> {
                    fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_start)
                    fragmentCameraBinding.stopButton.visibility = View.INVISIBLE
                }
                is VideoRecordEvent.Pause -> {
                    fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_resume)
                }
                is VideoRecordEvent.Resume -> {
                    fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_pause)
                }
                else -> {
                    Log.e(TAG, "Error(Unknown Event) from Recorder")
                    return
                }
        }

        val stats = event.recordingStats
        val size = stats.numBytesRecorded / 1000
        val time = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(stats.recordedDurationNanos)
        var text = "${state}: recorded ${size}KB, in ${time}second"
        if(event is VideoRecordEvent.Finalize)
            text = "${text}\nFile saved to: ${event.outputResults.outputUri}"

        fragmentCameraBinding.captureStatus.text=text
        Log.i(TAG, "recording event: $text")
    }

    /**
     * Enable/disable UI:
     *    User could select the capture parameters when recording is not in session
     *    Once recording is started, need to disable able UI to avoid conflict.
     */
    private fun enableUI(enable: Boolean) {
        fragmentCameraBinding.cameraButton.isEnabled = enable
        fragmentCameraBinding.captureButton.isEnabled = enable
        fragmentCameraBinding.stopButton.isEnabled = enable

        // Hide the audio and the quality selector list
       val visible = if (!enable) View.INVISIBLE else View.VISIBLE
            fragmentCameraBinding.audioSelection.visibility = visible
            fragmentCameraBinding.qualitySelection.visibility = visible
    }

    /**
     * ResetUI (restart):
     *    in case binding failed, let's give it another change for re-try. In future cases
     *    we might fail and user get notified on the status
     */
    private fun resetUIandState() {
        lifecycleScope.launch(Dispatchers.Main) {
            enableUI(true)
            fragmentCameraBinding.captureButton.setImageResource(R.drawable.ic_start)
            fragmentCameraBinding.stopButton.visibility = View.INVISIBLE
            fragmentCameraBinding.captureStatus.text = "Capture system reset due to binding failure," +
                "\nyou could retry with new settings."
            bindCaptureUsecase()
        }
    }

    /**
     *  initializeQualitySectionsUI():
     *    Populate a ListView to display camera capabilities, one front, and one back facing
     *    which has been enumerated into:
     *       cameraCapabilities.
     *    User selection is saved to qualitySelectorIndex, used later in bind capture pipeline phase.
     */
    private fun initializeQualitySectionsUI() {
        val selectorStrings = cameraCapabilities[cameraIndex].selector.map {
            qualityMap.getString(it)
        }
        // Assign adapter to ListView
        fragmentCameraBinding.qualitySelection.adapter =
        object : ArrayAdapter<String?>(
            requireContext(),
            android.R.layout.simple_list_item_1, android.R.id.text1, selectorStrings)
        {
            override fun getView(position: Int, tvView: View?, parent: ViewGroup): View {
                return (super.getView(position, tvView, parent) as TextView)
                  .apply { setTextColor(ContextCompat.getColor(requireContext(), R.color.ic_white)) }
            }
        }

        val previousSelection = intArrayOf(-1)
        val previousView = arrayOf<View?>(null)
        fragmentCameraBinding.qualitySelection.setOnItemClickListener { _, view, position, _ ->
            if (previousView[0] != null) {
                previousView[0]!!.setBackgroundColor(0x00000000)
            }
            view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.icPressed))
            previousSelection[0] = position
            previousView[0] = view

            // cache the current quality selection index
            if (qualitySelectorIndex != position) {
                qualitySelectorIndex = position
                bindCaptureUsecase()   //  rebind the capture use case
            }
        }
    }

    /**
     * Display capture the video in MediaStore
     *     event: VideoRecordEvent.Finalize holding MediaStore URI
     */
    private fun showVideo(event: VideoRecordEvent) {

        if (event !is VideoRecordEvent.Finalize) {
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            navController.navigate(
                CameraFragmentDirections.actionCameraFragmentToVideoViewer(
                    event.outputResults.outputUri
                )
            )
        }
    }

    // System function implementations
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    // system functions starts
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.Main) {
            if(enumerationDeferred != null ) {
                enumerationDeferred!!.await()
                enumerationDeferred = null
            }
            initializeUI()
            initializeQualitySectionsUI()
            bindCaptureUsecase()
        }
    }
    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    companion object {
        // default QualitySelector if no input from UI
        const val DEFAULT_QUALITY_SELECTOR_IDX = 0
        const val DEFAULT_ASPECT_RATIO = AspectRatio.RATIO_16_9
        val TAG:String = CameraFragment::class.java.simpleName
        private val qualityMap = QualityMap()
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}

/**
 * a helper class mapping QualitySelector.value <--> its string
 */
internal class QualityMap {
    private val qualityToString = mapOf(
        QualitySelector.QUALITY_UHD to "QUALITY_UHD(2160p)",
        QualitySelector.QUALITY_FHD to "QUALITY_FHD(1080p)",
        QualitySelector.QUALITY_HD to "QUALITY_HD(720p)",
        QualitySelector.QUALITY_SD to "QUALITY_SD(480p)"
    )
    private val stringToQuality = qualityToString.map { Pair(it.value, it.key) }.toMap()

    fun getString(key:Int) :String {
        return try {
            qualityToString[key]!!
        } catch (exc: java.lang.Exception) {
            Log.e(CameraFragment.TAG, "QualitySelector $Int is NOT supported")
            "Not Supported"
        }
    }
    fun getKey(description : String) : Int {
        return try {
            stringToQuality[description]!!
        } catch (exc: java.lang.Exception) {
            Log.e(CameraFragment.TAG, "Quality $description is NOT supported")
            -1
        }
    }
}

/**
 * A helper extended function to get the name(string) for the VideoRecordEvent.
 */
fun VideoRecordEvent.getName() : String {
    return when (this) {
        is VideoRecordEvent.Status -> "Status"
        is VideoRecordEvent.Start -> "Started"
        is VideoRecordEvent.Finalize-> "Finalized"
        is VideoRecordEvent.Pause -> "Paused"
        is VideoRecordEvent.Resume -> "Resumed"
        else -> "Error(Unknown)"
    }
}