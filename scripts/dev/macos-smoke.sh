#!/usr/bin/env bash
set -euo pipefail

echo "macOS smoke: build and launch sandbox IDE"

if ! command -v java >/dev/null; then
  echo "Java not found; install JDK 17 and ensure on PATH" >&2
  exit 1
fi

ver=$(java -version 2>&1 | head -n1)
echo "java -version => $ver"
if [[ "$ver" != *"\"17."*"\""* ]]; then
  echo "Warning: JDK 17 required, current: $ver" >&2
fi

./gradlew buildPlugin --console=plain --no-daemon

zip=$(ls -t build/distributions/*.zip | head -n1)
echo "Built: $zip"

SANDBOX="$HOME/.codex-idea-sandbox"
LOG="$SANDBOX/system/log/idea.log"
echo "Sandbox: $SANDBOX"
echo "Log: $LOG"

set +e
./gradlew runIde --console=plain --no-daemon
rc=$?
set -e
echo "runIde exit code: $rc"

if [[ -f "$LOG" ]]; then
  echo "Recent sandbox log lines:"
  tail -n 200 "$LOG" || true
else
  echo "Sandbox log not found yet: $LOG" >&2
fi

echo "Open the Codex tool window; verify CLI path detection and a simple chat turn."

