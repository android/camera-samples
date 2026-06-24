import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Scaffolds a new camera sample wired into the shared :core-camera / :core-ui architecture.
 *
 * Usage:
 *   ./gradlew createSample \
 *     -PsampleName="camera2-flash" \
 *     -PscreenName="Camera2FlashScreen" \
 *     -Ptitle="Camera2 • Flash" \
 *     -Pdesc="Toggle the flash with Camera2" \
 *     -Ptype="camera2"          # camera2 (default) or camerax
 *
 * It generates a compose-first module (UiState / ViewModel / Controller / Screen) that already
 * shows a working camera preview behind CameraSampleScaffold, then registers it in
 * settings.gradle.kts, app/build.gradle.kts, strings.xml and SampleCatalog.kt.
 */
abstract class CreateSampleTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val sampleName: Property<String>

    @get:Input
    @get:Optional
    abstract val screenName: Property<String>

    @get:Input
    @get:Optional
    abstract val title: Property<String>

    @get:Input
    @get:Optional
    abstract val desc: Property<String>

    @get:Input
    @get:Optional
    abstract val type: Property<String>

    @get:Input
    abstract val rootDirPath: Property<String>

    private val licenseHeader = """
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
""".trim()

    @TaskAction
    fun action() {
        val sName = sampleName.orNull
        val scName = screenName.orNull
        val sTitle = title.orNull
        val sDesc = desc.orNull
        val sType = (type.orNull ?: "camera2").lowercase()

        if (sName == null || scName == null || sTitle == null || sDesc == null) {
            println(
                """
                Usage: ./gradlew createSample \
                    -PsampleName="camera2-flash" \
                    -PscreenName="Camera2FlashScreen" \
                    -Ptitle="Camera2 • Flash" \
                    -Pdesc="Toggle the flash with Camera2" \
                    -Ptype="camera2"   # camera2 (default) or camerax
                """.trimIndent(),
            )
            return
        }

        val isCameraX = sType == "camerax"
        val dashIndex = sName.indexOf("-")
        val pkgName = if (dashIndex >= 0) {
            val apiPrefix = sName.substring(0, dashIndex).replace("_", "")
            val suffix = sName.substring(dashIndex + 1).replace("-", "").replace("_", "")
            "com.android.$apiPrefix.$suffix"
        } else {
            "com.android.${sName.replace("_", "")}"
        }
        val pkgPath = pkgName.replace(".", "/")
        val base = scName.removeSuffix("Screen")

        val rootDirFile = File(rootDirPath.get())
        val sampleDir = File(rootDirFile, "samples/$sName")
        sampleDir.mkdirs()
        val srcDir = File(sampleDir, "src/main/java/$pkgPath")
        srcDir.mkdirs()

        File(sampleDir, "build.gradle.kts").writeText(buildGradle(pkgName))
        File(sampleDir, "consumer-rules.pro").writeText("")
        File(sampleDir, "proguard-rules.pro").writeText(
            "# Add project specific ProGuard rules here.\n" +
                "# By default, the flags in this file are appended to flags specified\n" +
                "# in \$ANDROID_SDK/tools/proguard/proguard-android.txt\n",
        )

        File(srcDir, "${base}UiState.kt").writeText(uiState(pkgName, base))
        File(srcDir, "${base}ViewModel.kt").writeText(viewModel(pkgName, base))
        File(srcDir, "${base}Controller.kt").writeText(
            if (isCameraX) cameraXController(pkgName, base) else camera2Controller(pkgName, base),
        )
        File(srcDir, "$scName.kt").writeText(
            if (isCameraX) cameraXScreen(pkgName, base, scName) else camera2Screen(pkgName, base, scName),
        )

        // settings.gradle.kts
        val settingsFile = File(rootDirFile, "settings.gradle.kts")
        val settingsText = settingsFile.readText()
        if (!settingsText.contains("include(\":samples:$sName\")")) {
            settingsFile.appendText("include(\":samples:$sName\")\n")
        }

        // app/build.gradle.kts
        val appBuildFile = File(rootDirFile, "app/build.gradle.kts")
        val appBuildText = appBuildFile.readText()
        if (!appBuildText.contains("implementation(project(\":samples:$sName\"))")) {
            appBuildFile.writeText(
                appBuildText.replace(
                    "    implementation(project(\":core-ui\"))",
                    "    implementation(project(\":core-ui\"))\n" +
                        "    implementation(project(\":samples:$sName\"))",
                ),
            )
        }

        // strings.xml
        val stringsFile = File(rootDirFile, "app/src/main/res/values/strings.xml")
        val stringsText = stringsFile.readText()
        val titleKey = sName.replace("-", "_") + "_list_title"
        val descKey = sName.replace("-", "_") + "_list_description"
        if (!stringsText.contains(titleKey)) {
            val newStrings = "    <string name=\"$titleKey\">$sTitle</string>\n" +
                "    <string name=\"$descKey\">$sDesc</string>\n"
            stringsFile.writeText(stringsText.replace("</resources>", "$newStrings</resources>"))
        }

        // SampleCatalog.kt
        val catalogFile = File(
            rootDirFile,
            "app/src/main/java/com/android/camera/catalog/domain/SampleCatalog.kt",
        )
        val catalogText = catalogFile.readText()
        if (!catalogText.contains("$scName()")) {
            val typeEnum = if (isCameraX) "CAMERAX" else "CAMERA2"
            val newItem = "    SampleCatalogItem(\n" +
                "        title = R.string.$titleKey,\n" +
                "        description = R.string.$descKey,\n" +
                "        route = \"$scName\",\n" +
                "        sampleEntryScreen = { $scName() },\n" +
                "        type = SampleType.$typeEnum,\n" +
                "    ),"
            val withItem = catalogText.replace(
                "    // To create a new sample entry, add a new SampleCatalogItem here.",
                "$newItem\n\n    // To create a new sample entry, add a new SampleCatalogItem here.",
            )
            val importLine = "import $pkgName.$scName\n"
            catalogFile.writeText(withItem.replaceFirst("import ", importLine + "import "))
        }

        println("==========================================================")
        println("✅ Sample '$sName' ($sType) generated successfully!")
        println("Run './gradlew spotlessApply' then re-sync the Gradle project.")
        println("==========================================================")
    }

    private fun buildGradle(pkg: String): String = licenseHeader + "\n\n" + """
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "$pkg"
    compileSdk = 37

    buildFeatures {
        compose = true
    }

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Shared camera scaffolding (re-exports camera libs).
    implementation(project(":core-camera"))
    implementation(project(":core-ui"))
}
""".trimStart()

    private fun uiState(pkg: String, base: String): String = licenseHeader + "\n" + """
package $pkg

sealed interface ${base}UiState {
    data object Initial : ${base}UiState
    data object Previewing : ${base}UiState
    data class Error(val errorMessage: String?) : ${base}UiState
}
""".trimStart()

    private fun viewModel(pkg: String, base: String): String = licenseHeader + "\n" + """
package $pkg

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ${base}ViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow<${base}UiState>(${base}UiState.Initial)
        val uiState: StateFlow<${base}UiState> = _uiState.asStateFlow()

        fun initialize() {
            if (_uiState.value is ${base}UiState.Initial) {
                _uiState.value = ${base}UiState.Previewing
            }
        }

        fun resetError() {
            _uiState.value = ${base}UiState.Previewing
        }
    }
""".trimStart()

    private fun camera2Controller(pkg: String, base: String): String = licenseHeader + "\n" + """
package $pkg

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.viewfinder.core.ScaleType
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
import androidx.camera.viewfinder.view.ViewfinderView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.android.camera.core.camera2.BaseCamera2Controller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "${base}Controller"

@Composable
fun remember${base}Controller(
    context: Context,
    isFrontCamera: Boolean,
): ${base}Controller {
    val coroutineScope = rememberCoroutineScope()
    return remember(context, isFrontCamera) {
        ${base}Controller(context, isFrontCamera, coroutineScope)
    }
}

/** Preview-only Camera2 controller. Add your feature logic (capture, zoom, …) here. */
@Stable
class ${base}Controller(
    context: Context,
    isFrontCamera: Boolean,
    private val coroutineScope: CoroutineScope,
) : BaseCamera2Controller(context, isFrontCamera) {
    override fun onCameraOpened(camera: CameraDevice, viewfinder: ViewfinderView) {
        coroutineScope.launch {
            try {
                val request = ViewfinderSurfaceRequest(1920, 1080)
                updateTransformationInfo(currentDisplayRotation)
                viewfinder.scaleType = ScaleType.FILL_CENTER

                surfaceSession?.close()
                val session = viewfinder.requestSurfaceSessionAsync(request).await()
                surfaceSession = session
                val surface = session.surface

                previewRequestBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }

                createCaptureSession(camera, listOf(surface)) {
                    try {
                        val builder = previewRequestBuilder ?: return@createCaptureSession
                        builder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                        )
                        captureSession?.setRepeatingRequest(builder.build(), null, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        Log.e(TAG, "Failed to start preview", e)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Exception starting preview", e)
            }
        }
    }
}
""".trimStart()

    private fun camera2Screen(pkg: String, base: String, screen: String): String = licenseHeader + "\n" + """
package $pkg

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camera2.Camera2Preview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraBackButton
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun $screen(
    viewModel: ${base}ViewModel =
        hiltViewModel(
            checkNotNull(LocalViewModelStoreOwner.current) {
                "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
            },
            null,
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERA2) {
        when (val state = uiState) {
            ${base}UiState.Initial -> LoadingView()
            is ${base}UiState.Error ->
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)

            ${base}UiState.Previewing -> PreviewingContent(onBack = onBack)
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember${base}Controller(context = context, isFrontCamera = false)

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Camera2Preview(controller = controller)

    CameraBackButton(
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp),
    )
    // TODO: Add this sample's controls here.
}
""".trimStart()

    private fun cameraXController(pkg: String, base: String): String = licenseHeader + "\n" + """
package $pkg

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

private const val TAG = "${base}Controller"

@Composable
fun remember${base}Controller(
    context: Context,
    lifecycleOwner: LifecycleOwner,
): ${base}Controller =
    remember(context, lifecycleOwner) {
        ${base}Controller(context, lifecycleOwner)
    }

/** Preview-only CameraX controller. Add your feature logic (capture, zoom, …) here. */
@Stable
class ${base}Controller(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    var surfaceRequest: SurfaceRequest? by mutableStateOf(null)
        private set

    private var cameraProvider: ProcessCameraProvider? = null

    private val preview =
        Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }

    fun openCamera() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun closeCamera() {
        cameraProvider?.unbindAll()
    }

    fun release() {
        closeCamera()
    }
}
""".trimStart()

    private fun cameraXScreen(pkg: String, base: String, screen: String): String = licenseHeader + "\n" + """
package $pkg

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.android.camera.core.camerax.CameraXPreview
import com.android.camera.core.permissions.CameraPermissions
import com.android.camera.coreui.controls.CameraBackButton
import com.android.camera.coreui.scaffold.CameraApi
import com.android.camera.coreui.scaffold.CameraSampleScaffold
import com.android.camera.coreui.state.ErrorView
import com.android.camera.coreui.state.LoadingView

@Composable
fun $screen(
    viewModel: ${base}ViewModel =
        hiltViewModel(
            checkNotNull(LocalViewModelStoreOwner.current) {
                "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
            },
            null,
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onBack = { backDispatcher?.onBackPressed() ?: Unit }

    LaunchedEffect(Unit) { viewModel.initialize() }

    CameraSampleScaffold(permissions = CameraPermissions.PHOTO, api = CameraApi.CAMERAX) {
        when (val state = uiState) {
            ${base}UiState.Initial -> LoadingView()
            is ${base}UiState.Error ->
                ErrorView(errorMessage = state.errorMessage, onRetry = viewModel::resetError)

            ${base}UiState.Previewing -> PreviewingContent(onBack = onBack)
        }
    }
}

@Composable
private fun BoxScope.PreviewingContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember${base}Controller(context = context, lifecycleOwner = lifecycleOwner)

    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_RESUME -> controller.openCamera()
                    Lifecycle.Event.ON_PAUSE -> controller.closeCamera()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    controller.surfaceRequest?.let { request ->
        CameraXPreview(surfaceRequest = request)
    }

    CameraBackButton(
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp),
    )
    // TODO: Add this sample's controls here.
}
""".trimStart()
}

tasks.register<CreateSampleTask>("createSample") {
    description = "Generates a new compose-first camera sample for the Camera Samples Catalog"
    group = "generation"

    sampleName.set(providers.gradleProperty("sampleName"))
    screenName.set(providers.gradleProperty("screenName"))
    title.set(providers.gradleProperty("title"))
    desc.set(providers.gradleProperty("desc"))
    type.set(providers.gradleProperty("type"))
    rootDirPath.set(rootDir.absolutePath)
}
