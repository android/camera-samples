package com.android.example.cameraxbasic

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.*
import org.junit.runner.RunWith
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.Throws

/**
 * @see "https://developer.android.com/training/camerax/architecture.combine-use-cases"
 */
@RunWith(AndroidJUnit4::class)
class CameraPreviewTest : LifecycleOwner, ImageReader.OnImageAvailableListener, Consumer<SurfaceRequest.Result> {

    @get:Rule
    val cameraAccess = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private var registry: LifecycleRegistry? = null
    private var context: Context? = null
    private var thread: HandlerThread? = null
    private var executor: ExecutorService? = null
    private var reader: ImageReader? = null
    private var camera: Camera? = null
    private var provider // requires main thread
            : ProcessCameraProvider? = null
    private var count = AtomicInteger(0)

    /**
     * @implNote We can't use the main executor since it is reserved for the test framework.
     */
    @Before
    @Throws(Exception::class)
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Assert.assertNotNull(context)
        executor = Executors.newSingleThreadExecutor()
        thread = HandlerThread("CameraPreviewTest")
        thread!!.start()
        val future = ProcessCameraProvider.getInstance(context!!)
        provider = future.get()
        Assert.assertNotNull(provider)
        reader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 30)
        val surface = reader!!.getSurface()
        Assert.assertNotNull(surface)
        reader!!.setOnImageAvailableListener(this, Handler(thread!!.looper))
    }

    @UiThreadTest
    @After
    @Throws(Exception::class)
    fun teardown() {
        if (provider != null) provider!!.unbindAll()
        if (reader != null) reader!!.close()
        if (thread != null) thread!!.quit()
        if (executor != null) executor!!.shutdown()
    }

    override fun getLifecycle(): Lifecycle {
        return registry!!
    }

    @Before
    fun markCreated() {
        registry = LifecycleRegistry(this)
        registry!!.markState(Lifecycle.State.INITIALIZED)
        registry!!.markState(Lifecycle.State.CREATED)
    }

    @After
    fun markDestroyed() {
        registry!!.markState(Lifecycle.State.DESTROYED)
    }

    override fun accept(result: SurfaceRequest.Result) {
        when (result.resultCode) {
            SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> {
                Log.i("CameraPreviewTest", result.toString())
                return
            }
            SurfaceRequest.Result.RESULT_REQUEST_CANCELLED, SurfaceRequest.Result.RESULT_INVALID_SURFACE, SurfaceRequest.Result.RESULT_SURFACE_ALREADY_PROVIDED, SurfaceRequest.Result.RESULT_WILL_NOT_PROVIDE_SURFACE -> {
                Log.e("CameraPreviewTest", result.toString())
                return
            }
        }
    }

    override fun onImageAvailable(reader: ImageReader) {
        reader.acquireNextImage().use { image ->
            val value = count.getAndIncrement()
            Log.i("CameraPreviewTest", String.format("image: %d %s", value, image))
        }
    }

    @UiThreadTest
    @Test
    @Throws(Exception::class)
    fun checkPreviewUseCase() {
        // life cycle owner
        registry!!.markState(Lifecycle.State.STARTED)
        // selector
        val selectorBuilder = CameraSelector.Builder()
        Assert.assertTrue(provider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
        selectorBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)
        // usecase[]
        val previewBuilder = Preview.Builder()
        previewBuilder.setTargetResolution(Size(reader!!.width, reader!!.height))
        previewBuilder.setTargetRotation(Surface.ROTATION_90)
        val preview = previewBuilder.build()
        // acquire camera
        provider!!.unbindAll()
        camera = provider!!.bindToLifecycle(this, selectorBuilder.build(), preview)
        Assert.assertNotNull(camera)
        preview.setSurfaceProvider(executor!!, SurfaceProvider { request: SurfaceRequest ->
            val surface = reader!!.surface
            Log.i("CameraPreviewTest", String.format("providing: %s", surface))
            request.provideSurface(surface, executor!!, this)
        })

        // wait until onImageAvailable is invoked. retry several times
        for (repeat in 5 downTo 0) {
            Thread.sleep(600)
            val value = count.get()
            Log.i("CameraPreviewTest", String.format("count: %d", value))
            if (value > 0) return
        }
        Assert.assertNotEquals(0, count.get().toLong())
    }
}
