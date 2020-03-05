#!/bin/bash
set -xe

# Iterate over each subfolder, which contains an independent project
# You may edit this if your repo has a different project structure
for SAMPLE in */ ; do
    # Skip folders without a gradlew file
    [ ! -f "${SAMPLE}gradlew" ] && continue
    echo "Running unit tests for $SAMPLE"
    # Tell Gradle that this is a CI environment and disable parallel compilation
    bash "${SAMPLE}gradlew" -p "$SAMPLE" -Pci --no-parallel --stacktrace $@
done
