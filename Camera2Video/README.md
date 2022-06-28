
Android Camera2Video Sample
===========================

This sample captures video record via the Camera2 API including displaying
a camera preview and capturing a high-speed (slow motion) video using
repeating capture requests.

Introduction
------------

The [Camera2 API][1] allows users to capture video from the camera by
sending repeating capture requests from the camera framework to a
[media recorder][2].

This sample displays a live camera preview, allows the user to
press and hold the screen to record a video, and also encodes the recording
in an MP4 video file. The user has the following choices:

1) The dimensions and frame rate of the video
2) Which camera to use
3) Whether to capture in HDR format or SDR format
4) Whether to capture using two streams directly to a SurfaceView, or one stream to an EGL pipeline
5) Whether to apply a portrait filter in TextureView mode
6) Whether to apply preview stabilization
7) In single-stream mode with HDR, whether to use linear or PQ preview

The choice to use HDR will be presented if the capability is detected on the host device.

[1]: https://developer.android.com/reference/android/hardware/camera2/package-summary.html
[2]: https://developer.android.com/reference/android/media/MediaRecorder

Pre-requisites
--------------

- Android SDK 33+
- Android Studio 3.6+
- Device with video capture capability (or emulator)

Screenshots
-------------

<img src="screenshots/main.png" height="400" alt="Screenshot"/>

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/camera-samples

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.
