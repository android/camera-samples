/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Camera Samples Catalog"
include(":app")
include(":core-camera")
include(":core-theme")
include(":core-ui")
include(":samples:camerax-takeaphoto")
include(":samples:camera2-takeaphoto")
include(":samples:camera2-takeavideo")
include(":samples:camerax-takeavideo")
include(":samples:camera2-slowmotion")
include(":samples:camera2-extensions")
include(":samples:camera2-hdrviewfinder")
include(":samples:camera2-zoomandtorch")
include(":samples:camera2-manualcontrols")
include(":samples:camerax-qrscanner")
include(":samples:camerax-imagelabeling")
include(":samples:camerax-extensions")
include(":samples:camerax-zoomandtorch")
include(":samples:camerax-exposure")
include(":samples:camerax-luminosity")
include(":samples:camera2-qrscanner")
include(":samples:camerax-videopauseresume")
include(":samples:camerax-ultrahdr")
include(":samples:camerax-concurrentcamera")
include(":samples:camera2-rawcapture")
include(":samples:camerax-effects")
include(":samples:camerax-greenscreen")
include(":samples:camerax-hdrvideo")
include(":samples:camera2-hdrvideo")
include(":samples:camerax-lowlightboost")
include(":samples:camerax-videostabilization")
include(":samples:camerax-flipwhilerecording")
include(":samples:camerax-featurecombination")
