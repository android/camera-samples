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
import android.os.Looper
import android.os.Message
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
import com.example.android.camera.utils.AutoFitSurfaceView
import com.example.android.camera2.video.BuildConfig
import com.example.android.camera2.video.CameraActivity
import com.example.android.camera2.video.R
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

class HardwarePipeline(width: Int, height: Int, fps: Int, filterOn: Boolean,
        characteristics: CameraCharacteristics, encoder: EncoderWrapper,
        viewFinder: AutoFitSurfaceView) : Pipeline(width, height, fps, filterOn,
                characteristics, encoder, viewFinder) {
    private val renderThread: HandlerThread by lazy {
        val renderThread = HandlerThread("Camera2Video.RenderThread")
        renderThread.start()
        renderThread
    }

    private val renderHandler = RenderHandler(renderThread.getLooper(),
            width, height, fps, filterOn, characteristics, encoder)

    override fun createRecordRequest(session: CameraCaptureSession,
            previewStabilization: Boolean) : CaptureRequest {
        return renderHandler.createRecordRequest(session, previewStabilization)
    }

    override fun startRecording() {
        renderHandler.startRecording()
    }

    override fun stopRecording() {
        renderHandler.stopRecording()
    }

    override fun destroyWindowSurface() {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_DESTROY_WINDOW_SURFACE))
    }

    override fun setPreviewSize(previewSize: Size) {
        renderHandler.setPreviewSize(previewSize)
    }

    override fun createResources(surface: Surface) {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_CREATE_RESOURCES, 0, 0, surface))
    }

    override fun getTargets(): List<Surface> {
        return renderHandler.getTargets()
    }

    override fun actionDown(encoderSurface: Surface) {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_ACTION_DOWN, 0, 0, encoderSurface))
    }

    override fun clearFrameListener() {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_CLEAR_FRAME_LISTENER))
    }

    override fun cleanup() {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_CLEANUP))
    }

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

    private class RenderHandler(looper: Looper, width: Int, height: Int, fps: Int,
            filterOn: Boolean, characteristics: CameraCharacteristics, encoder: EncoderWrapper):
            Handler(looper), SurfaceTexture.OnFrameAvailableListener {
        companion object {
            val MSG_CREATE_RESOURCES = 0
            val MSG_DESTROY_WINDOW_SURFACE = 1
            val MSG_ACTION_DOWN = 2
            val MSG_CLEAR_FRAME_LISTENER = 3
            val MSG_CLEANUP = 4
            val MSG_ON_FRAME_AVAILABLE = 5
        }

        private val width = width
        private val height = height
        private val fps = fps
        private val filterOn = filterOn
        private val characteristics = characteristics
        private val encoder = encoder

        private var previewSize = Size(0, 0)

        /** OpenGL texture for the SurfaceTexture provided to the camera */
        private var cameraTexId: Int = 0

        /** The SurfaceTexture provided to the camera for capture */
        private lateinit var cameraTexture: SurfaceTexture

        /** The above SurfaceTexture cast as a Surface */
        private lateinit var cameraSurface: Surface

        /** OpenGL texture that will combine the camera output with rendering */
        private var renderTexId: Int = 0

        /** The SurfaceTexture we're rendering to */
        private lateinit var renderTexture: SurfaceTexture

        /** The above SurfaceTexture cast as a Surface */
        private lateinit var renderSurface: Surface

        /** Storage space for setting the texMatrix uniform */
        private val texMatrix = FloatArray(16)

        /** Orientation of the camera as 0, 90, 180, or 270 degrees */
        private val orientation: Int by lazy {
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        }

        @Volatile
        private var currentlyRecording = false

        /** EGL / OpenGL data. */
        private var eglDisplay = EGL14.EGL_NO_DISPLAY;
        private var eglContext = EGL14.EGL_NO_CONTEXT;
        private var eglConfig: EGLConfig? = null;
        private var eglRenderSurface: EGLSurface? = null
        private var eglEncoderSurface: EGLSurface? = null
        private var eglWindowSurface: EGLSurface? = null
        private var vertexShader = 0
        private var passthroughFragmentShader = 0
        private var portraitFragmentShader = 0

        private var passthroughShaderProgram: ShaderProgram? = null
        private var portraitShaderProgram: ShaderProgram? = null

        private val cvResourcesCreated = ConditionVariable(false)

        public fun startRecording() {
            currentlyRecording = true
        }

        public fun stopRecording() {
            currentlyRecording = false
        }

        public fun createRecordRequest(session: CameraCaptureSession,
                previewStabilization: Boolean) : CaptureRequest {
            cvResourcesCreated.block()

            // Capture request holds references to target surfaces
            return session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                // Add the preview surface target
                addTarget(cameraSurface)

                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))

                if (previewStabilization) {
                    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
                }
            }.build()
        }

        public fun setPreviewSize(previewSize: Size) {
            this.previewSize = previewSize
        }

        public fun getTargets(): List<Surface> {
            cvResourcesCreated.block()

            return listOf(cameraSurface)
        }

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

            val tmpSurfaceAttribs = intArrayOf(
                    EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val tmpSurface = EGL14.eglCreatePbufferSurface(
                    eglDisplay, eglConfig, tmpSurfaceAttribs, /*offset*/ 0)
            EGL14.eglMakeCurrent(eglDisplay, tmpSurface, tmpSurface, eglContext)
        }

        private fun createResources(surface: Surface) {
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                initEGL()
            }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglWindowSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface,
                    surfaceAttribs, 0)
            if (eglWindowSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("Failed to create EGL texture view surface")
            }

            EGL14.eglMakeCurrent(eglDisplay, eglWindowSurface, eglWindowSurface, eglContext)

            cameraTexId = createTexture()
            cameraTexture = SurfaceTexture(cameraTexId)
            cameraTexture.setOnFrameAvailableListener(this)
            cameraTexture.setDefaultBufferSize(width, height)
            cameraSurface = Surface(cameraTexture)

            renderTexId = createTexture()
            renderTexture = SurfaceTexture(renderTexId)
            renderTexture.setDefaultBufferSize(width, height)
            renderSurface = Surface(renderTexture)

            eglRenderSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, renderSurface,
                    surfaceAttribs, 0)
            if (eglRenderSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("Failed to create EGL render surface")
            }

            if (passthroughShaderProgram == null) {
                createShaderResources()
            }

            cvResourcesCreated.open()
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
            /* Check that EGL has been initialized. */
            if (eglDisplay == null) {
                throw IllegalStateException("EGL not initialized before call to createTexture()");
            }

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

        private fun destroyWindowSurface() {
            EGL14.eglDestroySurface(eglDisplay, eglWindowSurface)
            eglWindowSurface = null
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
            if (filterOn) {
                shaderProgram = portraitShaderProgram
            }

            copyTexture(cameraTexId, cameraTexture, Rect(0, 0, width, height),
                shaderProgram!!)

            EGL14.eglSwapBuffers(eglDisplay, eglRenderSurface)
            renderTexture.updateTexImage()
        }

        private fun copyRenderToPreview() {
            EGL14.eglMakeCurrent(eglDisplay, eglWindowSurface, eglRenderSurface, eglContext)

            val cameraAspectRatio = width.toFloat() / height.toFloat()
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

            EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)
        }

        private fun copyRenderToEncode() {
            EGL14.eglMakeCurrent(eglDisplay, eglEncoderSurface, eglRenderSurface, eglContext)

            var viewportWidth = width
            var viewportHeight = height

            /** Swap width and height if the camera is rotated on its side. */
            if (orientation == 90 || orientation == 270) {
                viewportWidth = height
                viewportHeight = width
            }

            copyTexture(renderTexId, renderTexture, Rect(0, 0, viewportWidth, viewportHeight),
                    passthroughShaderProgram!!)

            encoder.frameAvailable()

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglEncoderSurface,
                    cameraTexture.getTimestamp())
            EGL14.eglSwapBuffers(eglDisplay, eglEncoderSurface)
        }

        private fun actionDown(encoderSurface: Surface) {
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglEncoderSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig,
                    encoderSurface, surfaceAttribs, 0)
            if (eglEncoderSurface == EGL14.EGL_NO_SURFACE) {
                val error = EGL14.eglGetError()
                throw RuntimeException("Failed to create EGL encoder surface"
                        + ": eglGetError = 0x" + Integer.toHexString(error))
            }
        }

        private fun clearFrameListener() {
            cameraTexture.setOnFrameAvailableListener(null)
        }

        private fun cleanup() {
            EGL14.eglDestroySurface(eglDisplay, eglEncoderSurface)
            eglEncoderSurface = null
            EGL14.eglDestroySurface(eglDisplay, eglRenderSurface)
            eglRenderSurface = null

            cameraTexture.release()

            EGL14.eglDestroyContext(eglDisplay, eglContext)
        }

        private fun onFrameAvailableImpl(surfaceTexture: SurfaceTexture) {
            /** The camera API does not update the tex image. Do so here. */
            cameraTexture.updateTexImage()

            /** Copy from the camera texture to the render texture */
            if (eglRenderSurface != null) {
                copyCameraToRender()
            }

            /** Copy from the render texture to the TextureView */
            if (eglWindowSurface != null) {
                copyRenderToPreview()
            }

            /** Copy to the encoder surface if we're currently recording. */
            if (eglEncoderSurface != null && currentlyRecording) {
                copyRenderToEncode()
            }
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
            sendMessage(obtainMessage(MSG_ON_FRAME_AVAILABLE, 0, 0, surfaceTexture))
        }

        public override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_CREATE_RESOURCES -> createResources(msg.obj as Surface)
                MSG_DESTROY_WINDOW_SURFACE -> destroyWindowSurface()
                MSG_ACTION_DOWN -> actionDown(msg.obj as Surface)
                MSG_CLEAR_FRAME_LISTENER -> clearFrameListener()
                MSG_CLEANUP -> cleanup()
                MSG_ON_FRAME_AVAILABLE -> onFrameAvailableImpl(msg.obj as SurfaceTexture)
            }
        }
    }

    companion object {
        private val TAG = HardwarePipeline::class.java.simpleName

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
