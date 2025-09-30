#!/usr/bin/env bash
set -euo pipefail

LOG="$HOME/.codex-idea-sandbox/system/log/idea.log"

if [[ ! -f "$LOG" ]]; then
  echo "Log file not found: $LOG"
  exit 1
fi

echo "Showing last 200 lines of $LOG"
tail -n 200 "$LOG"

