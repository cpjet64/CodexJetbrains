#!/usr/bin/env bash
# Unix helper: build, test, verify with console output logged to file
# Usage: bash scripts/dev/unix-build.sh
set -euo pipefail

cd "$(dirname "$0")/../../"
mkdir -p build/logs

echo "Running: ./gradlew --no-daemon --console=plain test verifyPlugin buildPlugin"
./gradlew --no-daemon --console=plain test verifyPlugin buildPlugin 2>&1 | tee build/logs/gradle-build.log

