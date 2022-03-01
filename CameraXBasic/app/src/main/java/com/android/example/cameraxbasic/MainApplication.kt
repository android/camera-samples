package com.android.example.cameraxbasic

import android.app.Application
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

/**
 * Set CameraX logging level to Log.ERROR to avoid excessive logcat messages.
 * Refer to https://developer.android.com/reference/androidx/camera/core/CameraXConfig.Builder#setMinimumLoggingLevel(int)
 * for details.
 */
class MainApplication : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR).build()
    }
}