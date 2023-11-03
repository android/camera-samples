/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.example.android.camera2.extensions.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraExtensionSession.StillCaptureLatency
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.android.camera.utils.YuvToRgbConverter
import com.example.android.camera2.extensions.R
import com.example.android.camera2.extensions.ZoomUtil
import com.example.android.camera2.extensions.databinding.FragmentCameraBinding
import com.google.android.material.slider.Slider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The still capture progress is in DONE state.
 */
private const val PROGRESS_STATE_DONE = 0
/**
 * The still capture progress is in HOLD_STILL state which should show a message to request the end
 * users to hold still.
 */
private const val PROGRESS_STATE_HOLD_STILL = 1
/**
 * The still capture progress is in STILL_PROCESSING state which should show a message to let the
 * end users know the still image is under processing.
 */
private const val PROGRESS_STATE_STILL_PROCESSING = 2

/*
 * This is the main camera fragment where all camera extension logic can be found.
 * The module demonstrates a typical camera extension use case with preview running in
 * a TextureView and also corresponding still capture functionality.
 * These features are only available on SDK 31 and higher.
 */
class CameraFragment : Fragment(), TextureView.SurfaceTextureListener {

  /** AndroidX navigation arguments */
  private val args: CameraFragmentArgs by navArgs()

  /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
  private val cameraManager: CameraManager by lazy {
    val context = requireContext().applicationContext
    context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
  }

  /**
   * Still capture image reader
   */
  private lateinit var stillImageReader: ImageReader

  /**
   * Preview surface
   */
  private lateinit var previewSurface: Surface

  /**
   * Size of preview
   */
  private lateinit var previewSize: Size

  /**
   * Camera extension characteristics for the current camera device.
   */
  private lateinit var extensionCharacteristics: CameraExtensionCharacteristics

  /**
   * Camera characteristics for the current camera device.
   */
  private lateinit var characteristics: CameraCharacteristics

  /**
   * Flag whether we should restart preview after an extension switch.
   */
  private var restartPreview = false

  /**
   * Track current extension type and index.
   */
  private var currentExtension = -1
  private var currentExtensionIdx = -1

  /**
   * The current camera extension session.
   */
  private lateinit var cameraExtensionSession: CameraExtensionSession

  /**
   * A reference to the opened [CameraDevice].
   */
  private lateinit var cameraDevice: CameraDevice

  /**
   * A reference to the current view binding.
   */
  private var _binding: FragmentCameraBinding? = null
  private val binding get() = _binding!!

  private var zoomRatio: Float = ZoomUtil.minZoom()

  /**
   * Track the extension strength support for current extension mode.
   */
  private var isExtensionStrengthAvailable = false

  /**
   * Track the extension strength setting.
   */
  private var extensionStrength = -1
  /**
   * Track the postview support for current extension mode.
   */
  private var isPostviewAvailable = false
  /**
   * Track the capture process progress support for current extension mode.
   */
  private var isCaptureProcessProgressAvailable = false
  /**
   * A reference to the image reader to receive the postview when it can be supported for current
   * extension mode.
   */
  private var postviewImageReader: ImageReader? = null
  /**
   * A ScheduledFuture for repeatedly updating the capture progress info.
   */
  private var progressInfoScheduledFuture: ScheduledFuture<*>? = null
  /**
   * Track the process state of current still capture request.
   */
  private var progressState = PROGRESS_STATE_DONE
  /**
   * Track the still capture latency of current still capture request.
   */
  private var stillCaptureLatency: StillCaptureLatency? = null
  /**
   *  Track the HOLD_STILL or STILL_PROCESSING start timestamp of current still capture request.
   */
  private var progressStartTimestampMs: Long = 0
  /**
   * Track the capture processing progress of current still capture request.
   */
  private var captureProcessingProgress = -1

  /**
   * Calculates the remaining duration for the latency of current state.
   *
   * The duration is calculated against the start timestamp which was stored when current process
   * state was begun.
   */
  private fun calculateRemainingDurationInMs(): Long {
    // Only supported when API level is 34 or above
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return 0
    }

    // Only supported when capture latency info is provided
    if (stillCaptureLatency == null) {
      return 0
    }

    val currentTimestampMs = SystemClock.elapsedRealtime()
    val pastTimeMs = currentTimestampMs - progressStartTimestampMs
    val remainingTimeMs = if (progressState == PROGRESS_STATE_HOLD_STILL) {
      stillCaptureLatency!!.captureLatency - pastTimeMs
    } else {
      stillCaptureLatency!!.processingLatency - pastTimeMs
    }

    return if (remainingTimeMs > 0) remainingTimeMs else 0
  }

  /**
   * Calculates the process progress for the latency of current state.
   *
   * The progress is calculated against the total latency duration of current state.
   */
  private fun calculateProgressByRemainingDuration(): Int {
    // Only supported when API level is 34 or above
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return 0
    }

    // Only supported when capture latency info is provided
    if (stillCaptureLatency == null) {
      return 0
    }

    val currentTimestampMs = SystemClock.elapsedRealtime()
    val pastTimeMs = currentTimestampMs - progressStartTimestampMs
    return (if (progressState == PROGRESS_STATE_HOLD_STILL) {
      pastTimeMs * 100 / stillCaptureLatency!!.captureLatency
    } else {
      pastTimeMs * 100 / stillCaptureLatency!!.processingLatency
    }).toInt()
  }

  /**
   * Lens facing of the working camera
   */
  private var lensFacing = CameraCharacteristics.LENS_FACING_BACK

  /**
   * Gesture detector used for tap to focus
   */
  private val tapToFocusListener = object : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapUp(event: MotionEvent): Boolean {
      return tapToFocus(event)
    }
  }

  // Define a scale gesture detector to respond to pinch events and call
  // setZoom on Camera.Parameters.
  private val scaleGestureListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
      if (!hasZoomSupport()) {
        return false
      }

      // In case there is any focus happening, stop it.
      cancelPendingAutoFocus()
      return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
      // Set the zoom level
      startZoom(detector.scaleFactor)
      return true
    }
  }

  /**
   * Used to dispatch auto focus cancel after a timeout
   */
  private val tapToFocusTimeoutHandler = Handler(Looper.getMainLooper())

  /**
   * Trivial capture callback implementation.
   */
  private val captureCallbacks: CameraExtensionSession.ExtensionCaptureCallback =
    object : CameraExtensionSession.ExtensionCaptureCallback() {
      override fun onCaptureStarted(
        session: CameraExtensionSession, request: CaptureRequest,
        timestamp: Long
      ) {
        Log.v(TAG, "onCaptureStarted ts: $timestamp")
      }

      override fun onCaptureProcessStarted(
        session: CameraExtensionSession,
        request: CaptureRequest
      ) {
        Log.v(TAG, "onCaptureProcessStarted")
        // Turns to STILL_PROCESSING stage when the request tag is STILL_CAPTURE_TAG
        if (request.tag == STILL_CAPTURE_TAG && progressState == PROGRESS_STATE_HOLD_STILL) {
          progressState = PROGRESS_STATE_STILL_PROCESSING
          progressStartTimestampMs = SystemClock.elapsedRealtime()
          requireActivity().runOnUiThread {
            binding.progressState?.text = getString(R.string.state_still_processing)
          }
        }
      }

      override fun onCaptureResultAvailable(
        session: CameraExtensionSession,
        request: CaptureRequest,
        result: TotalCaptureResult
      ) {
        Log.v(TAG, "onCaptureResultAvailable")
        if (request.tag == AUTO_FOCUS_TAG) {
          Log.v(TAG, "Auto focus region requested")

          // Consider listening for auto focus state such as auto focus locked
          cameraExtensionSession.stopRepeating()
          val autoFocusRegions = request.get(CaptureRequest.CONTROL_AF_REGIONS)
          submitRequest(
            CameraDevice.TEMPLATE_PREVIEW,
            previewSurface,
            true,
          ) { builder ->
            builder.apply {
              set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
              set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
              set(CaptureRequest.CONTROL_AF_REGIONS, autoFocusRegions)
            }
          }

          queueAutoFocusReset()
        }

        // The initial extension strength value will be provided by the capture results. Checks it
        // to set and show the slider for end users to adjust their preferred strength setting.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
          isExtensionStrengthAvailable && extensionStrength == -1 &&
          result.keys.contains(CaptureResult.EXTENSION_STRENGTH)
        ) {
          result.get(CaptureResult.EXTENSION_STRENGTH)?.let {
            extensionStrength = it
            requireActivity().runOnUiThread {
              binding.strengthSlider?.apply {
                value = extensionStrength.toFloat()
                visibility = View.VISIBLE
              }
            }
          }
        }
      }

      override fun onCaptureFailed(
        session: CameraExtensionSession,
        request: CaptureRequest
      ) {
        Log.v(TAG, "onCaptureProcessFailed")
        hideCaptureProgressUI()
      }

      override fun onCaptureSequenceCompleted(
        session: CameraExtensionSession,
        sequenceId: Int
      ) {
        Log.v(TAG, "onCaptureProcessSequenceCompleted: $sequenceId")
      }

      override fun onCaptureSequenceAborted(
        session: CameraExtensionSession,
        sequenceId: Int
      ) {
        Log.v(TAG, "onCaptureProcessSequenceAborted: $sequenceId")
        hideCaptureProgressUI()
      }

      override fun onCaptureProcessProgressed(
        session: CameraExtensionSession,
        request: CaptureRequest,
        progress: Int,
      ) {
        // Caches current processing progress and updates the progress info
        captureProcessingProgress = progress
        updateProgressInfo()
      }
    }

  /**
   * The slide OnChangeListener implementation for receiving the value change and submitting the
   * request to change the extension strength setting.
   */
  private val sliderOnChangeListener: Slider.OnChangeListener =
    object : Slider.OnChangeListener {
      override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          return
        }

        extensionStrength = value.toInt()
        submitRequest(
          CameraDevice.TEMPLATE_PREVIEW,
          previewSurface,
          true,
        ) { builder ->
          builder.apply {
            set(
              CaptureRequest.EXTENSION_STRENGTH,
              extensionStrength
            )
            Log.d(TAG, "submit request for extension strength: $extensionStrength")
          }
        }
      }
    }

  /**
   * A list of supported extensions
   */
  private val supportedExtensions = ArrayList<Int>()

  override fun onSurfaceTextureAvailable(
    surfaceTexture: SurfaceTexture,
    width: Int, height: Int
  ) {
    initializeCamera()
  }

  override fun onSurfaceTextureSizeChanged(
    surfaceTexture: SurfaceTexture,
    width: Int, height: Int
  ) {}

  override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
    return true
  }

  override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

  /** [HandlerThread] where all image store operations run */
  private val storeThread = HandlerThread("StoreThread").apply { start() }

  /** [Handler] corresponding to [storeThread] */
  private val storeHandler = Handler(storeThread.looper)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentCameraBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  @SuppressLint("MissingPermission", "ClickableViewAccessibility")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.texture.surfaceTextureListener = this

    val tapToFocusGestureDetector = GestureDetector(requireContext(), tapToFocusListener)
    val scaleGestureDetector = ScaleGestureDetector(requireContext(), scaleGestureListener)
    binding.texture.setOnTouchListener { _, event ->
      tapToFocusGestureDetector.onTouchEvent(event)
      scaleGestureDetector.onTouchEvent(event)
      true
    }

    extensionCharacteristics = cameraManager.getCameraExtensionCharacteristics(args.cameraId)
    characteristics = cameraManager.getCameraCharacteristics(args.cameraId)
    lensFacing = characteristics[CameraCharacteristics.LENS_FACING]!!
    supportedExtensions.addAll(extensionCharacteristics.supportedExtensions)
    if (currentExtension == -1) {
      currentExtension = supportedExtensions[0]
      currentExtensionIdx = 0
      refreshStrengthAndCaptureProgressAvailabilityInfo()
      binding.strengthSlider?.visibility = View.GONE
      extensionStrength = -1
      binding.switchButton.text = getExtensionLabel(currentExtension)
    }

    binding.switchButton.setOnClickListener { v ->
      if (v.id == R.id.switch_button) {
        lifecycleScope.launch(Dispatchers.IO) {
          currentExtensionIdx = (currentExtensionIdx + 1) % supportedExtensions.size
          currentExtension = supportedExtensions[currentExtensionIdx]
          refreshStrengthAndCaptureProgressAvailabilityInfo()
          extensionStrength = -1
          requireActivity().runOnUiThread {
            binding.strengthSlider?.visibility = View.GONE
            binding.switchButton.text = getExtensionLabel(currentExtension)
            restartPreview = true
          }
          try {
            cameraExtensionSession.stopRepeating()
            cameraExtensionSession.close()
          } catch (e: Exception) {
            Log.e(TAG, "Camera failure when closing camera extension")
          }
        }
      }
    }
    // React to user touching the capture button
    binding.captureButton.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          lifecycleScope.launch(Dispatchers.IO) {
            clearPendingAutoFocusReset()
            takePicture()
          }
        }
      }

      true
    }

    // Sets strength slider change listener
    binding.strengthSlider?.addOnChangeListener(sliderOnChangeListener)
  }

  /**
   * Refreshes the extension strength and capture progress related availability info.
   */
  private fun refreshStrengthAndCaptureProgressAvailabilityInfo() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      isExtensionStrengthAvailable =
        extensionCharacteristics.getAvailableCaptureRequestKeys(currentExtension)
          .contains(CaptureRequest.EXTENSION_STRENGTH)
      isPostviewAvailable = extensionCharacteristics.isPostviewAvailable(currentExtension)
      isCaptureProcessProgressAvailable =
        extensionCharacteristics.isCaptureProcessProgressAvailable(currentExtension)
    }
  }

  /**
   * Begin all camera operations in a coroutine. This function:
   * - Opens the camera
   * - Configures the camera extension session
   * - Starts the preview by dispatching a repeating request
   */
  private fun initializeCamera() = lifecycleScope.launch(Dispatchers.IO) {
    // Open the selected camera
    cameraDevice = openCamera(cameraManager, args.cameraId)

    startPreview()
  }

  /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
  @SuppressLint("MissingPermission")
  private suspend fun openCamera(
    manager: CameraManager,
    cameraId: String,
  ): CameraDevice = suspendCancellableCoroutine { cont ->
    manager.openCamera(cameraId, Dispatchers.IO.asExecutor(), object : CameraDevice.StateCallback() {
      override fun onOpened(device: CameraDevice) = cont.resume(device)

      override fun onDisconnected(device: CameraDevice) {
        Log.w(TAG, "Camera $cameraId has been disconnected")
        requireActivity().finish()
      }

      override fun onError(device: CameraDevice, error: Int) {
        val msg = when (error) {
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
    })
  }

  /**
   * Pick a preview resolution that is both close/same as the display size and supported by camera
   * and extensions.
   */
  @Throws(CameraAccessException::class)
  private fun pickPreviewResolution(manager: CameraManager, cameraId: String) : Size {
    val characteristics = manager.getCameraCharacteristics(cameraId)
    val map = characteristics.get(
      CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
    )
    val textureSizes = map!!.getOutputSizes(
      SurfaceTexture::class.java
    )
    val displaySize = Point()
    val displayMetrics = requireActivity().resources.displayMetrics
    displaySize.x = displayMetrics.widthPixels
    displaySize.y = displayMetrics.heightPixels
    if (displaySize.x < displaySize.y) {
      displaySize.x = displayMetrics.heightPixels
      displaySize.y = displayMetrics.widthPixels
    }
    val displayArRatio = displaySize.x.toFloat() / displaySize.y
    val previewSizes = ArrayList<Size>()
    for (sz in textureSizes) {
      val arRatio = sz.width.toFloat() / sz.height
      if (abs(arRatio - displayArRatio) <= .2f) {
        previewSizes.add(sz)
      }
    }
    val extensionSizes = extensionCharacteristics.getExtensionSupportedSizes(
      currentExtension, SurfaceTexture::class.java
    )
    if (extensionSizes.isEmpty()) {
      Toast.makeText(
        requireActivity(), "Invalid preview extension sizes!.",
        Toast.LENGTH_SHORT
      ).show()
      requireActivity().finish()
    }

    var previewSize = extensionSizes[0]
    val supportedPreviewSizes =
      previewSizes.stream().distinct().filter { o: Size -> extensionSizes.contains(o) }
        .collect(Collectors.toList())
    if (supportedPreviewSizes.isNotEmpty()) {
      var currentDistance = Int.MAX_VALUE
      for (sz in supportedPreviewSizes) {
        val distance = abs(sz.width * sz.height - displaySize.x * displaySize.y)
        if (currentDistance > distance) {
          currentDistance = distance
          previewSize = sz
        }
      }
    } else {
      Log.w(
        TAG, "No overlap between supported camera and extensions preview sizes using "
          + "first available!"
      )
    }

    return previewSize
  }

  /**
   * Starts the camera preview.
   */
  @Synchronized
  private fun startPreview() {
    if (!binding.texture.isAvailable) {
      return
    }

    previewSize = pickPreviewResolution(cameraManager, args.cameraId)
    previewSurface = createPreviewSurface(previewSize)
    stillImageReader = createStillImageReader()
    postviewImageReader = createPostviewImageReader()

    val outputConfig = ArrayList<OutputConfiguration>()
    outputConfig.add(OutputConfiguration(stillImageReader.surface))
    outputConfig.add(OutputConfiguration(previewSurface))
    val extensionConfiguration = ExtensionSessionConfiguration(
      currentExtension, outputConfig,
      Dispatchers.IO.asExecutor(), object : CameraExtensionSession.StateCallback() {
        override fun onClosed(session: CameraExtensionSession) {
          if (restartPreview) {
            stillImageReader.close()
            postviewImageReader?.close()
            restartPreview = false
            startPreview()
          } else {
            cameraDevice.close()
          }
        }

        override fun onConfigured(session: CameraExtensionSession) {
          cameraExtensionSession = session
          submitRequest(
            CameraDevice.TEMPLATE_PREVIEW,
            previewSurface,
            true
          ) { request ->
            request.apply {
              set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
            }
          }
        }

        override fun onConfigureFailed(session: CameraExtensionSession) {
          Toast.makeText(
            requireActivity(),
            "Failed to start camera extension preview.",
            Toast.LENGTH_SHORT
          ).show()
          requireActivity().finish()
        }
      }
    )
    // Adds postview image reader surface to extension session configuration if it is supported.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      postviewImageReader?.let {
        extensionConfiguration.postviewOutputConfiguration = OutputConfiguration(it.surface)
      }
    }
    try {
      cameraDevice.createExtensionSession(extensionConfiguration)
    } catch (e: CameraAccessException) {
      Toast.makeText(
        requireActivity(), "Failed during extension initialization!.",
        Toast.LENGTH_SHORT
      ).show()
      requireActivity().finish()
    }
  }

  /**
   * Creates the preview surface
   */
  private fun createPreviewSurface(previewSize: Size): Surface {
    val texture = binding.texture.surfaceTexture
    texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
    return Surface(texture)
  }

  /**
   * Creates the still image reader and sets up OnImageAvailableListener
   */
  private fun createStillImageReader(): ImageReader {
    val yuvColorEncodingSystemSizes = extensionCharacteristics.getExtensionSupportedSizes(
      currentExtension, ImageFormat.YUV_420_888
    )
    val jpegSizes = extensionCharacteristics.getExtensionSupportedSizes(
      currentExtension, ImageFormat.JPEG
    )
    val stillFormat = if (jpegSizes.isEmpty()) ImageFormat.YUV_420_888 else ImageFormat.JPEG
    val stillCaptureSize = if (jpegSizes.isEmpty()) yuvColorEncodingSystemSizes[0] else jpegSizes[0]
    val stillImageReader = ImageReader.newInstance(
      stillCaptureSize.width,
      stillCaptureSize.height, stillFormat, 1
    )
    stillImageReader.setOnImageAvailableListener(
      { reader: ImageReader ->
        var output: OutputStream
        try {
          reader.acquireLatestImage().use { image ->
            hideCaptureProgressUI()
            val file = File(
              requireActivity().getExternalFilesDir(null),
              if (image.format == ImageFormat.JPEG) "frame.jpg" else "frame.yuv"
            )
            output = FileOutputStream(file)
            output.write(getDataFromImage(image))
            output.close()
            Toast.makeText(
              requireActivity(), "Frame saved at: " + file.path,
              Toast.LENGTH_SHORT
            ).show()
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }, storeHandler
    )
    return stillImageReader
  }

  /**
   * Creates postview image reader and sets up OnImageAvailableListener if current extension mode
   * supports postview.
   */
  private fun createPostviewImageReader(): ImageReader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || !isPostviewAvailable) {
      return null
    }

    val jpegSupportedSizes = extensionCharacteristics.getPostviewSupportedSizes(
      currentExtension,
      Size(
        stillImageReader.width,
        stillImageReader.height
      ),
      ImageFormat.JPEG
    )
    val yuvSupportedSizes = extensionCharacteristics.getPostviewSupportedSizes(
      currentExtension,
      Size(
        stillImageReader.width,
        stillImageReader.height
      ),
      ImageFormat.YUV_420_888
    )
    val postviewSize: Size
    val postviewFormat: Int
    if (!jpegSupportedSizes.isEmpty()) {
      postviewSize = jpegSupportedSizes[0]
      postviewFormat = ImageFormat.JPEG
    } else {
      postviewSize = yuvSupportedSizes[0]
      postviewFormat = ImageFormat.YUV_420_888
    }
    val postviewImageReader =
      ImageReader.newInstance(postviewSize.width, postviewSize.height, postviewFormat, 1)
    postviewImageReader.setOnImageAvailableListener(
      { reader: ImageReader ->
        try {
          reader.acquireLatestImage().use { image ->
            drawPostviewImage(image)
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }, storeHandler
    )

    return postviewImageReader
  }

  /**
   * Draw postview image to the capture progress UI
   */
  private fun drawPostviewImage(image: Image) {
    createBitmapFromImage(image)?.let { bitmap ->
      requireActivity().runOnUiThread {
        binding.progressInfoImage?.apply {
          // The following settings are for correctly displaying the postview image in portrait
          // orientation which Camera2Extensions sample app currently supports for.
          if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            rotation = 90.0f
            scaleY = 1.0f
          } else {
            rotation = 270.0f
            scaleY = -1.0f
          }
          setImageBitmap(bitmap)
          visibility = View.VISIBLE
        }
      }
    }
  }

  private fun createBitmapFromImage(image: Image): Bitmap? {
    Log.d(TAG, "createBitmapFromImage from image of format ${image.format}")
    when (image.format) {
      ImageFormat.JPEG -> {
        val data = getDataFromImage(image)
        return BitmapFactory.decodeByteArray(data, 0, data.size, null)
      }

      ImageFormat.YUV_420_888 -> {
        val yuvToRgbConverter = YuvToRgbConverter(requireContext())
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(image, bitmap)
        return bitmap
      }
    }

    return null
  }

  /**
   * Takes a picture.
   */
  private fun takePicture() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      // Retrieves the still capture latency info
      cameraExtensionSession.realtimeStillCaptureLatency?.let {
        stillCaptureLatency = it
        progressStartTimestampMs = SystemClock.elapsedRealtime()
      }
      captureProcessingProgress = -1
      showCaptureProgressUI()
    }
    submitRequest(
      CameraDevice.TEMPLATE_STILL_CAPTURE,
      if (isPostviewAvailable) {
        listOf(stillImageReader.surface, postviewImageReader!!.surface)
      } else {
        listOf(stillImageReader.surface)
      },
      false
    ) { request ->
      request.apply {
        set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        setTag(STILL_CAPTURE_TAG)
      }
    }
  }

  /**
   * Shows the UI for capture progress info
   */
  private fun showCaptureProgressUI() {
    // Do not show the UI if none of still capture latency, process progress or postview is
    // supported.
    if (stillCaptureLatency == null && !isCaptureProcessProgressAvailable && !isPostviewAvailable) {
      return
    }

    progressState = PROGRESS_STATE_HOLD_STILL
    requireActivity().runOnUiThread {
      enableUiControls(false)
      binding.progressInfoContainer?.visibility = View.VISIBLE
      binding.progressState?.text = getString(R.string.state_hold_still)
      binding.progressIndicator?.isIndeterminate = stillCaptureLatency == null
    }
    // Schedules to execute a runnable repeatedly to update the progress info
    if (stillCaptureLatency != null) {
      progressInfoScheduledFuture = Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(
          { updateProgressInfo() }, 0, 100, TimeUnit.MILLISECONDS
        )
    }
  }

  private fun enableUiControls(isEnabled: Boolean) {
    requireActivity().runOnUiThread {
      binding.switchButton.isEnabled = isEnabled
      binding.captureButton.isEnabled = isEnabled
      binding.strengthSlider?.isEnabled = isEnabled
    }
  }

  /**
   * Updates the capture progress info
   */
  private fun updateProgressInfo() {
    requireActivity().runOnUiThread {
      binding.progressState?.text = when (progressState) {
        PROGRESS_STATE_HOLD_STILL -> resources.getString(R.string.state_hold_still)
        PROGRESS_STATE_STILL_PROCESSING -> resources.getString(R.string.state_still_processing)
        else -> ""
      }

      binding.progressIndicator?.isIndeterminate = false

      if (progressState == PROGRESS_STATE_STILL_PROCESSING && isCaptureProcessProgressAvailable) {
        binding.progressIndicator?.progress = captureProcessingProgress

        if (captureProcessingProgress == 100) {
          hideCaptureProgressUI()
        }
      }

      stillCaptureLatency?.let {
        val remainingDurationMs = calculateRemainingDurationInMs()
        binding.progressLatencyDuration?.text =
          resources.getString(R.string.latency_duration, (remainingDurationMs + 500) / 1000)
        // Updates the progress indicator according to the remaining duration of latency time if
        // capture process progress is not supported.
        if (progressState == PROGRESS_STATE_HOLD_STILL || !isCaptureProcessProgressAvailable) {
          binding.progressIndicator?.progress = calculateProgressByRemainingDuration()
        }
        // Automatically turns to still-processing state if capture process progress is not
        // supported
        if (remainingDurationMs.toInt() == 0 && progressState == PROGRESS_STATE_HOLD_STILL) {
          progressState = PROGRESS_STATE_STILL_PROCESSING
          progressStartTimestampMs = SystemClock.elapsedRealtime()
          updateProgressInfo()
        }
      }
    }
  }

  /**
   * Hides the UI for capture progress info
   */
  private fun hideCaptureProgressUI() {
    progressState = PROGRESS_STATE_DONE
    requireActivity().runOnUiThread {
      binding.progressInfoContainer?.apply {
        visibility = View.GONE
        binding.progressInfoImage?.visibility = View.GONE
        binding.progressLatencyDuration?.text = ""
      }
      enableUiControls(true)
    }
    progressInfoScheduledFuture?.apply {
      cancel(true)
      progressInfoScheduledFuture = null
    }
  }

  private fun submitRequest(
    templateType: Int,
    target: Surface,
    isRepeating: Boolean,
    block: (captureRequest: CaptureRequest.Builder) -> CaptureRequest.Builder) {
    return submitRequest(templateType, listOf(target), isRepeating, block)
  }

  private fun submitRequest(
    templateType: Int,
    targets: List<Surface>,
    isRepeating: Boolean,
    block: (captureRequest: CaptureRequest.Builder) -> CaptureRequest.Builder) {
    try {
      val captureBuilder = cameraDevice.createCaptureRequest(templateType)
        .apply {
          targets.forEach {
            addTarget(it)
          }
          if (tag != null) {
            setTag(tag)
          }
          block(this)
        }
      if (isRepeating) {
        cameraExtensionSession.setRepeatingRequest(
          captureBuilder.build(),
          Dispatchers.IO.asExecutor(),
          captureCallbacks
        )
      } else {
        cameraExtensionSession.capture(
          captureBuilder.build(),
          Dispatchers.IO.asExecutor(),
          captureCallbacks
        )
      }
    } catch (e: CameraAccessException) {
      Toast.makeText(
        requireActivity(), "Camera failed to submit capture request!.",
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  override fun onStop() {
    super.onStop()
    try {
      clearPendingAutoFocusReset()
      cameraDevice.close()
    } catch (exc: Throwable) {
      Log.e(TAG, "Error closing camera", exc)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    storeThread.quitSafely()
  }

  override fun onResume() {
    super.onResume()
    if (binding.texture.isAvailable) {
      initializeCamera()
    }
  }

  /**
   * Removes any pending operation to restart auto focus in continuous picture mode.
   */
  private fun clearPendingAutoFocusReset() {
    tapToFocusTimeoutHandler.removeCallbacksAndMessages(null)
  }

  /**
   * Queue operation to restart auto focus in continuous picture mode.
   */
  private fun queueAutoFocusReset() {
    tapToFocusTimeoutHandler.postDelayed({
      Log.v(TAG, "Reset auto focus back to continuous picture")
      cameraExtensionSession.stopRepeating()

      submitRequest(
        CameraDevice.TEMPLATE_PREVIEW,
        previewSurface,
        true,
      ) { builder ->
        builder.apply {
          set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
          )
        }
      }
    }, AUTO_FOCUS_TIMEOUT_MILLIS)
  }

  /**
   * Handles the tap to focus event.
   * This will cancel any existing focus operation and restart it at the new point.
   * Note: If the device doesn't support auto focus then this operation will abort and return
   * false.
   */
  private fun tapToFocus(event: MotionEvent): Boolean {
    if (!hasAutoFocusMeteringSupport()) {
      return false
    }

    // Reset zoom -- TODO(Support zoom and tap to focus)
    zoomRatio = ZoomUtil.minZoom()

    cameraExtensionSession.stopRepeating()
    cancelPendingAutoFocus()
    startAutoFocus(meteringRectangle(event))

    return true
  }

  /**
   * Not all camera extensions have auto focus metering support.
   * Returns true if auto focus metering is supported otherwise false.
   */
  private fun hasAutoFocusMeteringSupport(): Boolean {
    if (characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) == 0) {
      return false
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val availableExtensionRequestKeys =
        extensionCharacteristics.getAvailableCaptureRequestKeys(currentExtension)
      return availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_AF_TRIGGER) &&
              availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_AF_MODE) &&
              availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_AF_REGIONS)
    }

    return false
  }

  /**
   * Not all camera extensions have zoom support.
   * Returns true if zoom is supported otherwise false.
   */
  private fun hasZoomSupport(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val availableExtensionRequestKeys =
        extensionCharacteristics.getAvailableCaptureRequestKeys(currentExtension)
      return availableExtensionRequestKeys.contains(CaptureRequest.CONTROL_ZOOM_RATIO)
    }

    return false
  }


  /**
   * Translates a touch event relative to the preview surface to a region relative to the sensor.
   * Note: This operation does not account for zoom / crop and should be handled otherwise the touch
   * point won't correctly map to the sensor.
   */
  private fun meteringRectangle(event: MotionEvent): MeteringRectangle {
    val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!

    val halfMeteringRectWidth = (METERING_RECTANGLE_SIZE * sensorSize.width()) / 2
    val halfMeteringRectHeight = (METERING_RECTANGLE_SIZE * sensorSize.height()) / 2

    // Normalize the [x,y] touch point in the view port to values in the range of [0,1]
    val normalizedPoint = floatArrayOf(event.x / previewSize.height, event.y / previewSize.width)

    // Scale and rotate the normalized point such that it maps to the sensor region
    Matrix().apply {
      postRotate(-sensorOrientation.toFloat(), 0.5f, 0.5f)
      postScale(sensorSize.width().toFloat(), sensorSize.height().toFloat())
      mapPoints(normalizedPoint)
    }

    val meteringRegion = Rect(
      (normalizedPoint[0] - halfMeteringRectWidth).toInt().coerceIn(0, sensorSize.width()),
      (normalizedPoint[1] - halfMeteringRectHeight).toInt().coerceIn(0, sensorSize.height()),
      (normalizedPoint[0] + halfMeteringRectWidth).toInt().coerceIn(0, sensorSize.width()),
      (normalizedPoint[1] + halfMeteringRectHeight).toInt().coerceIn(0, sensorSize.height())
    )

    return MeteringRectangle(meteringRegion, MeteringRectangle.METERING_WEIGHT_MAX)
  }

  private fun startAutoFocus(meteringRectangle: MeteringRectangle) {
    submitRequest(
      CameraDevice.TEMPLATE_PREVIEW,
      previewSurface,
      false,
    ) { request ->
      request.apply {
        set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(meteringRectangle))
        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        setTag(AUTO_FOCUS_TAG)
      }
    }
  }

  private fun cancelPendingAutoFocus() {
    clearPendingAutoFocusReset()
    submitRequest(
      CameraDevice.TEMPLATE_PREVIEW,
      previewSurface,
      true
    ) { request ->
      request.apply {
        set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
      }
    }
  }

  private fun startZoom(scaleFactor: Float) {
    zoomRatio =
      (zoomRatio * scaleFactor).coerceIn(ZoomUtil.minZoom(), ZoomUtil.maxZoom(characteristics))
    Log.d(TAG, "onScale: $zoomRatio")
    submitRequest(
      CameraDevice.TEMPLATE_PREVIEW,
      previewSurface,
      true
    ) { request ->
      request.apply {
        set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio)
      }
    }
  }

  companion object {
    private val TAG = CameraFragment::class.java.simpleName
    private const val STILL_CAPTURE_TAG = "still_capture_tag"
    private const val AUTO_FOCUS_TAG = "auto_focus_tag"
    private const val AUTO_FOCUS_TIMEOUT_MILLIS = 5_000L
    private const val METERING_RECTANGLE_SIZE = 0.15f

    private fun getExtensionLabel(extension: Int): String {
      return when (extension) {
        CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
        CameraExtensionCharacteristics.EXTENSION_NIGHT -> "NIGHT"
        CameraExtensionCharacteristics.EXTENSION_BOKEH -> "BOKEH"
        CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH-> "FACE RETOUCH"
        else -> "AUTO"
      }
    }

    private fun getDataFromImage(image: Image): ByteArray {
      val format = image.format
      val width = image.width
      val height = image.height
      var rowStride: Int
      var pixelStride: Int
      val data: ByteArray

      // Read image data
      val planes = image.planes
      var buffer: ByteBuffer
      var offset = 0
      if (format == ImageFormat.JPEG) {
        buffer = planes[0].buffer
        data = ByteArray(buffer.limit())
        buffer.rewind()
        buffer[data]
        return data
      }
      data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
      var maxRowSize = planes[0].rowStride
      for (plane in planes) {
        if (maxRowSize < plane.rowStride) {
          maxRowSize = plane.rowStride
        }
      }
      val rowData = ByteArray(maxRowSize)
      for (i in planes.indices) {
        buffer = planes[i].buffer
        rowStride = planes[i].rowStride
        pixelStride = planes[i].pixelStride
        // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
        val w = if (i == 0) width else width / 2
        val h = if (i == 0) height else height / 2
        for (row in 0 until h) {
          val bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8
          var length: Int
          if (pixelStride == bytesPerPixel) {
            // Special case: optimized read of the entire row
            length = w * bytesPerPixel
            buffer[data, offset, length]
            offset += length
          } else {
            // Generic case: should work for any pixelStride but slower.
            // Use intermediate buffer to avoid read byte-by-byte from
            // DirectByteBuffer, which is very bad for performance
            length = (w - 1) * pixelStride + bytesPerPixel
            buffer[rowData, 0, length]
            for (col in 0 until w) {
              data[offset++] = rowData[col * pixelStride]
            }
          }
          // Advance buffer the remainder of the row stride
          if (row < h - 1) {
            buffer.position(buffer.position() + rowStride - length)
          }
        }
        buffer.rewind()
      }
      return data
    }
  }
}
