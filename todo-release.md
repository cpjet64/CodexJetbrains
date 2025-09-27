# Release Readiness Checklist

## Immediate Blockers
- Restore ChatPanel build by replacing the missing addUserMessage helper in src/main/kotlin/dev/curt/codexjb/ui/ChatPanel.kt.
- Wrap MCP tool, prompt refresh, and run requests in SubmissionEnvelope before sending (ChatPanel).
- Re-run ./gradlew buildPlugin until it passes to unblock TODO_JetBrains.md items T1.2, T3.x, T10.2, and T10.11.

## High-Priority Work
- Wire diff viewer plus apply pipeline (DiffPanel, PatchApplier) into the ToolWindow; surface discard, auto-open, and git stage toggles.
- Instantiate and integrate ExecConsolePanel; persist visibility, respect approvals, capture ANSI output.
- Implement IDE settings panels for CLI path, model and effort defaults, approval mode, sandbox presets, staging and auto-open flags; add validation and whoami test.
- Build resilience toolkit: CLI stderr diagnostics tab, process restart and backoff, logging levels, health indicator, telemetry timings.

## Packaging and QA
- Add plugin branding assets, polished description, and required notices.
- Verify build artifact installs in IntelliJ and Rider; complete parity checklist (context action, ToolWindow placement, default keybindings).
- Draft CHANGELOG 0.1.0, prepare demo media, tag release, and document troubleshooting steps.

## Testing and Automation
- Extend tests for diff apply on VirtualFile, exec console streaming, MCP flows, and settings serialization.
- Add Gradle CI workflow for headless build and tests.

## Suggested Order of Attack
1. Fix build blocker and MCP envelopes.
2. Hook diff and exec consoles, expose settings toggles.
3. Layer resilience and diagnostics features.
4. Finish packaging, documentation, and parity validation.
