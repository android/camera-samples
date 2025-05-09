/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.camerax_mlkit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LENS_FACING_BACK
import androidx.camera.core.ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camerax_mlkit.ui.theme.CameraxMLKitTheme
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
        setContent {
            CameraxMLKitTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(barcodeScanner)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (this::barcodeScanner.isInitialized) {
            barcodeScanner.close()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    companion object {
        const val TAG = "CameraX-MLKit"
        const val REQUEST_CODE_PERMISSIONS = 10
        val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).toTypedArray()
        val PADDING = 16.dp
    }
}

@Composable
fun MainScreen(barcodeScanner: BarcodeScanner) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var qrCodeDetected by remember { mutableStateOf(false) }
    var qrCodeContent by remember { mutableStateOf("") }
    LaunchedEffect(key1 = Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            hasCameraPermission = true
        } else {
            // Request permission - this is handled in the activity, not here
            ActivityCompat.requestPermissions(
                context as MainActivity,
                MainActivity.REQUIRED_PERMISSIONS,
                MainActivity.REQUEST_CODE_PERMISSIONS
            )
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                barcodeScanner,
                { detected -> qrCodeDetected = detected },
                { content -> qrCodeContent = content })
        } else {
            //show a message that no camera is available
            Text(
                text = "No camera available",
                modifier = Modifier
                    .padding(MainActivity.PADDING)
                    .align(Alignment.TopCenter)
            )
        }
        QrCodeText(qrCodeDetected, qrCodeContent)
    }
}

@Composable
fun QrCodeText(qrCodeDetected: Boolean, qrCodeContent: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            text = if (qrCodeDetected) "QR Code Detected: $qrCodeContent" else "No QR Code Detected",
            modifier = Modifier.padding(MainActivity.PADDING)
        )
    }
}

@Composable
fun CameraPreview(
    barcodeScanner: BarcodeScanner,
    setQrCodeDetected: (Boolean) -> Unit,
    setQrCodeContent: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var cameraError by remember { mutableStateOf(false) }
    val cameraController = remember { LifecycleCameraController(context) }
    val previewView = remember { PreviewView(context) }
    cameraController.cameraSelector = CameraSelector.Builder().requireLensFacing(LENS_FACING_BACK).build()

    //Throttle the analysis to avoid constant checks.
    val resolutionStrategy = ResolutionStrategy(
        Size(500, 500),
        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
    )
    val resolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(resolutionStrategy).build()
    cameraController.setImageAnalysisResolutionSelector(resolutionSelector)

    cameraController.setImageAnalysisAnalyzer(
        ContextCompat.getMainExecutor(context),
        MlKitAnalyzer(
            listOf(barcodeScanner),
            COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context)
        ) { result: MlKitAnalyzer.Result? ->
            val barcodeResults = result?.getValue(barcodeScanner)
            if ((barcodeResults == null) ||
                (barcodeResults.size == 0) ||
                (barcodeResults.first() == null)
            ) {
                setQrCodeDetected(false)
                setQrCodeContent("") // Clear the text.
                previewView.overlay.clear()
                previewView.setOnTouchListener { _, _ -> false }
                return@MlKitAnalyzer
            }
            val qrCode = barcodeResults[0]
            val qrCodeViewModel = QrCodeViewModel(qrCode)
            val qrCodeDrawable = QrCodeDrawable(qrCodeViewModel)
            setQrCodeContent(qrCode.rawValue ?: "") // Display the content.
            setQrCodeDetected(true)
            previewView.setOnTouchListener(qrCodeViewModel.qrCodeTouchCallback)
            previewView.overlay.clear()
            previewView.overlay.add(qrCodeDrawable)

        }
    )

    cameraController.bindToLifecycle(lifecycleOwner).also {
        //Check if the camera was able to start or if there is a problem.
        try {
            cameraController.cameraInfo
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Camera error: $e")
            cameraError = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (cameraError) {
            Text(
                text = "Error: could not initialize camera",
                modifier = Modifier
                    .padding(MainActivity.PADDING)
            )
        } else {
            AndroidView(
                factory = {
                    previewView.apply {
                        this.controller = cameraController
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}