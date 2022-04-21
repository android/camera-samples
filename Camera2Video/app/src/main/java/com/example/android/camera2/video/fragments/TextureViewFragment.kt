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

package com.example.android.camera2.video.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.media.ImageWriter
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.opengl.EGL14
import android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION
import android.opengl.EGL14.EGL_OPENGL_ES2_BIT
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Bundle
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
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
import com.example.android.camera.utils.getPreviewOutputSize
import com.example.android.camera2.video.BuildConfig
import com.example.android.camera2.video.CameraActivity
import com.example.android.camera2.video.R
import com.example.android.camera2.video.databinding.FragmentTextureViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.RuntimeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

import com.example.android.camera2.video.EncoderWrapper

/** Generates a fullscreen quad to cover the entire viewport. Applies the transform set on the
    camera surface to adjust for orientation and scaling when used for copying from the camera
    surface to the render surface. We will pass an identity matrix when copying from the render
    surface to the recording / preview surfaces. */
private val TRANSFORM_VSHADER = """
attribute vec4 vPosition;
uniform mat4 texMatrix;
varying vec2 vTextureCoord;
void main() {
    gl_Position = vPosition;
    vec4 texCoord = vec4((vPosition.xy + vec2(1.0, 1.0)) / 2.0, 0.0, 1.0);
    vTextureCoord = (texMatrix * texCoord).xy;
}
"""

/** Passthrough fragment shader, simply copies from the source texture */
private val PASSTHROUGH_FSHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
void main() {
    gl_FragColor = texture2D(sTexture, vTextureCoord);
}
"""

private val PORTRAIT_FSHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
void main() {
    float x = vTextureCoord.x * 2.0 - 1.0, y = vTextureCoord.y * 2.0 - 1.0;
    vec4 color = texture2D(sTexture, vTextureCoord);
    float r = sqrt(x * x + y * y);
    float theta = atan(y, x);
    gl_FragColor = color * (1.0 - r);
}
"""

private val IDENTITY_MATRIX = floatArrayOf(
    1.0f, 0.0f, 0.0f, 0.0f,
    0.0f, 1.0f, 0.0f, 0.0f,
    0.0f, 0.0f, 1.0f, 0.0f,
    0.0f, 0.0f, 0.0f, 1.0f
)

private val FULLSCREEN_QUAD = floatArrayOf(
    -1.0f, -1.0f,  // 0 bottom left
     1.0f, -1.0f,  // 1 bottom right
    -1.0f,  1.0f,  // 2 top left
     1.0f,  1.0f,  // 3 top right
)

class TextureViewFragment : Fragment(), SurfaceTexture.OnFrameAvailableListener {

    /** Android ViewBinding */
    private var _fragmentBinding: FragmentTextureViewBinding? = null

    private val fragmentBinding get() = _fragmentBinding!!

    /** AndroidX navigation arguments */
    private val args: TextureViewFragmentArgs by navArgs()

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

            if (currentlyRecording) {
                fragmentBinding.overlay.postDelayed({
                    // Remove white flash animation
                    fragmentBinding.overlay.foreground = null
                    // Restart animation recursively
                    if (currentlyRecording) {
                        fragmentBinding.overlay.postDelayed(animationTask,
                                CameraActivity.ANIMATION_FAST_MILLIS)
                    }
                }, CameraActivity.ANIMATION_FAST_MILLIS)
            }
        }
    }

    /** Captures frames from a [CameraDevice] for our video recording */
    private lateinit var session: CameraCaptureSession

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Size of the TextureView, discovered when the SurfaceTexture becomes available */
    private var previewSize = Size(0, 0)

    /** OpenGL texture for the SurfaceTexture provided to the camera */
    private val cameraTexId: Int by lazy {
        createTexture()
    }

    /** The SurfaceTexture provided to the camera for capture */
    private val cameraTexture: SurfaceTexture by lazy {
        val texture = SurfaceTexture(cameraTexId)
        texture.setOnFrameAvailableListener(this)
        texture.setDefaultBufferSize(args.width, args.height)
        texture
    }

    /** The above SurfaceTexture cast as a Surface */
    private val cameraSurface: Surface by lazy {
        Surface(cameraTexture)
    }

    /** OpenGL texture that will combine the camera output with rendering */
    private val renderTexId: Int by lazy {
        createTexture()
    }

    /** The SurfaceTexture we're rendering to */
    private val renderTexture: SurfaceTexture by lazy {
        val texture = SurfaceTexture(renderTexId)
        texture.setDefaultBufferSize(args.width, args.height)
        texture
    }

    /** The above SurfaceTexture cast as a Surface */
    private val renderSurface: Surface by lazy {
        Surface(renderTexture)
    }

    /** Request capture to the ImageReader surface */
    private val captureRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            // Add the preview surface target
            addTarget(cameraSurface)
        }.build()
    }

    private var recordingStartMillis: Long = 0L

    /** Storage space for setting the texMatrix uniform */
    private val texMatrix = FloatArray(16)

    /** Orientation of the camera as 0, 90, 180, or 270 degrees */
    private val orientation: Int by lazy {
        characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
    }

    @Volatile
    private var currentlyRecording = false

    /** Condition variable for blocking until the recording completes */
    private val cvRecordingComplete = ConditionVariable(false)

    /** EGL / OpenGL data. */
    private var eglDisplay = EGL14.EGL_NO_DISPLAY;
    private var eglContext = EGL14.EGL_NO_CONTEXT;
    private var eglConfig: EGLConfig? = null;
    private var eglRenderSurface: EGLSurface? = null
    private var eglEncoderSurface: EGLSurface? = null
    private var eglTextureViewSurface: EGLSurface? = null
    private var vertexShader = 0
    private var passthroughFragmentShader = 0
    private var portraitFragmentShader = 0

    private class ShaderProgram(id: Int,
                                vPositionLoc: Int,
                                texMatrixLoc: Int) {
        private val id = id
        private val vPositionLoc = vPositionLoc
        private val texMatrixLoc = texMatrixLoc

        public fun setVertexAttribArray(vertexCoords: FloatArray) {
            val nativeBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            nativeBuffer.order(ByteOrder.nativeOrder())
            val vertexBuffer = nativeBuffer.asFloatBuffer()
            vertexBuffer.put(vertexCoords)
            nativeBuffer.position(0)
            vertexBuffer.position(0)

            GLES20.glEnableVertexAttribArray(vPositionLoc)
            checkGlError("glEnableVertexAttribArray")
            GLES20.glVertexAttribPointer(vPositionLoc, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)
            checkGlError("glVertexAttribPointer")
        }

        public fun setTexMatrix(texMatrix: FloatArray) {
            GLES20.glUniformMatrix4fv(texMatrixLoc, 1, false, texMatrix, 0)
            checkGlError("glUniformMatrix4fv")
        }

        public fun useProgram() {
            GLES20.glUseProgram(id)
            checkGlError("glUseProgram")
        }
    }

    private var passthroughShaderProgram: ShaderProgram? = null
    private var portraitShaderProgram: ShaderProgram? = null

    /** Initialize the EGL display, context, and render surface */
    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = intArrayOf(0, 0)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = null;
            throw RuntimeException("unable to initialize EGL14")
        }

        val configAttribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1);
        val numConfigs = intArrayOf(1);
        EGL14.eglChooseConfig(eglDisplay, configAttribList, 0, configs,
                0, configs.size, numConfigs, 0)
        eglConfig = configs[0]!!

        val contextAttribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
                contextAttribList, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }

        val clientVersion = intArrayOf(0)
        EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION,
                clientVersion, 0)
        Log.v(TAG, "EGLContext created, client version " + clientVersion[0])

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglRenderSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, renderSurface,
                surfaceAttribs, 0)
        if (eglRenderSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL render surface")
        }
    }

    private fun createShaderResources() {
        vertexShader = createShader(GLES20.GL_VERTEX_SHADER, TRANSFORM_VSHADER)
        passthroughFragmentShader = createShader(GLES20.GL_FRAGMENT_SHADER, PASSTHROUGH_FSHADER)
        portraitFragmentShader = createShader(GLES20.GL_FRAGMENT_SHADER, PORTRAIT_FSHADER)
        passthroughShaderProgram = createShaderProgram(passthroughFragmentShader)
        portraitShaderProgram = createShaderProgram(portraitFragmentShader)
    }

    /** Creates the shader program used to copy data from one texture to another */
    private fun createShaderProgram(fragmentShader: Int): ShaderProgram {
        var shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)
        val linkStatus = intArrayOf(0)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val msg = "Could not link program: " + GLES20.glGetProgramInfoLog(shaderProgram)
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
            throw RuntimeException(msg)
        }

        var vPositionLoc = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        var texMatrixLoc = GLES20.glGetUniformLocation(shaderProgram, "texMatrix")

        return ShaderProgram(shaderProgram, vPositionLoc, texMatrixLoc)
    }

    /** Create a shader given its type and source string */
    private fun createShader(type: Int, source: String): Int {
        var shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = intArrayOf(0)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val msg = "Could not compile shader " + type + ": " + GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException(msg)
        }
        return shader
    }

    /** Create an OpenGL texture */
    private fun createTexture(): Int {
        val buffer = IntBuffer.allocate(1)
        GLES20.glGenTextures(1, buffer)
        val texId = buffer.get(0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE)
        return texId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBinding =
                FragmentTextureViewBinding.inflate(inflater, container, false)
        return fragmentBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentBinding.viewFinder.setSurfaceTextureListener(
                object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture,
                    width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                EGL14.eglDestroySurface(eglDisplay, eglTextureViewSurface)
                eglTextureViewSurface = null
                return true
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                previewSize = Size(width, height)
                Log.v(TAG, "View finder size: ${fragmentBinding.viewFinder.width} x "
                        + "${fragmentBinding.viewFinder.height}")
                Log.v(TAG, "Selected preview size: $previewSize")

                fragmentBinding.viewFinder.post {
                    if (eglContext == EGL14.EGL_NO_CONTEXT) {
                        initEGL()
                    }

                    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                    eglTextureViewSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface,
                            surfaceAttribs, 0)
                    if (eglTextureViewSurface == EGL14.EGL_NO_SURFACE) {
                        throw RuntimeException("Failed to create EGL texture view surface")
                    }

                    EGL14.eglMakeCurrent(eglDisplay, eglTextureViewSurface, eglTextureViewSurface, eglContext)

                    if (passthroughShaderProgram == null) {
                        createShaderResources()
                    }

                    initializeCamera()
                }
            }
        })
    }

    private fun createEncoder(): EncoderWrapper {
        var width = args.width
        var height = args.height

        /** Swap width and height if the camera is rotated on its side */
        if (orientation == 90 || orientation == 270) {
            width = args.height
            height = args.width
        }

        return EncoderWrapper(width, height, RECORDER_VIDEO_BITRATE, args.fps, 0,
                MediaFormat.MIMETYPE_VIDEO_AVC, -1, outputFile)
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
        val targets = listOf(cameraSurface)

        // Start a capture session using our open camera and list of Surfaces where frames will go
        session = createCaptureSession(camera, targets, cameraHandler)

        // Sends the capture request as frequently as possible until the session is torn down or
        //  session.stopRepeating() is called
        session.setRepeatingRequest(captureRequest, null, cameraHandler)

        // React to user touching the capture button
        fragmentBinding.captureButton.setOnTouchListener { view, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> lifecycleScope.launch(Dispatchers.IO) {
                    // Prevents screen rotation during the video recording
                    requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_LOCKED

                    val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
                    eglEncoderSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig,
                            encoderSurface, surfaceAttribs, 0)
                    if (eglEncoderSurface == EGL14.EGL_NO_SURFACE) {
                        throw RuntimeException("Failed to create EGL encoder surface")
                    }

                    // Finalizes encoder setup and starts recording
                    encoder.apply {
                        start()
                    }

                    currentlyRecording = true

                    recordingStartMillis = System.currentTimeMillis()
                    Log.v(TAG, "Recording started")

                    // Starts recording animation
                    fragmentBinding.overlay.post(animationTask)
                }

                MotionEvent.ACTION_UP -> lifecycleScope.launch(Dispatchers.IO) {
                    session.stopRepeating()

                    cameraTexture.setOnFrameAvailableListener(null)
                    fragmentBinding.viewFinder.setSurfaceTextureListener(null)
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

                    EGL14.eglDestroySurface(eglDisplay, eglEncoderSurface)
                    eglEncoderSurface = null
                    EGL14.eglDestroySurface(eglDisplay, eglRenderSurface)
                    eglRenderSurface = null

                    cameraTexture.release()

                    try {
                        Log.v(TAG, "Recording stopped. Output file: $outputFile")
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
                        data = FileProvider.getUriForFile(requireView().context, authority,
                                outputFile)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })

                    // Finishes our current camera screen
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
     * Creates a [CameraCaptureSession].
     */
    private fun setupSession(
            device: CameraDevice,
            targets: List<Surface>,
            handler: Handler? = null,
            stateCallback: CameraCaptureSession.StateCallback
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val outputConfigs = mutableListOf<OutputConfiguration>()
            for (target in targets) {
                val outputConfig = OutputConfiguration(target)
                outputConfig.setTimestampBase(OutputConfiguration.TIMESTAMP_BASE_MONOTONIC)
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

            override fun onReady(session: CameraCaptureSession) {
                if (!currentlyRecording) {
                    return
                }

                currentlyRecording = false
                cvRecordingComplete.open()
            }
        }

        setupSession(device, targets, handler, stateCallback)
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

    private fun copyTexture(texId: Int, texture: SurfaceTexture, viewportRect: Rect,
            shaderProgram: ShaderProgram) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        checkGlError("glClearColor")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        checkGlError("glClear")

        shaderProgram.useProgram()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        checkGlError("glActiveTexture")
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        checkGlError("glBindTexture")

        texture.getTransformMatrix(texMatrix)
        shaderProgram.setTexMatrix(texMatrix)

        shaderProgram.setVertexAttribArray(FULLSCREEN_QUAD)

        GLES20.glViewport(viewportRect.left, viewportRect.top, viewportRect.right,
                viewportRect.bottom)
        checkGlError("glViewport")
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
    }

    private fun copyCameraToRender() {
        EGL14.eglMakeCurrent(eglDisplay, eglRenderSurface, eglRenderSurface, eglContext)

        var shaderProgram = passthroughShaderProgram
        if (args.filterOn) {
            shaderProgram = portraitShaderProgram
        }

        copyTexture(cameraTexId, cameraTexture, Rect(0, 0, args.width, args.height),
            shaderProgram!!)

        EGL14.eglSwapBuffers(eglDisplay, eglRenderSurface)
        renderTexture.updateTexImage()
    }

    private fun copyRenderToPreview() {
        EGL14.eglMakeCurrent(eglDisplay, eglTextureViewSurface, eglRenderSurface, eglContext)

        var cameraAspectRatio = args.width.toFloat() / args.height.toFloat()

        if (orientation == 90 || orientation == 270) {
            cameraAspectRatio = args.height.toFloat() / args.width.toFloat()
        }

        val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()
        var viewportWidth = previewSize.width
        var viewportHeight = previewSize.height
        var viewportX = 0
        var viewportY = 0

        /** The camera display is not the same size as the video. Letterbox the preview so that
            we can see exactly how the video will turn out. */
        if (previewAspectRatio < cameraAspectRatio) {
            /** Avoid vertical stretching */
            viewportHeight = (viewportWidth.toFloat() / cameraAspectRatio).toInt()
            viewportY = (previewSize.height - viewportHeight) / 2
        } else {
            /** Avoid horizontal stretching */
            viewportWidth = (viewportHeight.toFloat() * cameraAspectRatio).toInt()
            viewportX = (previewSize.width - viewportWidth) / 2
        }

        copyTexture(renderTexId, renderTexture, Rect(viewportX, viewportY, viewportWidth,
                viewportHeight), passthroughShaderProgram!!)

        EGL14.eglSwapBuffers(eglDisplay, eglTextureViewSurface)
    }

    private fun copyRenderToEncode() {
        EGL14.eglMakeCurrent(eglDisplay, eglEncoderSurface, eglRenderSurface, eglContext)

        var viewportWidth = args.width
        var viewportHeight = args.height

        /** Swap width and height if the camera is rotated on its side. */
        if (orientation == 90 || orientation == 270) {
            viewportWidth = args.height
            viewportHeight = args.width
        }

        copyTexture(renderTexId, renderTexture, Rect(0, 0, viewportWidth, viewportHeight),
                passthroughShaderProgram!!)

        encoder.frameAvailable()

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglEncoderSurface,
                cameraTexture.getTimestamp())
        EGL14.eglSwapBuffers(eglDisplay, eglEncoderSurface)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        /** The camera API does not update the tex image. Do so here. */
        cameraTexture.updateTexImage()

        fragmentBinding.viewFinder.post({
            /** Copy from the camera texture to the render texture */
            if (eglRenderSurface != null) {
                copyCameraToRender()
            }

            /** Copy from the render texture to the TextureView */
            if (eglTextureViewSurface != null) {
                copyRenderToPreview()
            }

            /** Copy to the encoder surface if we're currently recording. */
            if (eglEncoderSurface != null && currentlyRecording) {
                copyRenderToEncode()
            }
        })
    }

    companion object {
        private val TAG = TextureViewFragment::class.java.simpleName

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }

        /** Check if OpenGL failed, and throw an exception if so */
        private fun checkGlError(op: String) {
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                val msg = op + ": glError 0x" + Integer.toHexString(error)
                Log.e(TAG, msg)
                throw RuntimeException(msg)
            }
        }
    }
}
