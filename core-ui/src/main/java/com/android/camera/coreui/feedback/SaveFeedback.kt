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
package com.android.camera.coreui.feedback

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.camera.coreui.R
import kotlinx.coroutines.flow.Flow

/**
 * A one-shot result emitted when a sample persists captured media to the shared gallery. Samples
 * surface this through [ObserveSaveEvents] so the user gets a toast confirming the save (or failure).
 */
sealed interface SaveEvent {
    data object Saved : SaveEvent

    data object Failed : SaveEvent
}

/**
 * Observes a [Flow] of [SaveEvent]s and shows a short [Toast] for each — confirming a save with
 * [savedText] or a failure with [failedText]. Wire this near the top of a capture screen so saving a
 * photo or video produces the reviewer-requested "saved to Photos" confirmation.
 */
@Composable
fun ObserveSaveEvents(
    events: Flow<SaveEvent>,
    savedText: String = stringResource(R.string.save_feedback_saved),
    failedText: String = stringResource(R.string.save_feedback_failed),
) {
    val context = LocalContext.current
    LaunchedEffect(events) {
        events.collect { event ->
            val message = if (event is SaveEvent.Saved) savedText else failedText
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
