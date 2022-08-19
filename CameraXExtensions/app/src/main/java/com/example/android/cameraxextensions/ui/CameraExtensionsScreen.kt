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

package com.example.android.cameraxextensions.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.android.cameraxextensions.R
import com.example.android.cameraxextensions.adapter.CameraExtensionItem
import com.example.android.cameraxextensions.adapter.CameraExtensionsSelectorAdapter
import com.example.android.cameraxextensions.model.CameraUiAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Displays the camera preview and captured photo.
 * Encapsulates the details of how the screen is constructed and exposes a set of explicit
 * operations clients can perform on the screen.
 */
class CameraExtensionsScreen(private val root: View) {
    private val context: Context = root.context

    private val cameraShutterButton: View = root.findViewById(R.id.cameraShutter)
    private val photoPreview: ImageView = root.findViewById(R.id.photoPreview)
    private val closePhotoPreview: View = root.findViewById(R.id.closePhotoPreview)
    private val switchLensButton = root.findViewById<ImageView>(R.id.switchLens)
    private val extensionSelector: RecyclerView = root.findViewById(R.id.extensionSelector)
    private val extensionsAdapter: CameraExtensionsSelectorAdapter
    private val permissionsRationaleContainer: View =
        root.findViewById(R.id.permissionsRationaleContainer)
    private val permissionsRationale: TextView = root.findViewById(R.id.permissionsRationale)
    private val permissionsRequestButton: TextView =
        root.findViewById(R.id.permissionsRequestButton)

    val previewView: PreviewView = root.findViewById(R.id.previewView)

    private val _action: MutableSharedFlow<CameraUiAction> = MutableSharedFlow()
    val action: Flow<CameraUiAction> = _action

    init {
        val snapHelper = CenterItemSnapHelper()

        extensionsAdapter = CameraExtensionsSelectorAdapter { view -> onItemClick(view) }
        extensionSelector.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = extensionsAdapter
            addItemDecoration(OffsetCenterItemDecoration())
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var snapPosition = RecyclerView.NO_POSITION

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val layoutManager = recyclerView.layoutManager ?: return
                        val snapView = snapHelper.findSnapView(layoutManager) ?: return
                        val newSnapPosition = layoutManager.getPosition(snapView)
                        onItemSelected(snapPosition, newSnapPosition)
                        snapPosition = newSnapPosition
                    }
                }

                private fun onItemSelected(oldPosition: Int, newPosition: Int) {
                    if (oldPosition == newPosition) return
                    selectItem(newPosition)
                    extensionsAdapter.currentList[newPosition]
                        ?.let {
                            root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                                _action.emit(CameraUiAction.SelectCameraExtension(it.extensionMode))
                            }
                        }
                }

                private fun selectItem(position: Int) {
                    val data =
                        extensionsAdapter.currentList.mapIndexed { index, cameraExtensionModel ->
                            cameraExtensionModel.copy(selected = position == index)
                        }
                    extensionsAdapter.submitList(data)
                }
            })
        }

        snapHelper.attachToRecyclerView(extensionSelector)

        cameraShutterButton.setOnClickListener {
            root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                _action.emit(CameraUiAction.ShutterButtonClick)
            }
        }

        switchLensButton.setOnClickListener {
            root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                _action.emit(CameraUiAction.SwitchCameraClick)
            }
            it.animate().apply {
                rotation(180f)
                duration = 300L
                setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        it.rotation = 0f
                    }
                })
                start()
            }
        }

        closePhotoPreview.setOnClickListener {
            root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                _action.emit(CameraUiAction.ClosePhotoPreviewClick)
            }
        }

        permissionsRequestButton.setOnClickListener {
            root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                _action.emit(CameraUiAction.RequestPermissionClick)
            }
        }
    }

    fun setAvailableExtensions(extensions: List<CameraExtensionItem>) {
        extensionsAdapter.submitList(extensions)
    }

    fun showPhoto(uri: Uri?) {
        if (uri == null) return
        photoPreview.isVisible = true
        photoPreview.load(uri)
        closePhotoPreview.isVisible = true
    }

    fun hidePhoto() {
        photoPreview.isVisible = false
        closePhotoPreview.isVisible = false
        extensionSelector.isVisible = false
    }

    fun showCameraControls() {
        cameraShutterButton.isVisible = true
        switchLensButton.isVisible = true
        extensionSelector.isVisible = true
    }

    fun hideCameraControls() {
        cameraShutterButton.isVisible = false
        switchLensButton.isVisible = false
    }

    fun enableCameraShutter(isEnabled: Boolean) {
        cameraShutterButton.isEnabled = isEnabled
    }

    fun enableSwitchLens(isEnabled: Boolean) {
        switchLensButton.isEnabled = isEnabled
    }

    fun showCaptureError(errorMessage: String) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
    }

    fun hidePermissionsRequest() {
        permissionsRationaleContainer.isVisible = false
    }

    fun showPermissionsRequest(shouldShowRationale: Boolean) {
        permissionsRationaleContainer.isVisible = true
        if (shouldShowRationale) {
            permissionsRationale.text =
                context.getString(R.string.camera_permissions_request_with_rationale)
        } else {
            permissionsRationale.text = context.getString(R.string.camera_permissions_request)
        }
    }

    private fun onItemClick(view: View) {
        val layoutManager = extensionSelector.layoutManager as? LinearLayoutManager ?: return
        val viewMiddle = view.left + view.width / 2
        val middle = layoutManager.width / 2
        val dx = viewMiddle - middle
        extensionSelector.smoothScrollBy(dx, 0)
    }
}