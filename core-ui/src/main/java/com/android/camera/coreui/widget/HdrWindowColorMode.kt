/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.android.camera.coreui.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Drives the host activity's window color mode from [enabled]: switches the window to
 * [ActivityInfo.COLOR_MODE_HDR] while enabled (so an HDR-capable display lifts the highlights of 10-bit
 * HDR content — e.g. an Ultra HDR still or an HDR video viewfinder), and restores
 * [ActivityInfo.COLOR_MODE_DEFAULT] when disabled or when this composable leaves composition.
 *
 * `Window.colorMode` is API 26+, so this is a no-op below that. It only has a visible effect on devices
 * with an HDR-capable display showing genuine HDR content; on SDR displays it is harmless.
 */
@Composable
fun HdrWindowColorMode(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    LaunchedEffect(enabled, activity, supported) {
        if (activity != null && supported) {
            activity.window.colorMode =
                if (enabled) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        }
    }
    DisposableEffect(activity, supported) {
        onDispose {
            if (activity != null && supported) {
                activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
