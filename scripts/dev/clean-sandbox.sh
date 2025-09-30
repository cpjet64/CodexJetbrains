#!/usr/bin/env bash
set -euo pipefail

# Cleans the default explicit sandbox used by runIde configuration
SYS="$HOME/.codex-idea-sandbox/system"
CFG="$HOME/.codex-idea-sandbox/config"
PLG="$HOME/.codex-idea-sandbox/plugins"

echo "Removing sandbox directories:"
echo " - $SYS"
echo " - $CFG"
echo " - $PLG"
rm -rf "$SYS" "$CFG" "$PLG"
echo "Done."

