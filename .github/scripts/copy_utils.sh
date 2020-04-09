#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -xe

# Crawl all settings.gradle files which indicate an Android project
for GRADLE_FILE in `find . -name "settings.gradle"` ; do
    SAMPLE=$(dirname "${GRADLE_FILE}")
    # If the sample depends on "utils", copy it from the library project
    if grep -q "include 'utils'" "$GRADLE_FILE"; then
        rm -rf "$SAMPLE/utils"
        cp -avR "$SAMPLE/../CameraUtils/lib" "$SAMPLE/utils"

        # Temporarily disable .gitignore file to add to utils to Git
        mv "$SAMPLE/.gitignore" "$SAMPLE/.gitignore.bak"
        git add "$SAMPLE/utils"
        mv "$SAMPLE/.gitignore.bak" "$SAMPLE/.gitignore"
    fi
done
