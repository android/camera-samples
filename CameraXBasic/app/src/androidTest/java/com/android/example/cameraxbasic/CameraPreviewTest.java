package com.android.example.cameraxbasic;

import android.Manifest;
import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.util.Consumer;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @see "https://developer.android.com/training/camerax/architecture#combine-use-cases"
 */
@RunWith(AndroidJUnit4.class)
public class CameraPreviewTest
        implements LifecycleOwner, ImageReader.OnImageAvailableListener, Consumer<SurfaceRequest.Result> {
    @Rule
    public GrantPermissionRule cameraAccess = GrantPermissionRule.grant(Manifest.permission.CAMERA);

    LifecycleRegistry registry;
    Context context;
    HandlerThread thread;
    ExecutorService executor;
    ImageReader reader;
    Camera camera;
    ProcessCameraProvider provider; // requires main thread
    AtomicInteger count = new AtomicInteger(0);

    /**
     * We can't use main executor since it is reserved for test framework.
     *
     * @todo find a way to merge HandlerThread and Executor
     */
    @Before
    public void setup() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        assertNotNull(context);

        executor = Executors.newSingleThreadExecutor();
        thread = new HandlerThread(this.getClass().getTypeName());
        thread.start();

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        provider = future.get();
        assertNotNull(provider);

        reader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 30);
        Surface surface = reader.getSurface();
        assertNotNull(surface);
        reader.setOnImageAvailableListener(this, new Handler(thread.getLooper()));
    }

    @UiThreadTest
    @After
    public void teardown() throws Exception {
        if (provider != null)
            provider.unbindAll();
        if (reader != null)
            reader.close();
        if (thread != null)
            thread.quit();
        if (executor != null)
            executor.shutdown();
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return registry;
    }

    @Before
    public void markCreated() {
        registry = new LifecycleRegistry(this);
        registry.markState(Lifecycle.State.INITIALIZED);
        registry.markState(Lifecycle.State.CREATED);
    }

    @After
    public void markDestroyed() {
        registry.markState(Lifecycle.State.DESTROYED);
    }

    @Override
    public void accept(SurfaceRequest.Result result) {
        switch (result.getResultCode()) {
            case SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY:
                Log.i("CameraPreviewTest", result.toString());
                return;
            case SurfaceRequest.Result.RESULT_REQUEST_CANCELLED:
            case SurfaceRequest.Result.RESULT_INVALID_SURFACE:
            case SurfaceRequest.Result.RESULT_SURFACE_ALREADY_PROVIDED:
            case SurfaceRequest.Result.RESULT_WILL_NOT_PROVIDE_SURFACE:
                Log.e("CameraPreviewTest", result.toString());
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        try (Image image = reader.acquireNextImage()) {
            final int value = count.getAndIncrement();
            Log.i("CameraPreviewTest", String.format("image %d %s", value, image));
        }
    }

    @UiThreadTest
    @Test
    public void checkPreviewUseCase() throws Exception {
        // life cycle owner
        registry.markState(Lifecycle.State.STARTED);
        // selector
        CameraSelector.Builder selectorBuilder = new CameraSelector.Builder();
        assertTrue(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA));
        selectorBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK);
        // usecase[]
        Preview.Builder previewBuilder = new Preview.Builder();
        previewBuilder.setTargetResolution(new Size(reader.getWidth(), reader.getHeight()));
        previewBuilder.setTargetRotation(Surface.ROTATION_90);
        Preview preview = previewBuilder.build();
        // acquire camera
        provider.unbindAll();
        camera = provider.bindToLifecycle(this, selectorBuilder.build(), preview);
        assertNotNull(camera);
        preview.setSurfaceProvider(executor, (SurfaceRequest request) -> {
            Surface surface = reader.getSurface();
            Log.i("CameraPreviewTest", String.format("providing: %s", surface));
            request.provideSurface(surface, executor, this);
        });

        // wait until onImageAvailable is invoked. retry several times
        for (int repeat = 5; repeat >= 0; --repeat) {
            Thread.sleep(600);
            final int value = count.get();
            Log.i("CameraPreviewTest", String.format("count: %d", value));
            if (value > 0)
                return;
        }
        assertNotEquals(0, count.get());
    }
}
