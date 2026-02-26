# Codex (Unofficial)

Codex (Unofficial) is a community-driven IntelliJ Platform plugin that talks to the Codex CLI,
opening a dedicated ToolWindow so you can review messages, approvals, and applied diffs without
leaving the IDE. The codebase targets IntelliJ IDEA 2025.2 and relies on JDK 21 for builds and
runtime (per 252 branch requirements).

> This repository is under active construction. Follow day-to-day progress in `TODO_JetBrains.md`.

## Current Capabilities
- Launch the Codex ToolWindow and wire it to a long-lived Codex CLI session.
- Correlate Codex protocol events, retries, and telemetry for troubleshooting.
- Preview turn diffs and apply them through IntelliJ write actions with telemetry hooks.
- Surface CLI approval requests, status-bar indicators, and diagnostics helpers.
- Stream exec output with ANSI coloring while exposing cancel and copy affordances.

Several chat UI tasks remain in progress; see the TODO tracker for the exact breakdown.

## Prerequisites
- Java Development Kit 21 (`JAVA_HOME` should point to it before running Gradle; the build also
  configures toolchains to provision 21 automatically).
- Codex CLI available on your PATH or configured in the plugin settings.
- IntelliJ-based IDE 2025.2 or newer for installation and sandbox testing.

## Getting Started
1. Clone the repository and open it in IntelliJ IDEA or another JetBrains IDE.
2. Run `./gradlew buildPlugin` to compile sources, execute tests, and produce a ZIP under
   `build/distributions`.
3. Install the ZIP inside your IDE via Settings > Plugins > Gear Icon > Install Plugin from Disk.
4. Launch a sandbox for manual testing with `./gradlew runIde`.

### Cross-platform quick smokes
- Windows: `powershell -ExecutionPolicy Bypass -File scripts/dev/windows-smoke.ps1`
- Linux: `bash scripts/dev/linux-smoke.sh`
- macOS: `bash scripts/dev/macos-smoke.sh`

Each script builds the plugin, launches a sandbox IDE, and prints the recent sandbox log. In the IDE,
open the Codex (Unofficial) tool window and verify CLI detection and a simple chat turn.

## Capturing Console Output to Log Files
For reproducible troubleshooting and CI artifacts, capture Gradle stdout/stderr to files. Use
`--console=plain` for cleaner logs and add `--info` or `--stacktrace` if you need more detail.

First, create a logs directory:
- PowerShell: `New-Item -Force -ItemType Directory -Path .\build\logs | Out-Null`
- Bash/zsh: `mkdir -p build/logs`

### Windows PowerShell
- Build/test/package:
  - `.\gradlew.bat --no-daemon --console=plain test verifyPlugin buildPlugin *>&1 | Tee-Object -FilePath .\build\logs\gradle-build.log`
- Launch sandbox:
  - `.\gradlew.bat --no-daemon --console=plain runIde *>&1 | Tee-Object -FilePath .\build\logs\runIde.log`
- Optional, record entire PS session:
  - `Start-Transcript -Path .\build\logs\session.log -Append; .\gradlew.bat --no-daemon --console=plain runIde; Stop-Transcript`

### Ubuntu/macOS (Bash/zsh)
- Build/test/package:
  - `./gradlew --no-daemon --console=plain test verifyPlugin buildPlugin 2>&1 | tee build/logs/gradle-build.log`
- Launch sandbox:
  - `./gradlew --no-daemon --console=plain runIde 2>&1 | tee build/logs/runIde.log`

### Windows CMD
- Build/test/package:
  - `gradlew.bat --no-daemon --console=plain test verifyPlugin buildPlugin > build\logs\gradle-build.log 2>&1`
- Launch sandbox:
  - `gradlew.bat --no-daemon --console=plain runIde > build\logs\runIde.log 2>&1`

### Logging wrappers (recommended)
- Windows PowerShell
  - Build/test/package with logging: `powershell -ExecutionPolicy Bypass -File scripts/dev/win-build.ps1`
  - Run sandbox with logging: `powershell -ExecutionPolicy Bypass -File scripts/dev/win-run-ide.ps1`
  - Verify pipeline (test, verifyPlugin, build): `powershell -ExecutionPolicy Bypass -File scripts/dev/verify.ps1`
  - Tail recent sandbox logs: `powershell -ExecutionPolicy Bypass -File scripts/dev/tail-ide-logs.ps1`
- Linux/macOS (Bash/zsh)
  - Build/test/package with logging: `bash scripts/dev/unix-build.sh`
  - Run sandbox with logging: `bash scripts/dev/unix-run-ide.sh`
  - Verify pipeline (test, verifyPlugin, build): `bash scripts/dev/verify.sh`
  - Clean sandbox paths: `bash scripts/dev/clean-sandbox.sh`
  - Tail recent sandbox logs: `bash scripts/dev/tail-ide-logs.sh`

## Commands by OS/Shell
- Windows PowerShell
  - Build, test, verify, package:
    - `\.\gradlew.bat test verifyPlugin buildPlugin`
  - Launch sandbox (recommended):
    - `\.\gradlew.bat runIde`
  - Launch sandbox with explicit sandbox paths (quote each -D as one arg):
    - `\.\gradlew.bat "-Didea.system.path=$env:USERPROFILE\.codex-idea-sandbox\system" "-Didea.config.path=$env:USERPROFILE\.codex-idea-sandbox\config" "-Didea.plugins.path=$env:USERPROFILE\.codex-idea-sandbox\plugins" runIde`

- Ubuntu/macOS (Bash/zsh)
  - Build, test, verify, package:
    - `./gradlew test verifyPlugin buildPlugin`
  - Launch sandbox:
    - `./gradlew runIde`
  - Launch sandbox with explicit sandbox paths:
    - `./gradlew "-Didea.system.path=$HOME/.codex-idea-sandbox/system" "-Didea.config.path=$HOME/.codex-idea-sandbox/config" "-Didea.plugins.path=$HOME/.codex-idea-sandbox/plugins" runIde`

- Windows WSL (Ubuntu)
  - Run from your Linux home (avoid `/mnt/c` to prevent socket issues):
    - `./gradlew test verifyPlugin buildPlugin`
  - Launch sandbox with explicit sandbox paths:
    - `./gradlew "-Didea.system.path=$HOME/.codex-idea-sandbox/system" "-Didea.config.path=$HOME/.codex-idea-sandbox/config" "-Didea.plugins.path=$HOME/.codex-idea-sandbox/plugins" runIde`

## Sandbox and IDE logs
In addition to console capture, the IDE writes rotating logs you can inspect after reproducing an
issue:

- Default Gradle sandbox: `build/idea-sandbox/system/log/idea.log`
- Explicit sandbox paths (as shown above):
  - Windows: `%USERPROFILE%\.codex-idea-sandbox\system\log\idea.log`
  - Linux/macOS: `$HOME/.codex-idea-sandbox/system/log/idea.log`

The Windows smoke script will print the recent sandbox log lines automatically. You can also tail
the last lines manually, for example on Windows PowerShell:

```
Get-Content $env:USERPROFILE\.codex-idea-sandbox\system\log\idea.log -Tail 200
```

## Troubleshooting
- Open Settings > Tools > Codex to verify the CLI path, WSL preference, and default session options. Per-project overrides are available in the same panel.
- Use the Diagnostics tab in the Codex tool window to stream stderr output from the CLI. The copy button exports the buffer, and the clear button resets it (a log file with rotation is written under the IDE log directory).
- If Codex becomes unresponsive, the plugin auto-restarts the CLI and marks the status bar health indicator as Stale or Restarting. Persistent errors surface as Error; collect diagnostics and the issue snapshot if this happens.
- Run Codex (Unofficial): Report Issue from the Tools menu to copy a full environment snapshot (global/project settings, recent diagnostics, and health metrics) to your clipboard for GitHub issue reports.

### Ubuntu 24.04: CLI path not sticking
If the sandbox IDE cannot find or retain your configured Codex CLI path:
- Set an absolute path in Settings > Tools > Codex > CLI path (e.g., `/usr/local/bin/codex` or `/home/<you>/.local/bin/codex`).
- Ensure the binary is executable: `chmod +x /path/to/codex`.
- Avoid shell aliases/functions; the plugin launches the executable directly.
- Verify with the Test connection button (runs `codex whoami`).
- For per-project differences, use the Project Overrides section in the same settings page.

Notes for WSL and multiple shells:
- On Windows + WSL, enable Prefer WSL when discovering Codex CLI or set a concrete path.
- On Linux, the IDE's sandbox PATH may differ from your login shell. Provide a full path to be explicit.

### Capturing logs for crashes (e.g., folder selection/terminal launch)
To help diagnose IDE-level crashes:
- Reproduce the issue, then open Help > Show Log in Explorer/Finder and inspect `idea.log`.
- Include the stack trace in the GitHub issue along with Diagnostics output from the Codex tool window.
- From a terminal, confirm the CLI works independently: `codex --version` and `codex whoami`.

## Development Workflow
- Keep `JAVA_HOME` aligned with JDK 21; run `scripts/dev/check-java.ps1` (Windows) or
  `scripts/dev/check-java.sh` (Unix) if you are unsure.
- Execute `./gradlew test` before committing; add focused tests when touching UI or protocol
  logic.
- Track feature status in `TODO_JetBrains.md` and update the checkboxes as you move tasks
  from "Not started" to "In progress" or "Completed".
- Follow the commit format `[T<task>.<sub>] <summary>; post-test=<pass>; compare=<stdout>`
  as documented in `AGENTS.md`.

## Releasing
- Versioning follows semver. See `RELEASING.md` for tagging policy and step-by-step instructions.
- Draft release notes using `RELEASE_NOTES_TEMPLATE.md` and attach artifacts from CI.
- See `CHANGELOG.md` and `todo-release.md` for current release readiness status.

## Repository Layout
- `src/main/kotlin` - plugin sources grouped by feature packages (`core`, `proto`, `ui`).
- `src/main/resources` - plugin metadata, icons, and descriptors (`META-INF/plugin.xml`).
- `src/test/kotlin` - unit and UI tests mirroring the production package structure.
- `scripts` - helper scripts for environment setup and diagnostics.
- `TODO_JetBrains.md` - authoritative backlog and per-task status log.

## Contributing
Please read `CONTRIBUTING.md` for coding style, testing expectations, and pull request
etiquette. External contributions are welcome while the project is still stabilizing.

## Documentation Index
- Quick start: `docs/quick-start.md`
- Settings guide: `docs/settings-guide.md`
- Security policy: `SECURITY.md`
- Privacy statement/disclaimer: `PRIVACY.md`
- Support channels: `SUPPORT.md`
- Third-party notices: `NOTICE`

## License
The project is distributed under the Apache License 2.0. See `LICENSE` and `NOTICE` for details.
