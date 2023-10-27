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

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.DataSpace
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.DynamicRangeProfiles
import android.hardware.HardwareBuffer
import android.hardware.SyncFence
import android.opengl.EGL14
import android.opengl.EGL14.EGL_NO_DISPLAY
import android.opengl.EGL14.EGL_NO_SURFACE
import android.opengl.EGL15
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20.GL_COLOR_ATTACHMENT0
import android.opengl.GLES20.GL_FRAMEBUFFER
import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.glFinish
import android.opengl.GLES20.glFlush
import android.opengl.GLES30
import android.os.Build
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.SurfaceControl
import androidx.annotation.RequiresApi
import androidx.opengl.EGLExt.Companion.eglCreateSyncKHR
import androidx.opengl.EGLImageKHR
import com.example.android.camera.utils.AutoFitSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.RuntimeException

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

/**
 * Fragment shaders
 */
private val INCLUDE_HLG_EOTF = """
// BT.2100 / BT.2020 HLG EOTF for one channel.
highp float hlgEotfSingleChannel(highp float hlgChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=265-279;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;
  return hlgChannel <= 0.5 ? hlgChannel * hlgChannel / 3.0 :
      (b + exp((hlgChannel - c) / a)) / 12.0;
}

// BT.2100 / BT.2020 HLG EOTF.
highp vec3 hlgEotf(highp vec3 hlgColor) {
  return vec3(
      hlgEotfSingleChannel(hlgColor.r),
      hlgEotfSingleChannel(hlgColor.g),
      hlgEotfSingleChannel(hlgColor.b)
  );
}
"""

private val INCLUDE_YUV_TO_RGB = """
vec3 yuvToRgb(vec3 yuv) {
  const mat3 yuvToRgbColorTransform = mat3(
    1.1689f, 1.1689f, 1.1689f,
    0.0000f, -0.1881f, 2.1502f,
    1.6853f, -0.6530f, 0.0000f
  );
  const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);
  yuv = yuv - yuvOffset;
  return clamp(yuvToRgbColorTransform * yuv, 0.0, 1.0);
}
"""

private val TRANSFORM_HDR_VSHADER = """#version 300 es
in vec4 vPosition;
uniform mat4 texMatrix;
out vec2 vTextureCoord;
out vec4 outPosition;
void main() {
    outPosition = vPosition;
    vec4 texCoord = vec4((vPosition.xy + vec2(1.0, 1.0)) / 2.0, 0.0, 1.0);
    vTextureCoord = (texMatrix * texCoord).xy;
    gl_Position = vPosition;
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

private val PASSTHROUGH_HDR_FSHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
uniform samplerExternalOES sTexture;
out vec4 outColor;
void main() {
    outColor = texture(sTexture, vTextureCoord);
}
"""

private val YUV_TO_RGB_PASSTHROUGH_HDR_FSHADER = """#version 300 es
#extension GL_EXT_YUV_target : require
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform __samplerExternal2DY2YEXT sTexture;
in vec2 vTextureCoord;
out vec4 outColor;
""" + INCLUDE_YUV_TO_RGB +
"""
void main() {
    vec4 color = texture(sTexture, vTextureCoord);
    color.rgb = yuvToRgb(color.rgb);
    outColor = color;
}
"""

private val YUV_TO_RGB_PORTRAIT_HDR_FSHADER = """#version 300 es
#extension GL_EXT_YUV_target : require
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform __samplerExternal2DY2YEXT sTexture;
in vec2 vTextureCoord;
out vec4 outColor;
""" + INCLUDE_YUV_TO_RGB +
"""
// BT.2100 / BT.2020 HLG OETF for one channel.
highp float hlgOetfSingleChannel(highp float linearChannel) {
  // Specification:
  // https://www.khronos.org/registry/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_HLG
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=529-543;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float a = 0.17883277;
  const highp float b = 0.28466892;
  const highp float c = 0.55991073;

  return linearChannel <= 1.0 / 12.0 ? sqrt(3.0 * linearChannel) :
      a * log(12.0 * linearChannel - b) + c;
}

// BT.2100 / BT.2020 HLG OETF.
highp vec3 hlgOetf(highp vec3 linearColor) {
  return vec3(
      hlgOetfSingleChannel(linearColor.r),
      hlgOetfSingleChannel(linearColor.g),
      hlgOetfSingleChannel(linearColor.b)
  );
}
""" + INCLUDE_HLG_EOTF +
"""
void main() {
    vec4 color = texture(sTexture, vTextureCoord);

    // Convert from YUV to RGB
    color.rgb = yuvToRgb(color.rgb);

    // Convert from HLG to linear
    color.rgb = hlgEotf(color.rgb);

    // Apply the portrait effect. Use gamma 2.4, roughly equivalent to what we expect in sRGB
    float x = vTextureCoord.x * 2.0 - 1.0, y = vTextureCoord.y * 2.0 - 1.0;
    float r = sqrt(x * x + y * y);
    color.rgb *= pow(1.0f - r, 2.4f);

    // Convert back to HLG
    color.rgb = hlgOetf(color.rgb);
    outColor = color;
}
"""

private val HLG_TO_LINEAR_HDR_FSHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES sTexture;
in vec2 vTextureCoord;
out vec4 outColor;
""" + INCLUDE_HLG_EOTF +
"""
void main() {
    vec4 color = texture(sTexture, vTextureCoord);

    // Convert from HLG electrical to linear optical [0.0, 1.0]
    color.rgb = hlgEotf(color.rgb);

    outColor = color;
}
"""

private val HLG_TO_PQ_HDR_FSHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES sTexture;
in vec2 vTextureCoord;
out vec4 outColor;
""" + INCLUDE_HLG_EOTF +
"""
// BT.2100 / BT.2020, PQ / ST2084 OETF.
highp vec3 pqOetf(highp vec3 linearColor) {
  // Specification:
  // https://registry.khronos.org/DataFormat/specs/1.3/dataformat.1.3.inline.html#TRANSFER_PQ
  // Reference implementation:
  // https://cs.android.com/android/platform/superproject/+/master:frameworks/native/libs/renderengine/gl/ProgramCache.cpp;l=514-527;drc=de09f10aa504fd8066370591a00c9ff1cafbb7fa
  const highp float m1 = (2610.0 / 16384.0);
  const highp float m2 = (2523.0 / 4096.0) * 128.0;
  const highp float c1 = (3424.0 / 4096.0);
  const highp float c2 = (2413.0 / 4096.0) * 32.0;
  const highp float c3 = (2392.0 / 4096.0) * 32.0;

  highp vec3 temp = pow(linearColor, vec3(m1));
  temp = (c1 + c2 * temp) / (1.0 + c3 * temp);
  return pow(temp, vec3(m2));
}

void main() {
    vec4 color = texture(sTexture, vTextureCoord);

    // Convert from HLG electrical to linear optical [0.0, 1.0]
    color.rgb = hlgEotf(color.rgb);

    // HLG has a different L = 1 than PQ, which is 10,000 cd/m^2.
    color.rgb /= 40.0f;

    // Convert from linear optical [0.0, 1.0] to PQ electrical
    color.rgb = pqOetf(color.rgb);

    outColor = color;
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

private val EGL_GL_COLORSPACE_KHR               = 0x309D
private val EGL_GL_COLORSPACE_BT2020_LINEAR_EXT = 0x333F
private val EGL_GL_COLORSPACE_BT2020_PQ_EXT     = 0x3340
private val EGL_GL_COLORSPACE_BT2020_HLG_EXT     = 0x3540
private val EGL_SMPTE2086_DISPLAY_PRIMARY_RX_EXT       = 0x3341
private val EGL_SMPTE2086_DISPLAY_PRIMARY_RY_EXT       = 0x3342
private val EGL_SMPTE2086_DISPLAY_PRIMARY_GX_EXT       = 0x3343
private val EGL_SMPTE2086_DISPLAY_PRIMARY_GY_EXT       = 0x3344
private val EGL_SMPTE2086_DISPLAY_PRIMARY_BX_EXT       = 0x3345
private val EGL_SMPTE2086_DISPLAY_PRIMARY_BY_EXT       = 0x3346
private val EGL_SMPTE2086_WHITE_POINT_X_EXT            = 0x3347
private val EGL_SMPTE2086_WHITE_POINT_Y_EXT            = 0x3348
private val EGL_SMPTE2086_MAX_LUMINANCE_EXT            = 0x3349
private val EGL_SMPTE2086_MIN_LUMINANCE_EXT            = 0x334A

class HardwarePipeline(width: Int, height: Int, fps: Int, filterOn: Boolean, transfer: Int,
        dynamicRange: Long, characteristics: CameraCharacteristics, encoder: EncoderWrapper,
        viewFinder: AutoFitSurfaceView) : Pipeline(width, height, fps, filterOn, dynamicRange,
                characteristics, encoder, viewFinder) {
    private val renderThread: HandlerThread by lazy {
        val renderThread = HandlerThread("Camera2Video.RenderThread")
        renderThread.start()
        renderThread
    }

    private val renderHandler = RenderHandler(renderThread.getLooper(),
            width, height, fps, filterOn, transfer, dynamicRange, characteristics, encoder, viewFinder)

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
        renderHandler.waitDestroyWindowSurface()
    }

    override fun setPreviewSize(previewSize: Size) {
        renderHandler.setPreviewSize(previewSize)
    }

    override fun createResources(surface: Surface) {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_CREATE_RESOURCES, 0, 0, surface))
    }

    override fun getPreviewTargets(): List<Surface> {
        return renderHandler.getTargets()
    }

    override fun getRecordTargets(): List<Surface> {
        return renderHandler.getTargets()
    }

    override fun actionDown(encoderSurface: Surface) {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_ACTION_DOWN, 0, 0, encoderSurface))
    }

    override fun clearFrameListener() {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_CLEAR_FRAME_LISTENER))
        renderHandler.waitClearFrameListener()
    }

    override fun cleanup() {
        renderHandler.sendMessage(renderHandler.obtainMessage(
                RenderHandler.MSG_CLEANUP))
        renderHandler.waitCleanup()
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

            GLES30.glEnableVertexAttribArray(vPositionLoc)
            checkGlError("glEnableVertexAttribArray")
            GLES30.glVertexAttribPointer(vPositionLoc, 2, GLES30.GL_FLOAT, false, 8, vertexBuffer)
            checkGlError("glVertexAttribPointer")
        }

        public fun setTexMatrix(texMatrix: FloatArray) {
            GLES30.glUniformMatrix4fv(texMatrixLoc, 1, false, texMatrix, 0)
            checkGlError("glUniformMatrix4fv")
        }

        public fun useProgram() {
            GLES30.glUseProgram(id)
            checkGlError("glUseProgram")
        }
    }

    private class RenderHandler(looper: Looper, width: Int, height: Int, fps: Int,
            filterOn: Boolean, transfer: Int, dynamicRange: Long,
            characteristics: CameraCharacteristics, encoder: EncoderWrapper,
            viewFinder: AutoFitSurfaceView): Handler(looper),
            SurfaceTexture.OnFrameAvailableListener {
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
        private val transfer = transfer
        private val dynamicRange = dynamicRange
        private val encoder = encoder
        private val viewFinder = viewFinder

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

        /** Stuff needed for displaying HLG via SurfaceControl */
        private var contentSurfaceControl: SurfaceControl? = null
        private var windowTexId: Int = 0
        private var windowFboId: Int = 0

        private var supportsNativeFences = false

        /** Storage space for setting the texMatrix uniform */
        private val texMatrix = FloatArray(16)

        /** Orientation of the camera as 0, 90, 180, or 270 degrees */
        private val orientation: Int by lazy {
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        }

        @Volatile
        private var currentlyRecording = false

        /** EGL / OpenGL data. */
        private var eglDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var eglConfig: EGLConfig? = null
        private var eglRenderSurface: EGLSurface? = EGL_NO_SURFACE
        private var eglEncoderSurface: EGLSurface? = EGL_NO_SURFACE
        private var eglWindowSurface: EGLSurface? = EGL_NO_SURFACE
        private var vertexShader = 0
        private var cameraToRenderFragmentShader = 0
        private var renderToPreviewFragmentShader = 0
        private var renderToEncodeFragmentShader = 0

        private var cameraToRenderShaderProgram: ShaderProgram? = null
        private var renderToPreviewShaderProgram: ShaderProgram? = null
        private var renderToEncodeShaderProgram: ShaderProgram? = null

        private val cvResourcesCreated = ConditionVariable(false)
        private val cvDestroyWindowSurface = ConditionVariable(false)
        private val cvClearFrameListener = ConditionVariable(false)
        private val cvCleanup = ConditionVariable(false)

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
            checkEglError("eglGetDisplay")

            val version = intArrayOf(0, 0)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                eglDisplay = null
                throw RuntimeException("Unable to initialize EGL14")
            }
            checkEglError("eglInitialize")

            val eglVersion = version[0] * 10 + version[1]
            Log.i(TAG, "eglVersion: " + eglVersion)

            /** Check that the necessary extensions for color spaces are supported if HDR is enabled */
            if (isHDR()) {
                val requiredExtensionsList = mutableListOf<String>("EGL_KHR_gl_colorspace")
                if (transfer == TransferFragment.PQ_ID) {
                    requiredExtensionsList.add("EGL_EXT_gl_colorspace_bt2020_pq")
                } else if (transfer == TransferFragment.LINEAR_ID) {
                    requiredExtensionsList.add("EGL_EXT_gl_colorspace_bt2020_linear")
                } else if (transfer == TransferFragment.HLG_ID) {
                    requiredExtensionsList.add("EGL_EXT_gl_colorspace_bt2020_hlg")
                }

                val eglExtensions = EGL14.eglQueryString(eglDisplay, EGL14.EGL_EXTENSIONS)

                for (requiredExtension in requiredExtensionsList) {
                    if (!eglExtensions.contains(requiredExtension)) {
                        Log.e(TAG, "EGL extension not supported: " + requiredExtension)
                        Log.e(TAG, "Supported extensions: ")
                        Log.e(TAG, eglExtensions)
                        throw RuntimeException("EGL extension not supported: " + requiredExtension)
                    }
                }

                // More devices can be supported if the eglCreateSyncKHR is used instead of
                // EGL15.eglCreateSync
                supportsNativeFences = eglVersion >= 15
                        && eglExtensions.contains("EGL_ANDROID_native_fence_sync")
            }

            Log.i(TAG, "isHDR: " + isHDR())
            if (isHDR()) {
                Log.i(TAG, "Preview transfer: " + TransferFragment.idToStr(transfer))
            }

            var renderableType = EGL14.EGL_OPENGL_ES2_BIT
            if (isHDR()) {
                renderableType = EGLExt.EGL_OPENGL_ES3_BIT_KHR
            }

            var rgbBits = 8
            var alphaBits = 8
            if (isHDR()) {
                rgbBits = 10
                alphaBits = 2
            }

            val configAttribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_RED_SIZE, rgbBits,
                EGL14.EGL_GREEN_SIZE, rgbBits,
                EGL14.EGL_BLUE_SIZE, rgbBits,
                EGL14.EGL_ALPHA_SIZE, alphaBits,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = intArrayOf(1)
            EGL14.eglChooseConfig(eglDisplay, configAttribList, 0, configs,
                    0, configs.size, numConfigs, 0)
            eglConfig = configs[0]!!

            var requestedVersion = 2
            if (isHDR()) {
                requestedVersion = 3
            }

            val contextAttribList = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, requestedVersion,
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

            var windowSurfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            if (isHDR()) {
                windowSurfaceAttribs = when (transfer) {
                    TransferFragment.PQ_ID -> intArrayOf(
                        EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT,
                        EGL14.EGL_NONE
                    )
                    TransferFragment.LINEAR_ID -> intArrayOf(
                        EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_LINEAR_EXT,
                        EGL14.EGL_NONE
                    )
                    // We configure HLG below
                    TransferFragment.HLG_ID -> intArrayOf(
                            EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_HLG_EXT,
                            EGL14.EGL_NONE
                    )
                    TransferFragment.HLG_WORKAROUND_ID -> intArrayOf(EGL14.EGL_NONE)
                    else -> throw RuntimeException("Unexpected transfer " + transfer)
                }
            }

            if (!isHDR() or (transfer != TransferFragment.HLG_WORKAROUND_ID)) {
                eglWindowSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, surface,
                    windowSurfaceAttribs, 0
                )
                if (eglWindowSurface == EGL_NO_SURFACE) {
                    throw RuntimeException("Failed to create EGL texture view surface")
                }
            }

            if (eglWindowSurface != EGL_NO_SURFACE) {
                /**
                 * This is only experimental for the transfer function. It is intended to be
                 * supplied alongside CTA 861.3 metadata
                 * https://registry.khronos.org/EGL/extensions/EXT/EGL_EXT_surface_CTA861_3_metadata.txt.
                 * which describes the max and average luminance of the content).
                 *
                 * The display will use these parameters to map the source content colors to a
                 * colors that fill the display's capabilities.
                 *
                 * Without providing these parameters, the display will assume "reasonable defaults",
                 * which may not be accurate for the source content. This would most likely result
                 * in inaccurate colors, although the exact effect is device-dependent.
                 *
                 * The parameters needs to be tuned.
                 * */
                if (isHDR() and (transfer == TransferFragment.PQ_ID)) {
                    val SMPTE2086_MULTIPLIER = 50000
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_MAX_LUMINANCE_EXT, 10000 * SMPTE2086_MULTIPLIER
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_MIN_LUMINANCE_EXT, 0
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_DISPLAY_PRIMARY_RX_EXT,
                        (0.708f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_DISPLAY_PRIMARY_RY_EXT,
                        (0.292f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_DISPLAY_PRIMARY_GX_EXT,
                        (0.170f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_DISPLAY_PRIMARY_GY_EXT,
                        (0.797f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_DISPLAY_PRIMARY_BX_EXT,
                        (0.131f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_DISPLAY_PRIMARY_BY_EXT,
                        (0.046f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_WHITE_POINT_X_EXT,
                        (0.3127f * SMPTE2086_MULTIPLIER).toInt()
                    )
                    EGL14.eglSurfaceAttrib(
                        eglDisplay, eglWindowSurface,
                        EGL_SMPTE2086_WHITE_POINT_Y_EXT,
                        (0.3290f * SMPTE2086_MULTIPLIER).toInt()
                    )
                }
            }

            cameraTexId = createTexture()
            cameraTexture = SurfaceTexture(cameraTexId)
            cameraTexture.setOnFrameAvailableListener(this)
            cameraTexture.setDefaultBufferSize(width, height)
            cameraSurface = Surface(cameraTexture)


            if (isHDR() and (transfer == TransferFragment.HLG_WORKAROUND_ID)) {
                // Communicating HLG content may not be supported on EGLSurface in API 33, as there
                // is no EGL extension for communicating the surface color space. Instead, create
                // a child SurfaceControl whose parent is the viewFinder's SurfaceView and push
                // buffers directly to the SurfaceControl.
                contentSurfaceControl = SurfaceControl.Builder()
                    .setName("HardwarePipeline")
                    .setParent(viewFinder.surfaceControl)
                    .setHidden(false)
                    .build()
                windowTexId = createTexId()
                windowFboId = createFboId()
            }

            renderTexId = createTexture()
            renderTexture = SurfaceTexture(renderTexId)
            renderTexture.setDefaultBufferSize(width, height)
            renderSurface = Surface(renderTexture)

            var renderSurfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglRenderSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, renderSurface,
                    renderSurfaceAttribs, 0)
            if (eglRenderSurface == EGL_NO_SURFACE) {
                throw RuntimeException("Failed to create EGL render surface")
            }

            createShaderResources()
            cvResourcesCreated.open()
        }

        private fun createShaderResources() {
            if (isHDR()) {
                /** Check that GL_EXT_YUV_target is supported for HDR */
                val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS)
                if (!extensions.contains("GL_EXT_YUV_target")) {
                    throw RuntimeException("Device does not support GL_EXT_YUV_target")
                }

                vertexShader = createShader(GLES30.GL_VERTEX_SHADER, TRANSFORM_HDR_VSHADER)

                cameraToRenderFragmentShader = when (filterOn) {
                    false -> createShader(GLES30.GL_FRAGMENT_SHADER,
                            YUV_TO_RGB_PASSTHROUGH_HDR_FSHADER)
                    true -> createShader(GLES30.GL_FRAGMENT_SHADER,
                            YUV_TO_RGB_PORTRAIT_HDR_FSHADER)
                }
                cameraToRenderShaderProgram = createShaderProgram(cameraToRenderFragmentShader)

                renderToPreviewFragmentShader = when (transfer) {
                    TransferFragment.PQ_ID -> createShader(GLES30.GL_FRAGMENT_SHADER,
                            HLG_TO_PQ_HDR_FSHADER)
                    TransferFragment.LINEAR_ID -> createShader(GLES30.GL_FRAGMENT_SHADER,
                            HLG_TO_LINEAR_HDR_FSHADER)
                    TransferFragment.HLG_ID,
                    TransferFragment.HLG_WORKAROUND_ID -> createShader(GLES30.GL_FRAGMENT_SHADER,
                            PASSTHROUGH_HDR_FSHADER)
                    else -> throw RuntimeException("Unexpected transfer " + transfer)
                }

                renderToPreviewShaderProgram = createShaderProgram(
                        renderToPreviewFragmentShader)

                renderToEncodeFragmentShader = createShader(GLES30.GL_FRAGMENT_SHADER,
                        PASSTHROUGH_HDR_FSHADER)
                renderToEncodeShaderProgram = createShaderProgram(renderToEncodeFragmentShader)
            } else {
                vertexShader = createShader(GLES30.GL_VERTEX_SHADER, TRANSFORM_VSHADER)

                val passthroughFragmentShader = createShader(GLES30.GL_FRAGMENT_SHADER,
                        PASSTHROUGH_FSHADER)
                val passthroughShaderProgram = createShaderProgram(passthroughFragmentShader)

                cameraToRenderShaderProgram = when (filterOn) {
                    false -> passthroughShaderProgram
                    true -> createShaderProgram(createShader(GLES30.GL_FRAGMENT_SHADER,
                            PORTRAIT_FSHADER))
                }

                renderToPreviewShaderProgram = passthroughShaderProgram
                renderToEncodeShaderProgram = passthroughShaderProgram
            }
        }

        /** Creates the shader program used to copy data from one texture to another */
        private fun createShaderProgram(fragmentShader: Int): ShaderProgram {
            var shaderProgram = GLES30.glCreateProgram()
            checkGlError("glCreateProgram")

            GLES30.glAttachShader(shaderProgram, vertexShader)
            checkGlError("glAttachShader")
            GLES30.glAttachShader(shaderProgram, fragmentShader)
            checkGlError("glAttachShader")
            GLES30.glLinkProgram(shaderProgram)
            checkGlError("glLinkProgram")

            val linkStatus = intArrayOf(0)
            GLES30.glGetProgramiv(shaderProgram, GLES30.GL_LINK_STATUS, linkStatus, 0)
            checkGlError("glGetProgramiv")
            if (linkStatus[0] == 0) {
                val msg = "Could not link program: " + GLES30.glGetProgramInfoLog(shaderProgram)
                GLES30.glDeleteProgram(shaderProgram)
                throw RuntimeException(msg)
            }

            var vPositionLoc = GLES30.glGetAttribLocation(shaderProgram, "vPosition")
            checkGlError("glGetAttribLocation")
            var texMatrixLoc = GLES30.glGetUniformLocation(shaderProgram, "texMatrix")
            checkGlError("glGetUniformLocation")

            return ShaderProgram(shaderProgram, vPositionLoc, texMatrixLoc)
        }

        /** Create a shader given its type and source string */
        private fun createShader(type: Int, source: String): Int {
            var shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            checkGlError("glShaderSource")
            GLES30.glCompileShader(shader)
            checkGlError("glCompileShader")
            val compiled = intArrayOf(0)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            checkGlError("glGetShaderiv")
            if (compiled[0] == 0) {
                val msg = "Could not compile shader " + type + ": " + GLES30.glGetShaderInfoLog(shader)
                GLES30.glDeleteShader(shader)
                throw RuntimeException(msg)
            }
            return shader
        }

        private fun createTexId(): Int {
            val buffer = IntBuffer.allocate(1)
            GLES30.glGenTextures(1, buffer)
            return buffer.get(0)
        }

        private fun destroyTexId(id: Int) {
            val buffer = IntBuffer.allocate(1)
            buffer.put(0, id)
            GLES30.glDeleteTextures(1, buffer)
        }

        private fun createFboId(): Int {
            val buffer = IntBuffer.allocate(1)
            GLES30.glGenFramebuffers(1, buffer)
            return buffer.get(0)
        }

        private fun destroyFboId(id: Int) {
            val buffer = IntBuffer.allocate(1)
            buffer.put(0, id)
            GLES30.glDeleteFramebuffers(1, buffer)
        }

        /** Create an OpenGL texture */
        private fun createTexture(): Int {
            /* Check that EGL has been initialized. */
            if (eglDisplay == null) {
                throw IllegalStateException("EGL not initialized before call to createTexture()");
            }

            val texId = createTexId()
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE)
            return texId
        }

        private fun destroyWindowSurface() {
            if (eglWindowSurface != EGL_NO_SURFACE && eglDisplay != EGL_NO_DISPLAY) {
                EGL14.eglDestroySurface(eglDisplay, eglWindowSurface)
            }
            eglWindowSurface = EGL14.EGL_NO_SURFACE
            cvDestroyWindowSurface.open()
        }

        public fun waitDestroyWindowSurface() {
            cvDestroyWindowSurface.block()
        }

        private fun copyTexture(texId: Int, texture: SurfaceTexture, viewportRect: Rect,
                shaderProgram: ShaderProgram, outputIsFramebuffer: Boolean) {
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            checkGlError("glClearColor")
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            checkGlError("glClear")

            shaderProgram.useProgram()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            checkGlError("glActiveTexture")
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
            checkGlError("glBindTexture")

            texture.getTransformMatrix(texMatrix)

            // HardwareBuffer coordinates are flipped relative to what GLES expects
            if (outputIsFramebuffer) {
                val flipMatrix = floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, -1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 1f, 0f, 1f
                )
                android.opengl.Matrix.multiplyMM(texMatrix, 0, flipMatrix, 0, texMatrix.clone(), 0)
            }
            shaderProgram.setTexMatrix(texMatrix)

            shaderProgram.setVertexAttribArray(FULLSCREEN_QUAD)

            GLES30.glViewport(viewportRect.left, viewportRect.top, viewportRect.right,
                    viewportRect.bottom)
            checkGlError("glViewport")
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            checkGlError("glDrawArrays")
        }

        private fun copyCameraToRender() {
            EGL14.eglMakeCurrent(eglDisplay, eglRenderSurface, eglRenderSurface, eglContext)

            copyTexture(cameraTexId, cameraTexture, Rect(0, 0, width, height),
                cameraToRenderShaderProgram!!, false)

            EGL14.eglSwapBuffers(eglDisplay, eglRenderSurface)
            renderTexture.updateTexImage()
        }

        private fun copyRenderToPreview() {

            var hardwareBuffer: HardwareBuffer? = null
            var eglImage: EGLImageKHR? = null
            if (transfer == TransferFragment.HLG_WORKAROUND_ID) {
                EGL14.eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, eglContext)

                // TODO: use GLFrameBufferRenderer to optimize the performance
                // Note that pooling and reusing HardwareBuffers will have significantly better
                // memory utilization so the HardwareBuffers do not have to be allocated every frame
                hardwareBuffer = HardwareBuffer.create(
                        previewSize.width, previewSize.height,
                        HardwareBuffer.RGBA_1010102, 1,
                        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                                or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                                or HardwareBuffer.USAGE_COMPOSER_OVERLAY)

                // If we're sending output buffers to a SurfaceControl we cannot render to an
                // EGLSurface. We need to render to a HardwareBuffer instead by importing the
                // HardwareBuffer into EGL, associating it with a texture, and framebuffer, and
                // drawing directly into the HardwareBuffer.
                eglImage = androidx.opengl.EGLExt.eglCreateImageFromHardwareBuffer(
                    eglDisplay, hardwareBuffer)
                checkGlError("eglCreateImageFromHardwareBuffer")

                GLES30.glBindTexture(GL_TEXTURE_2D, windowTexId)
                checkGlError("glBindTexture")
                androidx.opengl.EGLExt.glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImage!!)
                checkGlError("glEGLImageTargetTexture2DOES")

                GLES30.glBindFramebuffer(GL_FRAMEBUFFER, windowFboId);
                checkGlError("glBindFramebuffer")
                GLES30.glFramebufferTexture2D(
                    GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, windowTexId, 0);
                checkGlError("glFramebufferTexture2D")
            } else {
                EGL14.eglMakeCurrent(eglDisplay, eglWindowSurface, eglRenderSurface, eglContext)
            }

            val cameraAspectRatio = width.toFloat() / height.toFloat()
            val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()
            var viewportWidth = previewSize.width
            var viewportHeight = previewSize.height
            var viewportX = 0
            var viewportY = 0

            /** The camera display is not the same size as the video. Letterbox the preview so that
             * we can see exactly how the video will turn out. */
            if (previewAspectRatio < cameraAspectRatio) {
                /** Avoid vertical stretching */
                viewportHeight = ((viewportHeight.toFloat() / previewAspectRatio) * cameraAspectRatio).toInt()
                viewportY = (previewSize.height - viewportHeight) / 2
            } else {
                /** Avoid horizontal stretching */
                viewportWidth = ((viewportWidth.toFloat() / cameraAspectRatio) * previewAspectRatio).toInt()
                viewportX = (previewSize.width - viewportWidth) / 2
            }

            copyTexture(renderTexId, renderTexture,
                        Rect(viewportX, viewportY, viewportWidth, viewportHeight),
                        renderToPreviewShaderProgram!!, hardwareBuffer != null)

            if (hardwareBuffer != null) {
                if (contentSurfaceControl == null) {
                    throw RuntimeException("Forgot to set up SurfaceControl for HLG preview!")
                }

                // When rendering to HLG, send each camera frame to the display and communicate the
                // HLG colorspace here.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val fence = createSyncFence()
                    if (fence == null) {
                        glFinish()
                        checkGlError("glFinish")
                    }
                    SurfaceControl.Transaction()
                            .setBuffer(
                                    contentSurfaceControl!!,
                                    hardwareBuffer,
                                    fence)
                            .setDataSpace(
                                    contentSurfaceControl!!,
                                    DataSpace.pack(
                                        DataSpace.STANDARD_BT2020,
                                        DataSpace.TRANSFER_HLG,
                                        DataSpace.RANGE_FULL
                                    ))
                            .apply()
                    hardwareBuffer.close()
                }
            } else {
                EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)
            }

            if (eglImage != null) {
                GLES30.glBindFramebuffer(GL_FRAMEBUFFER, 0);
                androidx.opengl.EGLExt.eglDestroyImageKHR(eglDisplay, eglImage)
            }

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
                    renderToEncodeShaderProgram!!, false)

            encoder.frameAvailable()

            EGL14.eglSwapBuffers(eglDisplay, eglEncoderSurface)
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun createSyncFence() : SyncFence? {
            if (!supportsNativeFences) {
                return null
            }

            val eglSync = EGL15.eglCreateSync(
                    eglDisplay, androidx.opengl.EGLExt.EGL_SYNC_NATIVE_FENCE_ANDROID,
                    longArrayOf(EGL14.EGL_NONE.toLong()), 0)
            checkGlError("eglCreateSync")
            glFlush()
            checkGlError("glFlush")
            return eglSync?.let {
                val fence = EGLExt.eglDupNativeFenceFDANDROID(eglDisplay, it)
                checkGlError("eglDupNativeFenceFDANDROID")
                fence
            }
        }

        private fun actionDown(encoderSurface: Surface) {
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglEncoderSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig,
                    encoderSurface, surfaceAttribs, 0)
            if (eglEncoderSurface == EGL_NO_SURFACE) {
                val error = EGL14.eglGetError()
                throw RuntimeException("Failed to create EGL encoder surface"
                        + ": eglGetError = 0x" + Integer.toHexString(error))
            }
        }

        private fun clearFrameListener() {
            cameraTexture.setOnFrameAvailableListener(null)
            cvClearFrameListener.open()
        }

        public fun waitClearFrameListener() {
            cvClearFrameListener.block()
        }

        private fun cleanup() {
            EGL14.eglDestroySurface(eglDisplay, eglEncoderSurface)
            eglEncoderSurface = EGL_NO_SURFACE
            EGL14.eglDestroySurface(eglDisplay, eglRenderSurface)
            eglRenderSurface = EGL_NO_SURFACE

            cameraTexture.release()

            if (windowTexId > 0) {
                destroyTexId(windowTexId)
            }

            if (windowFboId > 0) {
                destroyFboId(windowFboId)
            }

            EGL14.eglDestroyContext(eglDisplay, eglContext)

            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT

            cvCleanup.open()
        }

        public fun waitCleanup() {
            cvCleanup.block()
        }

        @Suppress("UNUSED_PARAMETER")
        private fun onFrameAvailableImpl(surfaceTexture: SurfaceTexture) {
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                return
            }

            /** The camera API does not update the tex image. Do so here. */
            cameraTexture.updateTexImage()

            /** Copy from the camera texture to the render texture */
            if (eglRenderSurface != EGL_NO_SURFACE) {
                copyCameraToRender()
            }

            /** Copy from the render texture to the TextureView */
            copyRenderToPreview()

            /** Copy to the encoder surface if we're currently recording. */
            if (eglEncoderSurface != EGL_NO_SURFACE && currentlyRecording) {
                copyRenderToEncode()
            }
        }

        private fun isHDR(): Boolean {
            return dynamicRange != DynamicRangeProfiles.STANDARD
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
            val error = GLES30.glGetError()
            if (error != GLES30.GL_NO_ERROR) {
                val msg = op + ": glError 0x" + Integer.toHexString(error)
                Log.e(TAG, msg)
                throw RuntimeException(msg)
            }
        }

        private fun checkEglError(op: String) {
            val eglError = EGL14.eglGetError()
            if (eglError != EGL14.EGL_SUCCESS) {
                val msg = op + ": eglError 0x" + Integer.toHexString(eglError)
                Log.e(TAG, msg)
                throw RuntimeException(msg);
            }
        }
    }

}
