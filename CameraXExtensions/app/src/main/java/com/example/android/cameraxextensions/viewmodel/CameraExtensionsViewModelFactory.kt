/*
 * Copyright 2022 The Android Open Source Project
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

package com.example.android.cameraxextensions.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.android.cameraxextensions.repository.ImageCaptureRepository

/**
 * Creates ViewModel instances of [CameraExtensionsViewModel] to support injection of [Application]
 * and [ImageCaptureRepository]
 */
class CameraExtensionsViewModelFactory(
    private val application: Application,
    private val imageCaptureRepository: ImageCaptureRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CameraExtensionsViewModel(application, imageCaptureRepository) as T
    }
}
