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
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.hilt.plugin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.android.camera.catalog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.android.camera.catalog"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(project(":core-theme"))

    // CameraX Samples
    implementation(project(":samples:camerax-takeaphoto"))
    implementation(project(":samples:camerax-takeavideo"))
    implementation(project(":samples:camerax-qrscanner"))
    implementation(project(":samples:camerax-imagelabeling"))
    implementation(project(":samples:camerax-extensions"))
    implementation(project(":samples:camerax-zoomandtorch"))
    implementation(project(":samples:camerax-exposure"))
    implementation(project(":samples:camerax-luminosity"))
    implementation(project(":samples:camerax-ultrahdr"))
    implementation(project(":samples:camerax-concurrentcamera"))
    implementation(project(":samples:camerax-videopauseresume"))
    implementation(project(":samples:camerax-effects"))
    implementation(project(":samples:camerax-greenscreen"))

    // Camera2 Samples
    implementation(project(":samples:camera2-takeaphoto"))
    implementation(project(":samples:camera2-takeavideo"))
    implementation(project(":samples:camera2-slowmotion"))
    implementation(project(":samples:camera2-extensions"))
    implementation(project(":samples:camera2-hdrviewfinder"))
    implementation(project(":samples:camera2-zoomandtorch"))
    implementation(project(":samples:camera2-manualcontrols"))
    implementation(project(":samples:camera2-qrscanner"))
    implementation(project(":samples:camera2-rawcapture"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
