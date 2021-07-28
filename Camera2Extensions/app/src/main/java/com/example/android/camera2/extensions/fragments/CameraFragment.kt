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
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraExtensionSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.ExtensionSessionConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.example.android.camera2.extensions.R
import com.example.android.camera2.extensions.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.stream.Collectors
import kotlin.RuntimeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch

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
   * Camera extension characteristics for the current camera device.
   */
  private lateinit var extensionCharacteristics: CameraExtensionCharacteristics

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
      }

      override fun onCaptureFailed(
        session: CameraExtensionSession,
        request: CaptureRequest
      ) {
        Log.v(TAG, "onCaptureProcessFailed")
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

    extensionCharacteristics = cameraManager.getCameraExtensionCharacteristics(args.cameraId)
    supportedExtensions.addAll(extensionCharacteristics.supportedExtensions)
    if (currentExtension == -1) {
      currentExtension = supportedExtensions[0]
      currentExtensionIdx = 0
      binding.switchButton.text = getExtensionLabel(currentExtension)
    }

    binding.switchButton.setOnClickListener { v ->
      if (v.id == R.id.switch_button) {
        lifecycleScope.launch(Dispatchers.IO) {
          currentExtensionIdx = (currentExtensionIdx + 1) % supportedExtensions.size
          currentExtension = supportedExtensions[currentExtensionIdx]
          requireActivity().runOnUiThread {
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
            takePicture()
          }
        }
      }

      true
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
    val texture = binding.texture.surfaceTexture
    val previewSize = pickPreviewResolution(cameraManager, args.cameraId)
    texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
    val previewSurface = Surface(texture)
    val yuvColorEncodingSystemSizes = extensionCharacteristics.getExtensionSupportedSizes(
      currentExtension, ImageFormat.YUV_420_888
    )
    val jpegSizes = extensionCharacteristics.getExtensionSupportedSizes(
      currentExtension, ImageFormat.JPEG
    )
    val stillFormat = if (jpegSizes.isEmpty()) ImageFormat.YUV_420_888 else ImageFormat.JPEG
    val stillCaptureSize = if (jpegSizes.isEmpty()) yuvColorEncodingSystemSizes[0] else jpegSizes[0]
    stillImageReader = ImageReader.newInstance(
      stillCaptureSize.width,
      stillCaptureSize.height, stillFormat, 1
    )
    stillImageReader.setOnImageAvailableListener(
      { reader: ImageReader ->
        var output: OutputStream
        try {
          reader.acquireLatestImage().use { image ->
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
    val outputConfig = ArrayList<OutputConfiguration>()
    outputConfig.add(OutputConfiguration(stillImageReader.surface))
    outputConfig.add(OutputConfiguration(previewSurface))
    val extensionConfiguration = ExtensionSessionConfiguration(
      currentExtension, outputConfig,
      Dispatchers.IO.asExecutor(), object : CameraExtensionSession.StateCallback() {
        override fun onClosed(session: CameraExtensionSession) {
          if (restartPreview) {
            stillImageReader.close()
            restartPreview = false
            startPreview()
          } else {
            cameraDevice.close()
          }
        }

        override fun onConfigured(session: CameraExtensionSession) {
          cameraExtensionSession = session
          try {
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureBuilder.addTarget(previewSurface)
            cameraExtensionSession.setRepeatingRequest(
              captureBuilder.build(),
              Dispatchers.IO.asExecutor(), captureCallbacks
            )
          } catch (e: CameraAccessException) {
            Toast.makeText(
              requireActivity(), "Failed to preview capture request.",
              Toast.LENGTH_SHORT
            ).show()
            requireActivity().finish()
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
   * Takes a picture.
   */
  private fun takePicture() {
    try {
      val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
      captureBuilder.addTarget(stillImageReader.surface)
      cameraExtensionSession.capture(
        captureBuilder.build(),
        Dispatchers.IO.asExecutor(), captureCallbacks
      )
    } catch (e: CameraAccessException) {
      Toast.makeText(
        requireActivity(), "Camera failed to start still capture!.",
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  override fun onStop() {
    super.onStop()
    try {
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

  companion object {
    private val TAG = CameraFragment::class.java.simpleName

    private fun getExtensionLabel(extension: Int): String {
      return when (extension) {
        CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
        CameraExtensionCharacteristics.EXTENSION_NIGHT -> "NIGHT"
        CameraExtensionCharacteristics.EXTENSION_BOKEH -> "BOKEH"
        CameraExtensionCharacteristics.EXTENSION_BEAUTY -> "BEAUTY"
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
