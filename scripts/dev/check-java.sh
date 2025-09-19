#!/usr/bin/env bash
set -euo pipefail

err() {
  echo "[check-java] $*" >&2
}

if [[ -z "${JAVA_HOME:-}" ]]; then
  err "JAVA_HOME is not set. Export it before running Gradle."
  exit 1
fi

JAVA_BIN="$JAVA_HOME/bin/java"
if [[ ! -x "$JAVA_BIN" ]]; then
  err "JAVA_HOME does not contain a runnable java binary: $JAVA_BIN"
  exit 1
fi

JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | head -n 1)
if [[ "$JAVA_VERSION" != *"17."* ]]; then
  err "Expected a Java 17 runtime but found: $JAVA_VERSION"
  exit 1
fi

echo "JAVA_HOME -> $JAVA_HOME"
echo "$JAVA_VERSION"
