/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.hdrviewfinder;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.util.List;

/**
 * Simple interface for operating the camera, with major camera operations
 * all performed on a background handler thread.
 */
public class CameraOps {

    private static final String TAG = "CameraOps";

    public static final long CAMERA_CLOSE_TIMEOUT = 2000; // ms

    private final CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraSession;
    private List<Surface> mSurfaces;

    private final ConditionVariable mCloseWaiter = new ConditionVariable();

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;

    private final ErrorDisplayer mErrorDisplayer;

    private final CameraReadyListener mReadyListener;
    private final Handler mReadyHandler;

    /**
     * Create a new camera ops thread.
     *
     * @param errorDisplayer listener for displaying error messages
     * @param readyListener  listener for notifying when camera is ready for requests
     * @param readyHandler   the handler for calling readyListener methods on
     */
    CameraOps(CameraManager manager, ErrorDisplayer errorDisplayer,
              CameraReadyListener readyListener, Handler readyHandler) {
        mCameraThread = new HandlerThread("CameraOpsThread");
        mCameraThread.start();

        if (manager == null || errorDisplayer == null ||
                readyListener == null || readyHandler == null) {
            throw new IllegalArgumentException("Need valid displayer, listener, handler");
        }

        mCameraManager = manager;
        mErrorDisplayer = errorDisplayer;
        mReadyListener = readyListener;
        mReadyHandler = readyHandler;
    }

    /**
     * Open the first back-facing camera listed by the camera manager.
     * Displays a dialog if it cannot open a camera.
     */
    public void openCamera(final String cameraId) {
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCameraHandler.post(new Runnable() {
            @SuppressWarnings("MissingPermission")
            public void run() {
                if (mCameraDevice != null) {
                    throw new IllegalStateException("Camera already open");
                }
                try {
                    mCameraManager.openCamera(cameraId, mCameraDeviceListener, mCameraHandler);
                } catch (CameraAccessException e) {
                    String errorMessage = mErrorDisplayer.getErrorString(e);
                    mErrorDisplayer.showErrorDialog(errorMessage);
                }
            }
        });
    }

    /**
     * Close the camera and wait for the close callback to be called in the camera thread.
     * Times out after @{value CAMERA_CLOSE_TIMEOUT} ms.
     */
    public void closeCameraAndWait() {
        mCloseWaiter.close();
        mCameraHandler.post(mCloseCameraRunnable);
        boolean closed = mCloseWaiter.block(CAMERA_CLOSE_TIMEOUT);
        if (!closed) {
            Log.e(TAG, "Timeout closing camera");
        }
    }

    private Runnable mCloseCameraRunnable = new Runnable() {
        public void run() {
            if (mCameraDevice != null) {
                mCameraDevice.close();
            }
            mCameraDevice = null;
            mCameraSession = null;
            mSurfaces = null;
        }
    };

    /**
     * Set the output Surfaces, and finish configuration if otherwise ready.
     */
    public void setSurfaces(final List<Surface> surfaces) {
        mCameraHandler.post(new Runnable() {
            public void run() {
                mSurfaces = surfaces;
                startCameraSession();
            }
        });
    }

    /**
     * Get a request builder for the current camera.
     */
    public CaptureRequest.Builder createCaptureRequest(int template) throws CameraAccessException {
        CameraDevice device = mCameraDevice;
        if (device == null) {
            throw new IllegalStateException("Can't get requests when no camera is open");
        }
        return device.createCaptureRequest(template);
    }

    /**
     * Set a repeating request.
     */
    public void setRepeatingRequest(final CaptureRequest request,
                                    final CameraCaptureSession.CaptureCallback listener,
                                    final Handler handler) {
        mCameraHandler.post(new Runnable() {
            public void run() {
                try {
                    mCameraSession.setRepeatingRequest(request, listener, handler);
                } catch (CameraAccessException e) {
                    String errorMessage = mErrorDisplayer.getErrorString(e);
                    mErrorDisplayer.showErrorDialog(errorMessage);
                }
            }
        });
    }

    /**
     * Set a repeating request.
     */
    public void setRepeatingBurst(final List<CaptureRequest> requests,
                                  final CameraCaptureSession.CaptureCallback listener,
                                  final Handler handler) {
        mCameraHandler.post(new Runnable() {
            public void run() {
                try {
                    mCameraSession.setRepeatingBurst(requests, listener, handler);
                } catch (CameraAccessException e) {
                    String errorMessage = mErrorDisplayer.getErrorString(e);
                    mErrorDisplayer.showErrorDialog(errorMessage);
                }
            }
        });
    }

    /**
     * Configure the camera session.
     */
    private void startCameraSession() {
        // Wait until both the camera device is open and the SurfaceView is ready
        if (mCameraDevice == null || mSurfaces == null) return;

        try {
            mCameraDevice.createCaptureSession(
                    mSurfaces, mCameraSessionListener, mCameraHandler);
        } catch (CameraAccessException e) {
            String errorMessage = mErrorDisplayer.getErrorString(e);
            mErrorDisplayer.showErrorDialog(errorMessage);
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    /**
     * Main listener for camera session events
     * Invoked on mCameraThread
     */
    private CameraCaptureSession.StateCallback mCameraSessionListener =
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraSession = session;
                    mReadyHandler.post(new Runnable() {
                        public void run() {
                            // This can happen when the screen is turned off and turned back on.
                            if (null == mCameraDevice) {
                                return;
                            }

                            mReadyListener.onCameraReady();
                        }
                    });

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    mErrorDisplayer.showErrorDialog("Unable to configure the capture session");
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            };

    /**
     * Main listener for camera device events.
     * Invoked on mCameraThread
     */
    private CameraDevice.StateCallback mCameraDeviceListener = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startCameraSession();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            mCloseWaiter.open();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mErrorDisplayer.showErrorDialog("The camera device has been disconnected.");
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mErrorDisplayer.showErrorDialog("The camera encountered an error:" + error);
            camera.close();
            mCameraDevice = null;
        }

    };

    /**
     * Simple listener for main code to know the camera is ready for requests, or failed to
     * start.
     */
    public interface CameraReadyListener {
        void onCameraReady();
    }

    /**
     * Simple listener for displaying error messages
     */
    public interface ErrorDisplayer {
        void showErrorDialog(String errorMessage);

        String getErrorString(CameraAccessException e);
    }

}
