#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/logs

if [[ "${RUNNER_OS:-}" != "" ]]; then
  echo "Running in CI on ${RUNNER_OS}"
fi

./gradlew --no-daemon --console=plain test verifyPlugin buildPlugin 2>&1 | tee build/logs/verify.log

echo "Artifacts:"
ls -la build/distributions || true

