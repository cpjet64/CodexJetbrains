# Codex for JetBrains

Codex for JetBrains is a community-driven IntelliJ Platform plugin that talks to the Codex CLI,
opening a dedicated ToolWindow so you can review messages, approvals, and applied diffs without
leaving the IDE. The codebase now targets IntelliJ IDEA 2025.2 and relies on JDK 17 for builds and
runtime.

> This repository is under active construction. Follow day-to-day progress in `TODO_JetBrains.md`.

## Current Capabilities
- Launch the Codex ToolWindow and wire it to a long-lived Codex CLI session.
- Correlate Codex protocol events, retries, and telemetry for troubleshooting.
- Preview turn diffs and apply them through IntelliJ write actions with telemetry hooks.
- Surface CLI approval requests, status-bar indicators, and diagnostics helpers.
- Stream exec output with ANSI coloring while exposing cancel and copy affordances.

Several chat UI tasks remain in progress; see the TODO tracker for the exact breakdown.

## Prerequisites
- Java Development Kit 17 (`JAVA_HOME` must point to it before running Gradle).
- Codex CLI available on your PATH or configured in the plugin settings.
- IntelliJ-based IDE 2025.2 or newer for installation and sandbox testing.

## Getting Started
1. Clone the repository and open it in IntelliJ IDEA or another JetBrains IDE.
2. Run `./gradlew buildPlugin` to compile sources, execute tests, and produce a ZIP under
   `build/distributions`.
3. Install the ZIP inside your IDE via *Settings > Plugins > Gear Icon > Install Plugin from Disk*.
4. Launch a sandbox for manual testing with `./gradlew runIde`.

## Troubleshooting
- Open **Codex ▸ Settings** to verify the CLI path, WSL preference, and default session options. Per-project overrides are available from the same panel.
- Use the **Diagnostics** tab in the Codex tool window to stream stderr output from the CLI. The copy button exports the buffer, and the clear button resets it (a log file with rotation is written under the IDE log directory).
- If Codex becomes unresponsive, the plugin auto-restarts the CLI and marks the status bar health indicator as *Stale* or *Restarting*. Persistent errors surface as *Error*; collect diagnostics and the issue snapshot if this happens.
- Run **Codex: Report Issue** from the Tools menu to copy a full environment snapshot (global/project settings, recent diagnostics, and health metrics) to your clipboard for GitHub issue reports.

## Development Workflow
- Keep `JAVA_HOME` aligned with JDK 17; run `scripts/dev/check-java.ps1` (Windows) or
  `scripts/dev/check-java.sh` (Unix) if you are unsure.
- Execute `./gradlew test` before committing; add focused tests when touching UI or protocol
  logic.
- Track feature status in `TODO_JetBrains.md` and update the checkboxes as you move tasks
  from "Not started" to "In progress" or "Completed".
- Follow the commit format `[T<task>.<sub>] <summary>; post-test=<pass>; compare=<stdout>`
  as documented in `AGENTS.md`.

## Repository Layout
- `src/main/kotlin` - plugin sources grouped by feature packages (`core`, `proto`, `ui`).
- `src/main/resources` - plugin metadata, icons, and descriptors (`META-INF/plugin.xml`).
- `src/test/kotlin` - unit and UI tests mirroring the production package structure.
- `scripts` - helper scripts for environment setup and diagnostics.
- `TODO_JetBrains.md` - authoritative backlog and per-task status log.

## Contributing
Please read `CONTRIBUTING.md` for coding style, testing expectations, and pull request
etiquette. External contributions are welcome while the project is still stabilizing.

## License
The project is distributed under the Apache License 2.0. See `LICENSE` for details.
