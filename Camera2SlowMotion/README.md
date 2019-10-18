
Android Camera2SlowMotion Sample
================================

This sample captures high-speed video via the Camera2 API including displaying
a camera preview and capturing a high-speed (slow motion) video using
repeating burst capture requests.

Introduction
------------

The [Camera2 API][1] allows users to capture [high-speed video][2], i.e. slow
motion, by sending repeating burst capture requests to the camera framework.

This sample displays a live camera preview in SurfaceView, allows the user to
press and hold the screen to record a video, and also encodes the recording
in an MP4 video file.

[1]: https://developer.android.com/reference/android/hardware/camera2/package-summary.html
[2]: https://developer.android.com/reference/android/hardware/camera2/CameraMetadata.html#REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO

Pre-requisites
--------------

- Android SDK 29+
- Android Studio 3.5+
- Device with high-speed capture capability

Screenshots
-------------

<img src="screenshots/main.png" height="400" alt="Screenshot"/>
<img src="screenshots/video.gif" height="400" alt="Video"/>

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
