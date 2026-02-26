#!/usr/bin/env bash
set -euo pipefail

ERRORS=0

echo "=== Repo Hygiene Check ==="

# Check for large tracked files (>10MB, excluding vendor/)
echo -n "Large files (>10MB): "
LARGE_FILES=$(git ls-files | while read -r f; do
    if [[ -f "$f" && "$f" != vendor/* ]]; then
        SIZE=$(wc -c < "$f" 2>/dev/null || echo 0)
        if [ "$SIZE" -gt 10485760 ]; then
            echo "  $f ($(( SIZE / 1048576 ))MB)"
        fi
    fi
done)
if [ -z "$LARGE_FILES" ]; then
    echo "PASS"
else
    echo "FAIL"
    echo "$LARGE_FILES"
    ERRORS=$((ERRORS + 1))
fi

# Check for merge conflict markers
echo -n "Merge conflict markers: "
if git grep -l '<<<<<<< ' -- '*.rs' '*.toml' '*.json' '*.ts' '*.js' '*.py' '*.md' 2>/dev/null; then
    echo "FAIL — conflict markers found"
    ERRORS=$((ERRORS + 1))
else
    echo "PASS"
fi

# Check required files exist
echo -n "Required files: "
MISSING=""
for f in .gitignore; do
    if [ ! -f "$f" ]; then
        MISSING="$MISSING $f"
    fi
done
if [ -z "$MISSING" ]; then
    echo "PASS"
else
    echo "FAIL — missing:$MISSING"
    ERRORS=$((ERRORS + 1))
fi

if [ "$ERRORS" -gt 0 ]; then
    echo "=== $ERRORS hygiene issue(s) found ==="
    exit 1
fi

echo "=== All hygiene checks passed ==="
