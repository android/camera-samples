tasks.register("createSample") {
    description = "Generates a new sample for the Camera Samples Catalog"
    group = "generation"

    doLast {
        val sampleName = project.findProperty("sampleName") as? String
        val screenName = project.findProperty("screenName") as? String
        val title = project.findProperty("title") as? String
        val desc = project.findProperty("desc") as? String
        
        if (sampleName == null || screenName == null || title == null || desc == null) {
            println("""
                Usage: ./gradlew createSample \
                    -PsampleName="camera2-video" \
                    -PscreenName="Camera2VideoScreen" \
                    -Ptitle="Camera2 • Video" \
                    -Pdesc="A simple video sample"
            """.trimIndent())
            return@doLast
        }

        val pkgName = "com.android.camera.samples.${sampleName.replace("-", "").replace("_", "")}"
        val pkgPath = pkgName.replace(".", "/")
        val sampleDir = file("samples/$sampleName")
        sampleDir.mkdirs()

        // 1. build.gradle.kts
        val buildFile = file("samples/$sampleName/build.gradle.kts")
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
    kotlinOptions {
        jvmTarget = "17"
    }
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
        val srcDir = file("samples/$sampleName/src/main/java/$pkgPath")
        srcDir.mkdirs()
        val screenFile = file(srcDir.path + "/${screenName}.kt")
        screenFile.writeText("""
package $pkgName

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun $screenName() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Hello from $screenName")
    }
}
""".trimIndent() + "\n")

        // 3. Update settings.gradle.kts
        val settingsFile = file("settings.gradle.kts")
        val settingsText = settingsFile.readText()
        if (!settingsText.contains("include(\":samples:${sampleName}\")")) {
            settingsFile.appendText("include(\":samples:${sampleName}\")\n")
        }

        // 4. Update app/build.gradle.kts
        val appBuildFile = file("app/build.gradle.kts")
        val appBuildText = appBuildFile.readText()
        if (!appBuildText.contains("implementation(project(\":samples:${sampleName}\"))")) {
            val updatedAppBuildText = appBuildText.replace(
                "implementation(project(\":ui-component\"))",
                "implementation(project(\":ui-component\"))\n    implementation(project(\":samples:${sampleName}\"))"
            )
            appBuildFile.writeText(updatedAppBuildText)
        }

        // 5. Update strings.xml
        val stringsFile = file("app/src/main/res/values/strings.xml")
        val stringsText = stringsFile.readText()
        val titleKey = "${sampleName.replace("-", "_")}_list_title"
        val descKey = "${sampleName.replace("-", "_")}_list_description"
        if (!stringsText.contains(titleKey)) {
            val newStrings = "    <string name=\"$titleKey\">$title</string>\n    <string name=\"$descKey\">$desc</string>\n"
            val updatedStrings = stringsText.replace("</resources>", "$newStrings</resources>")
            stringsFile.writeText(updatedStrings)
        }

        // 6. Update SampleCatalog.kt
        val catalogFile = file("app/src/main/java/com/android/camera/catalog/domain/SampleCatalog.kt")
        val catalogText = catalogFile.readText()
        if (!catalogText.contains("$screenName()")) {
            val newItem = "" +
"    SampleCatalogItem(\n" +
"        title = R.string.$titleKey,\n" +
"        description = R.string.$descKey,\n" +
"        route = \"$screenName\",\n" +
"        sampleEntryScreen = { $screenName() },\n" +
"        type = SampleType.CAMERA2,\n" +
"        tags = listOf(),\n" +
"        isFeatured = false,\n" +
"    ),"
            
            val updatedCatalog = catalogText.replace(
                "    // To create a new sample entry, add a new SampleCatalogItem here.",
                "$newItem\n\n    // To create a new sample entry, add a new SampleCatalogItem here."
            )
            
            val importLine = "import $pkgName.$screenName\n"
            val finalCatalog = updatedCatalog.replaceFirst("import ", importLine + "import ")
            catalogFile.writeText(finalCatalog)
        }
        
        println("==========================================================")
        println("✅ Sample '$sampleName' generated successfully!")
        println("To see your new sample, please re-sync the Gradle project.")
        println("==========================================================")
    }
}
