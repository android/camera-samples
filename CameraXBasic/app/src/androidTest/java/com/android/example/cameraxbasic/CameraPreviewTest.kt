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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * @see "https://developer.android.com/training/camerax/architecture.combine-use-cases"
 */
@RunWith(AndroidJUnit4::class)
class CameraPreviewTest : LifecycleOwner, ImageReader.OnImageAvailableListener, Consumer<SurfaceRequest.Result> {

    @get:Rule
    val cameraAccess = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private var registry: LifecycleRegistry? = null
    private val thread = HandlerThread("CameraPreviewTest").also { it.start() }
    private var executor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null // requires main thread

    /**
     * @implNote We can't use the main executor since it is reserved for the test framework.
     */
    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        Assert.assertNotNull(context)
        provider = ProcessCameraProvider.getInstance(context).get()
        Assert.assertNotNull(provider)
    }

    @UiThreadTest
    @After
    fun teardown() {
        provider?.unbindAll()
        executor?.shutdown()
    }

    /**
     * @implNote In checkPreviewUseCase, ImageReader will provide a Surface for camera preview test.
     *  When each ImageProxy is acquired, the AtomicInteger will be incremented.
     *  By doing so we can ensure the camera binding is working as expected.
     */
    private val reader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 30)
    private val count = AtomicInteger(0)

    @Before
    fun setupImageReader() {
        reader.setOnImageAvailableListener(this, Handler(thread.looper))
    }

    @After
    fun teardownImageReader() {
        reader.close()
        thread.quit()
    }

    override fun onImageAvailable(reader: ImageReader) {
        reader.acquireNextImage().use { image ->
            val imageNumber = count.getAndIncrement()
            Log.i("CameraPreviewTest", String.format("image: %d %s", imageNumber, image))
        }
    }

    /**
     * @see ProcessCameraProvider.bindToLifecycle
     */
    override fun getLifecycle() = registry!!

    @Before
    fun markCreated() {
        registry = LifecycleRegistry(this).also{
            it.markState(Lifecycle.State.INITIALIZED)
            it.markState(Lifecycle.State.CREATED)
        }
    }

    @After
    fun markDestroyed() {
        registry?.markState(Lifecycle.State.DESTROYED)
    }

    /**
     * @see SurfaceRequest.provideSurface
     */
    override fun accept(result: SurfaceRequest.Result) {
        when (result.resultCode) {
            SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY -> {
                Log.i("CameraPreviewTest", result.toString())
            }
            SurfaceRequest.Result.RESULT_REQUEST_CANCELLED, SurfaceRequest.Result.RESULT_INVALID_SURFACE, SurfaceRequest.Result.RESULT_SURFACE_ALREADY_PROVIDED, SurfaceRequest.Result.RESULT_WILL_NOT_PROVIDE_SURFACE -> {
                Log.e("CameraPreviewTest", result.toString())
            }
        }
    }


    @UiThreadTest
    @Test
    fun checkPreviewUseCase() {
        // life cycle owner
        registry?.markState(Lifecycle.State.STARTED)

        // select Back camera
        val selectorBuilder = CameraSelector.Builder()
        Assert.assertTrue(provider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
        selectorBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK)

        // fit the preview size to ImageReader
        val previewBuilder = Preview.Builder()
        previewBuilder.setTargetResolution(Size(reader.width, reader.height))
        previewBuilder.setTargetRotation(Surface.ROTATION_90)
        val preview = previewBuilder.build()

        // acquire camera binding
        provider!!.unbindAll()
        val camera = provider!!.bindToLifecycle(this, selectorBuilder.build(), preview)
        Assert.assertNotNull(camera)
        preview.setSurfaceProvider(executor!!, SurfaceProvider { request: SurfaceRequest ->
            val surface = reader.surface
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
