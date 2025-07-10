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

echo "JAVA_HOME_11_X64: $JAVA_HOME_11_X64"
echo "JAVA_HOME_17_X64: $JAVA_HOME_17_X64"

# Crawl all gradlew files which indicate an Android project
# You may edit this if your repo has a different project structure
for GRADLEW in `find . -name "gradlew"` ; do
    SAMPLE=$(dirname "${GRADLEW}")
    if [[ -n "$CI" ]]; then
        echo "CI: $CI"

        VERSION_FILE="$SAMPLE/.java-version"
        ORIGINAL_PATH=${ORIGINAL_PATH:-$PATH}
        DEFAULT_JAVA_HOME=$JAVA_HOME_17_X64
        export JAVA_HOME=$DEFAULT_JAVA_HOME
        export PATH=$ORIGINAL_PATH
        if [[ -f "$VERSION_FILE" ]]; then
            REQUIRED_VERSION=$(tr -d '[:space:]' < "$VERSION_FILE")
            case "$REQUIRED_VERSION" in
            "11") export JAVA_HOME=$JAVA_HOME_11_X64 ;;
            "17") export JAVA_HOME=$JAVA_HOME_17_X64 ;;
            *) echo "CI Warning: Unknown version '$REQUIRED_VERSION'. Using default." ;;
            esac
        fi
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    if [[ -z "$CI" ]]; then
        echo "No CI"
    fi

    if [[ -z "$CI" && -n "$(command -v jenv)" ]]; then
        # Initialize jenv to manage Java versions
        if command -v jenv &> /dev/null; then
            pushd "$SAMPLE"
            eval "$(jenv init -)"
            popd
        fi
    fi
    # Tell Gradle that this is a CI environment and disable parallel compilation
    bash "$GRADLEW" -p "$SAMPLE" -Pci --no-parallel --stacktrace $@
done
