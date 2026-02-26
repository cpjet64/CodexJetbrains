set windows-shell := ["powershell.exe", "-NoLogo", "-Command"]

# === Modes ===

# Pre-commit: fast checks (~10-30s)
ci-fast: hygiene test-quick

# Pre-push: exhaustive checks (~5-15min)
ci-deep: ci-fast build-plugin verify-plugin test-full

# === Repo Hygiene ===
hygiene:
    bash scripts/hygiene.sh

# Use JDK 21 explicitly because system default may be newer and unsupported by Gradle/Kotlin script parsing.
_with-jdk21 cmd:
    if (Test-Path 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot') { $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot'; $env:Path = "$env:JAVA_HOME\bin;$env:Path" }; {{cmd}}

test-quick:
    just _with-jdk21 ".\gradlew.bat --no-daemon test --tests 'dev.curt.codexjb.ui.ChatPanelDebounceTest' --tests 'dev.curt.codexjb.ui.ChatPanelTest' --tests 'dev.curt.codexjb.proto.ApprovalsTest'"

test-full:
    just _with-jdk21 ".\gradlew.bat --no-daemon test"

build-plugin:
    just _with-jdk21 ".\gradlew.bat --no-daemon buildPlugin"

verify-plugin:
    just _with-jdk21 ".\gradlew.bat --no-daemon verifyPlugin"

# === Optional ===
clean:
    just _with-jdk21 ".\gradlew.bat --no-daemon clean"

# === Frontend (optional for mixed projects) ===
# fmt-frontend:
#     pnpm prettier --check .
# lint-frontend:
#     pnpm eslint .
# test-frontend:
#     pnpm vitest run

# === Python (optional for mixed projects) ===
# fmt-python:
#     uv run ruff format --check .
# lint-python:
#     uv run ruff check .
# test-python:
#     uv run pytest
