
Android Camera2Raw Sample
===================================

This sample demonstrates using the Camera2 API to capture a JPEG and RAW sensor frame.
Check the source code to see a typical example of how to display the camera preview;
run auto-focus, auto-exposure metering, and auto-white-balance; capture a JPEG and
RAW image for the same sensor frame; and save these into MediaStore for use in other
applications.

Introduction
------------

The [Camera2 API][1] allows users to capture RAW images, i.e. unprocessed pixel data
directly from the camera sensor that has not yet been converted into a format and
colorspace typically used for displaying and storing images viewed by humans.  The
[DngCreator][2] class is provided as part of the Camera2 API as a utility for saving
RAW images as DNG files.

This sample displays a live camera preview in a TextureView, and saves JPEG and DNG
file for each image captured.

[1]: https://developer.android.com/reference/android/hardware/camera2/package-summary.html
[2]: https://developer.android.com/reference/android/hardware/camera2/DngCreator.html

Pre-requisites
--------------

- Android SDK 28
- Android Build Tools v28.0.3
- Android Support Repository

Screenshots
-------------

<img src="screenshots/main.png" height="400" alt="Screenshot"/> 

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Google+ Community: https://plus.google.com/communities/105153134372062985968
- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/googlesamples/android-Camera2Raw

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.
