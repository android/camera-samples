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
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.android.cameraxextensions.R
import com.example.android.cameraxextensions.adapter.CameraExtensionsSelectorAdapter
import com.example.android.cameraxextensions.model.CameraUiAction
import com.example.android.cameraxextensions.viewstate.CameraPreviewScreenViewState
import com.example.android.cameraxextensions.viewstate.CaptureScreenViewState
import com.example.android.cameraxextensions.viewstate.PostCaptureScreenViewState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Displays the camera preview and captured photo.
 * Encapsulates the details of how the screen is constructed and exposes a set of explicit
 * operations clients can perform on the screen.
 */
@SuppressLint("ClickableViewAccessibility")
class CameraExtensionsScreen(private val root: View) {

    private companion object {
        // animation constants for focus point
        private const val SPRING_STIFFNESS_ALPHA_OUT = 100f
        private const val SPRING_STIFFNESS = 800f
        private const val SPRING_DAMPING_RATIO = 0.35f
    }

    private val context: Context = root.context

    private val cameraShutterButton: View = root.findViewById(R.id.cameraShutter)
    private val photoPreview: ImageView = root.findViewById(R.id.photoPreview)
    private val closePhotoPreview: View = root.findViewById(R.id.closePhotoPreview)
    private val switchLensButton = root.findViewById<ImageView>(R.id.switchLens)
    private val extensionSelector: RecyclerView = root.findViewById(R.id.extensionSelector)
    private val extensionsAdapter: CameraExtensionsSelectorAdapter
    private val focusPointView: View = root.findViewById(R.id.focusPoint)
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
            switchLens(root, it)
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

        val gestureDetector = GestureDetectorCompat(context, object : SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val meteringPointFactory = previewView.meteringPointFactory
                val focusPoint = meteringPointFactory.createPoint(e.x, e.y)
                root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    _action.emit(CameraUiAction.Focus(focusPoint))
                }
                showFocusPoint(focusPointView, e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                switchLens(root, switchLensButton)
                return true
            }
        })

        val scaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                        _action.emit(CameraUiAction.Scale(detector.scaleFactor))
                    }
                    return true
                }
            })

        previewView.setOnTouchListener { _, event ->
            var didConsume = scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                didConsume = gestureDetector.onTouchEvent(event)
            }
            didConsume
        }
    }

    fun setCaptureScreenViewState(state: CaptureScreenViewState) {
        setCameraScreenViewState(state.cameraPreviewScreenViewState)
        when (state.postCaptureScreenViewState) {
            PostCaptureScreenViewState.PostCaptureScreenHiddenViewState -> hidePhoto()
            is PostCaptureScreenViewState.PostCaptureScreenVisibleViewState -> showPhoto(state.postCaptureScreenViewState.uri)
        }
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

    private fun showPhoto(uri: Uri?) {
        if (uri == null) return
        photoPreview.isVisible = true
        photoPreview.load(uri)
        closePhotoPreview.isVisible = true
    }

    private fun hidePhoto() {
        photoPreview.isVisible = false
        closePhotoPreview.isVisible = false
    }

    private fun setCameraScreenViewState(state: CameraPreviewScreenViewState) {
        cameraShutterButton.isEnabled = state.shutterButtonViewState.isEnabled
        cameraShutterButton.isVisible = state.shutterButtonViewState.isVisible

        switchLensButton.isEnabled = state.switchLensButtonViewState.isEnabled
        switchLensButton.isVisible = state.switchLensButtonViewState.isVisible

        extensionSelector.isVisible = state.extensionsSelectorViewState.isVisible
        extensionsAdapter.submitList(state.extensionsSelectorViewState.extensions)
    }

    private fun onItemClick(view: View) {
        val layoutManager = extensionSelector.layoutManager as? LinearLayoutManager ?: return
        val viewMiddle = view.left + view.width / 2
        val middle = layoutManager.width / 2
        val dx = viewMiddle - middle
        extensionSelector.smoothScrollBy(dx, 0)
    }

    private fun switchLens(root: View, switchLensButton: View) {
        root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            _action.emit(CameraUiAction.SwitchCameraClick)
        }
        switchLensButton.animate().apply {
            rotation(180f)
            duration = 300L
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    switchLensButton.rotation = 0f
                }
            })
            start()
        }
    }

    private fun showFocusPoint(view: View, x: Float, y: Float) {
        val drawable = FocusPointDrawable()
        val strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        )
        drawable.setStrokeWidth(strokeWidth)

        val alphaAnimation = SpringAnimation(view, DynamicAnimation.ALPHA, 1f).apply {
            spring.stiffness = SPRING_STIFFNESS
            spring.dampingRatio = SPRING_DAMPING_RATIO

            addEndListener { _, _, _, _ ->
                SpringAnimation(view, DynamicAnimation.ALPHA, 0f)
                    .apply {
                        spring.stiffness = SPRING_STIFFNESS_ALPHA_OUT
                        spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                    }
                    .start()
            }
        }
        val scaleAnimationX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SPRING_STIFFNESS
            spring.dampingRatio = SPRING_DAMPING_RATIO
        }
        val scaleAnimationY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SPRING_STIFFNESS
            spring.dampingRatio = SPRING_DAMPING_RATIO
        }

        view.apply {
            background = drawable
            isVisible = true
            translationX = x - width / 2f
            translationY = y - height / 2f
            alpha = 0f
            scaleX = 1.5f
            scaleY = 1.5f
        }

        alphaAnimation.start()
        scaleAnimationX.start()
        scaleAnimationY.start()
    }
}