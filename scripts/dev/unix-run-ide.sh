#!/usr/bin/env bash
# Unix helper: launch sandbox IDE with console output logged to file
# Usage: bash scripts/dev/unix-run-ide.sh
set -euo pipefail

cd "$(dirname "$0")/../../"
mkdir -p build/logs

echo "Running: ./gradlew --no-daemon --console=plain runIde"
./gradlew --no-daemon --console=plain runIde 2>&1 | tee build/logs/runIde.log

