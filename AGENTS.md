# AGENTS.md — CodexJetbrains

## Purpose
This document is for automation agents and humans to build, test, and run the plugin consistently using the IntelliJ Platform Gradle Plugin **2.x** and **JDK 21**.

---

## Prerequisites
- **JDK 21** (JAVA_HOME should point to a JDK 21 install)
- **Gradle Wrapper** checked in (`./gradlew`)
- Internet access to JetBrains artifact repos (via cache-redirector)

---

## Configuration Rules
- **Repositories live in `settings.gradle.kts`** (pluginManagement + dependencyResolutionManagement).
- The project **must not** declare `repositories {}` in `build.gradle.kts`.
- The IntelliJ Gradle plugin is **`org.jetbrains.intellij.platform`**, version **2.9.0** (resolved via settings).
- Kotlin plugin version: **1.9.24** (declared in `build.gradle.kts`).

---

## Key Build Parameters
- `platformVersion` — baseline IDE version to resolve (default: `2024.2`).
  Set with: `-PplatformVersion=2024.2` or `ORG_GRADLE_PROJECT_platformVersion`.
- `codex_withJava` — include the `com.intellij.java` bundled plugin (default: `false`).
  Set with: `-Pcodex_withJava=true` or `ORG_GRADLE_PROJECT_codex_withJava`.

### Compatibility Guidance
- `codex_withJava=true` → compatible with IDEs that ship Java (IDEA, Android Studio…).
- `codex_withJava=false` → broader IDE coverage (PyCharm/WebStorm/Rider etc.), **only if the code does not use Java PSI**.

---

## Canonical Commands

### Run tests
```bash
./gradlew test
```

### Build plugin distribution
```bash
./gradlew buildPlugin
```

### Verify plugin compatibility
```bash
./gradlew verifyPlugin
```

### Run plugin in sandbox IDE
```bash
./gradlew runIde
```

### Clean build
```bash
./gradlew clean build
```

---

## Test Suite

### Current Status
- **Total tests**: 114
- **All tests passing**: ✅
- Test framework: JUnit4 with kotlin.test and IntelliJ Platform test fixtures

### Key Test Files
- Unit tests for parsing, telemetry, configuration, etc.
- Integration tests:
  - `PatchApplierIntegrationTest` — validates patch application to files
  - `DiagnosticsServiceTest` — validates sensitive data redaction

### Running specific tests
```bash
./gradlew test --tests "dev.curt.codexjb.tooling.PatchApplierIntegrationTest"
```

---

## Run Configurations

Shared run configurations are available in `.idea/runConfigurations/`:
- **Run Plugin** — launches plugin in sandbox IDE
- **Build Plugin** — builds plugin distribution
- **Verify Plugin** — runs plugin verification
- **Run Tests** — executes test suite

---

## Architecture Notes

### Patch Application
- `PatchApplier.apply()` — main entry point for applying unified diffs
- `PatchApplier.doApplyWithBase()` — internal testable function that accepts custom base path
- `PatchEngine.apply()` — low-level patch application logic (unit tested)
- `UnifiedDiffParser.parse()` — parses unified diff format

### Security
- `SensitiveDataRedactor` — redacts API keys and tokens (e.g., `sk-[A-Za-z0-9]{16,}`)
- `DiagnosticsService` — logs with automatic redaction

---

## Common Issues

### Test Failures
- Ensure JDK 21 is used
- Run `./gradlew clean test` to clear cached test results
- Check that no IDE instances are holding file locks

### Build Failures
- Verify internet connectivity for dependency resolution
- Check `~/.gradle/caches` for corrupted artifacts
- Ensure JAVA_HOME points to JDK 21

---

## Version Information
- **IntelliJ Platform Gradle Plugin**: 2.9.0 (2.x series)
- **Target Platform**: 2024.2
- **Kotlin**: 1.9.24
- **JDK**: 21
- **Gradle**: 8.7 (via wrapper)

## Additional Context

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CodexJetbrains is an unofficial IntelliJ Platform plugin that integrates with the Codex CLI. It provides a dedicated ToolWindow for interacting with Codex without leaving the IDE, including message review, approval handling, and diff application.

- **Target Platform**: IntelliJ IDEA 2025.2+ (build 242+)
- **Runtime**: JDK 21 (enforced via Gradle toolchain)
- **Language**: Kotlin 1.9.24
- **Build System**: Gradle with IntelliJ Platform Gradle Plugin 2.9.0

## Build Commands

### Prerequisites
- JDK 21 (`JAVA_HOME` must point to JDK 21)
- Codex CLI available on PATH or configured in plugin settings

### Core Gradle Tasks
```bash
# Build, test, and package the plugin
./gradlew test verifyPlugin buildPlugin

# Run a sandbox IDE for manual testing
./gradlew runIde

# Run only tests
./gradlew test
```

### Platform-Specific Wrappers
The repository includes helper scripts in `scripts/dev/`:

**Windows (PowerShell)**:
- `powershell -ExecutionPolicy Bypass -File scripts/dev/win-build.ps1` - Build with logging
- `powershell -ExecutionPolicy Bypass -File scripts/dev/win-run-ide.ps1` - Run sandbox with logging
- `powershell -ExecutionPolicy Bypass -File scripts/dev/verify.ps1` - Full verification pipeline
- `powershell -ExecutionPolicy Bypass -File scripts/dev/windows-smoke.ps1` - Quick smoke test

**Linux/macOS (Bash)**:
- `bash scripts/dev/unix-build.sh` - Build with logging
- `bash scripts/dev/unix-run-ide.sh` - Run sandbox with logging
- `bash scripts/dev/verify.sh` - Full verification pipeline
- `bash scripts/dev/linux-smoke.sh` or `bash scripts/dev/macos-smoke.sh` - Smoke tests

### Running Tests
```bash
# All tests
./gradlew test

# Specific test class (example)
./gradlew test --tests "dev.curt.codexjb.proto.EventBusTest"
```

## Architecture

### Core Package Structure
- **`core/`** - Process management, CLI execution, configuration, health monitoring, telemetry
- **`proto/`** - Protocol handling for Codex CLI communication (events, envelopes, correlation)
- **`ui/`** - UI components (ToolWindow, panels, status bar, ANSI rendering)
- **`tooling/`** - Diff/patch application, Git staging integration
- **`actions/`** - IDE actions (Ask Codex, Diagnostics, Report Issue)

### Key Components

#### Process Management
- **CodexProcessService** (`core/CodexProcessService.kt`): Manages long-lived Codex CLI process lifecycle
- **CodexProcessConfig** (`core/CodexProcessConfig.kt`): Configuration for CLI execution
- **ProcessHealthMonitor** (`core/ProcessHealthMonitor.kt`): Monitors CLI health and handles auto-restart

#### Protocol Communication
- **EventBus** (`proto/EventBus.kt`): Central event distribution for CLI protocol messages
- **Envelopes** (`proto/Envelopes.kt`): JSON envelope encoding/decoding for CLI communication
  - `SubmissionEnvelope`: Plugin → CLI messages
  - `EventEnvelope`: CLI → Plugin events
- **TurnRegistry** (`proto/TurnRegistry.kt`): Tracks conversation turns and their state
- **SessionState** (`proto/SessionState.kt`): Maintains session configuration from CLI

#### UI Components
- **CodexToolWindowFactory** (`ui/CodexToolWindowFactory.kt`): Main entry point, wires CLI to UI
- **ChatPanel** (`ui/ChatPanel.kt`): Chat interface for user interaction
- **DiffPanel** (`ui/DiffPanel.kt`): Preview and apply diffs from Codex
- **DiagnosticsPanel** (`ui/DiagnosticsPanel.kt`): Stream stderr output for troubleshooting
- **ExecConsolePanel** (`ui/ExecConsolePanel.kt`): Display exec tool output with ANSI coloring

#### Diff Application
- **PatchApplier** (`tooling/PatchApplier.kt`): Applies unified diffs to project files via IntelliJ write actions
- **UnifiedDiffParser** (`diff/UnifiedDiff.kt`): Parses unified diff format
- **GitStager** (`tooling/GitStager.kt`): Auto-stages applied changes when enabled

### Configuration
Settings are managed through:
- **CodexConfigService** (`core/CodexConfigService.kt`): Global application settings
- **CodexProjectSettingsService** (`core/CodexProjectSettingsService.kt`): Per-project overrides
- Settings UI: `ui/settings/CodexSettingsConfigurable.kt`

Settings include CLI path, WSL preference, auto-staging, model/effort defaults, sandbox policies, and approval levels.

### WSL Support
On Windows, the plugin can execute Codex via WSL:
- **WslDetection** (`core/WslDetection.kt`): Detects WSL availability
- When enabled, wraps Codex execution through `wsl` command

## Development Workflow

### Commit Format
Follow the format documented in `AGENTS.md`:
```
[T<task>.<sub>] <summary>; post-test=<pass>; compare=<stdout>
```

### Before Committing
1. Run `./gradlew test` to ensure all tests pass
2. Add focused tests for UI or protocol logic changes
3. Track feature status in TODO markdown files (various `todo-*.md` files exist)

### Sandbox Locations
- **Default**: `build/idea-sandbox/system/log/idea.log`
- **Explicit paths** (configured in build.gradle.kts):
  - Windows: `%USERPROFILE%\.codex-idea-sandbox\`
  - Linux/macOS: `$HOME/.codex-idea-sandbox/`

### Build Parameters
- `platformVersion`: IDE version (default: `2024.2`)
- `codex_withJava`: Include Java plugin support (default: `false`)
  - Set `true` for IDEA/Android Studio compatibility
  - Keep `false` for broader IDE coverage (PyCharm, WebStorm, etc.)

### Troubleshooting
- Check Settings > Tools > Codex for CLI path and WSL settings
- Use Diagnostics tab in Codex ToolWindow to view stderr
- Run "Codex (Unofficial): Report Issue" from Tools menu to capture diagnostics snapshot
- Sandbox logs are in `.codex-idea-sandbox/system/log/idea.log`
- On Ubuntu 24.04: Use absolute CLI path if detection fails

## Testing

Tests are organized by package in `src/test/kotlin/`:
- Core logic tests (process management, config, health monitoring)
- Protocol tests (event bus, envelopes, correlation, approvals)
- UI tests (panels, diff application, chat)
- Integration tests (patch application end-to-end)

Test framework uses JUnit5 with IntelliJ Platform test framework.

## Plugin Descriptor

Plugin metadata in `src/main/resources/META-INF/plugin.xml`:
- Plugin ID: `dev.curt.codex`
- Minimum build: 242 (IntelliJ 2025.2)
- Depends only on `com.intellij.modules.platform` (no Java PSI by default)

## Environment

### Cache Locations
All caches are centralized under `C:\Dev\cache\`. These environment variables are set system-wide — do not override them in project config or scripts.

| Cache | Path | Env Variable |
|---|---|---|
| Cargo registry/git/bin | `C:\Dev\cache\cargo` | `CARGO_HOME` |
| Rustup toolchains | `C:\Dev\cache\rustup` | `RUSTUP_HOME` |
| sccache | `C:\Dev\cache\sccache` | `SCCACHE_DIR` |
| npm | `C:\Dev\cache\npm` | `npm_config_cache` |
| pnpm store | `C:\Dev\cache\pnpm-store` | pnpm config |
| Yarn | `C:\Dev\cache\yarn` | `YARN_CACHE_FOLDER` |
| pip | `C:\Dev\cache\pip` | `PIP_CACHE_DIR` |
| uv | `C:\Dev\cache\uv` | `UV_CACHE_DIR` |
| NuGet | `C:\Dev\cache\nuget` | `NUGET_PACKAGES` |


#### General Cache Rules
- **Do NOT** create local cache directories (`.cargo-home/`, `.npm-cache/`, `.pip-cache/`, etc.) — global env vars point all tools to the centralized cache.

### Agent Temp Directory
If you need a temporary working directory, use `C:\Dev\agent-temp`. Do NOT use system temp or create temp dirs inside the project.

### Project Location
This project lives at `C:\Dev\repos\active\CodexJetbrains`.

## Workflow Orchestration

### 1. Plan Node Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One tack per subagent for focused execution

### 3. Self-Improvement Loop
- After ANY correction from the user: update `//reporoot/.AGENTS/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake to AGENTS.md
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

### 4. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

### 6. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

## Task Management

1. **Initialize**: Check for the existence of and read the contents of the Justfile if present.
2. **Plan First**: Write plan to `//reporoot/.AGENTS/todo.md` with checkable items
3. **Save Plan**: Once a plan has been generated, save it to `//reporoot/.AGENTS/plans/shortnamethatdescribeswhattheplanis.md`
4. **Verify Plan**: Check in before starting implementation
5. **Track Progress**: Mark items complete as you go
6. **Explain Changes**: High-level summary at each step
7. **Document Results**: Add review section to `//reporoot/.AGENTS/todo.md`
8. **Capture Lessons**: Update `//reporoot/.AGENTS/lessons.md` after corrections

## Core Principles

- **Simplicity First**: Make every change as simple as possible. Impact minimal code.
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards.
- **Minimal Impact**: Changes should only touch what's necessary. Avoid introducing bugs.
