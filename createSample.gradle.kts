import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

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
    abstract val rootDirPath: Property<String>

    @TaskAction
    fun action() {
        val sName = sampleName.orNull
        val scName = screenName.orNull
        val sTitle = title.orNull
        val sDesc = desc.orNull
        
        if (sName == null || scName == null || sTitle == null || sDesc == null) {
            println("""
                Usage: ./gradlew createSample \
                    -PsampleName="camera2-video" \
                    -PscreenName="Camera2VideoScreen" \
                    -Ptitle="Camera2 • Video" \
                    -Pdesc="A simple video sample"
            """.trimIndent())
            return
        }

        val safeName = sName as String
        val dashIndex = safeName.indexOf("-")
        val pkgName = if (dashIndex >= 0) {
            val apiPrefix = safeName.substring(0, dashIndex).replace("_", "")
            val sampleSuffix = safeName.substring(dashIndex + 1).replace("-", "").replace("_", "")
            "com.android.$apiPrefix.$sampleSuffix"
        } else {
            "com.android.${safeName.replace("_", "")}"
        }
        val pkgPath = pkgName.replace(".", "/")
        val rootDirFile = File(rootDirPath.get())
        val sampleDir = File(rootDirFile, "samples/$sName")
        sampleDir.mkdirs()

        // 1. build.gradle.kts
        val buildFile = File(sampleDir, "build.gradle.kts")
        buildFile.writeText("""
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "$pkgName"
    compileSdk = 36

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
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material.icons.extended)
    implementation(project(":ui-component"))
}
""".trimIndent() + "\n")

        // 2. Main Screen file
        val srcDir = File(sampleDir, "src/main/java/$pkgPath")
        srcDir.mkdirs()
        val screenFile = File(srcDir, "${scName}.kt")
        screenFile.writeText("""
package $pkgName

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun $scName() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Hello from $scName")
    }
}
""".trimIndent() + "\n")

        // 3. Update settings.gradle.kts
        val settingsFile = File(rootDirFile, "settings.gradle.kts")
        val settingsText = settingsFile.readText()
        if (!settingsText.contains("include(\":samples:$sName\")")) {
            settingsFile.appendText("include(\":samples:$sName\")\n")
        }

        // 4. Update app/build.gradle.kts
        val appBuildFile = File(rootDirFile, "app/build.gradle.kts")
        val appBuildText = appBuildFile.readText()
        if (!appBuildText.contains("implementation(project(\":samples:$sName\"))")) {
            val updatedAppBuildText = appBuildText.replace(
                "implementation(project(\":ui-component\"))",
                "implementation(project(\":ui-component\"))\n    implementation(project(\":samples:$sName\"))"
            )
            appBuildFile.writeText(updatedAppBuildText)
        }

        // 5. Update strings.xml
        val stringsFile = File(rootDirFile, "app/src/main/res/values/strings.xml")
        val stringsText = stringsFile.readText()
        val titleKey = sName.replace("-", "_") + "_list_title"
        val descKey = sName.replace("-", "_") + "_list_description"
        if (!stringsText.contains(titleKey)) {
            val newStrings = "    <string name=\"$titleKey\">$sTitle</string>\n    <string name=\"$descKey\">$sDesc</string>\n"
            val updatedStrings = stringsText.replace("</resources>", "$newStrings</resources>")
            stringsFile.writeText(updatedStrings)
        }

        // 6. Update SampleCatalog.kt
        val catalogFile = File(rootDirFile, "app/src/main/java/com/android/camera/catalog/domain/SampleCatalog.kt")
        val catalogText = catalogFile.readText()
        if (!catalogText.contains("$scName()")) {
            val newItem = "" +
"    SampleCatalogItem(\n" +
"        title = R.string.$titleKey,\n" +
"        description = R.string.$descKey,\n" +
"        route = \"$scName\",\n" +
"        sampleEntryScreen = { $scName() },\n" +
"        type = SampleType.CAMERA2,\n" +
"        tags = listOf(),\n" +
"        isFeatured = false,\n" +
"    ),"
            
            val updatedCatalog = catalogText.replace(
                "    // To create a new sample entry, add a new SampleCatalogItem here.",
                "$newItem\n\n    // To create a new sample entry, add a new SampleCatalogItem here."
            )
            
            val importLine = "import $pkgName.$scName\n"
            val finalCatalog = updatedCatalog.replaceFirst("import ", importLine + "import ")
            catalogFile.writeText(finalCatalog)
        }
        
        println("==========================================================")
        println("✅ Sample '$sName' generated successfully!")
        println("To see your new sample, please re-sync the Gradle project.")
        println("==========================================================")
    }
}

tasks.register<CreateSampleTask>("createSample") {
    description = "Generates a new sample for the Camera Samples Catalog"
    group = "generation"
    
    sampleName.set(providers.gradleProperty("sampleName"))
    screenName.set(providers.gradleProperty("screenName"))
    title.set(providers.gradleProperty("title"))
    desc.set(providers.gradleProperty("desc"))
    rootDirPath.set(rootDir.absolutePath)
}