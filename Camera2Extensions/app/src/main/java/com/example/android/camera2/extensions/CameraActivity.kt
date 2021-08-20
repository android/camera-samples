/*
 * Copyright (C) 2021 The Android Open Source Project
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


package com.example.android.camera2.extensions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/*
 * This is the launching point for the camera extension app where the camera fragment container
 * is delayed to allow the UI to settle. All of the camera extension code can be found in
 *  "CameraFragment".
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Before setting full screen flags, we must wait a bit to let UI settle; otherwise,
                // we may be trying to set app to immersive mode before it's ready and the flags do
                // not stick
                container.postDelayed({
                                          @Suppress("DEPRECATION")
                                          container.systemUiVisibility = FLAGS_FULLSCREEN
                                      },
                                      IMMERSIVE_FLAG_TIMEOUT)
            } else {
                Toast.makeText(this, R.string.permission_required,
                               Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        container = findViewById(R.id.fragment_container)
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            container.postDelayed({
                                      @Suppress("DEPRECATION")
                                      container.systemUiVisibility = FLAGS_FULLSCREEN
                                  },
                                  IMMERSIVE_FLAG_TIMEOUT)
        } else  {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    companion object {
        /** Combination of all flags required to put activity into immersive mode */
        @Suppress("DEPRECATION")
        const val FLAGS_FULLSCREEN =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        /** Milliseconds used for UI animations */
        private const val IMMERSIVE_FLAG_TIMEOUT = 500L
    }
}
