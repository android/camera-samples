import org.jetbrains.kotlin.config.KotlinCompilerVersion

/*
 * Copyright 2019 Google LLC
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
  id("com.android.application")
  kotlin("android")
  kotlin("android.extensions")
  kotlin("kapt")
  id("androidx.navigation.safeargs")
}

/***
 * We keep the android { ... } block written in Groovy
 * because there are not Kotlin snippets yet in the Android Gradle Plugin documentation
 * https://developer.android.com/studio/build
 */
apply(from = "android.gradle")

dependencies {
  val kotlin_version = KotlinCompilerVersion.VERSION

  implementation(project(":common"))

  // Kotlin lang
  implementation("androidx.core:core-ktx:1.1.0")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.3")

  // App compat and UI things
  implementation("androidx.appcompat:appcompat:1.1.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.2.0-rc03")
  implementation("androidx.viewpager2:viewpager2:1.0.0")

  // Navigation library
  val nav_version = "2.1.0"
  implementation("androidx.navigation:navigation-fragment-ktx:$nav_version")
  implementation("androidx.navigation:navigation-ui-ktx:$nav_version")

  // EXIF Interface
  implementation("androidx.exifinterface:exifinterface:1.1.0")

  // Glide
  implementation("com.github.bumptech.glide:glide:4.11.0")
  kapt("com.github.bumptech.glide:compiler:4.11.0")

  // Unit testing
  testImplementation("androidx.test.ext:junit:1.1.1")
  testImplementation("androidx.test:rules:1.2.0")
  testImplementation("androidx.test:runner:1.2.0")
  testImplementation("androidx.test.espresso:espresso-core:3.2.0")
  testImplementation("org.robolectric:robolectric:4.3.1")

  // Instrumented testing
  androidTestImplementation("androidx.test.ext:junit:1.1.1")
  androidTestImplementation("androidx.test:rules:1.2.0")
  androidTestImplementation("androidx.test:runner:1.2.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
}