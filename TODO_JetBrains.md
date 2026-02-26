# TODO_JetBrains.md

Authoritative backlog and per-task status log for this repo.
Update checkboxes here first; detailed step lists live in the linked docs.

Legend
- [ ] Not started
- [/] In progress (add status note)
- [x] Completed
- [!] Blocked (add blocker details)

Rules
- Keep lines at or below 100 characters.
- Update status markers before switching tasks.
- Run targeted tests when a task touches code or build logic.
- Keep this file in sync with source trackers until they are retired.

Sources
- todo-final.md (release blockers and marketplace prep)
- VSCODE_PARITY_IMPLEMENTATION.md (feature parity plan)
- JETBRAINS_FULL_INTEGRATION_ROADMAP.md (post-v1 roadmap)

## A. Release blockers (v0.1.0)

- [x] [F0.1] Replace placeholder vendor name/email in plugin.xml with maintainer info.
- [x] [F0.2] Supply polished description copy in plugin.xml.
- [x] [F0.3] Ship pluginIcon.svg (1x/2x) and ensure Gradle packaging picks them up.
- [x] [F0.4] Resolve unused parameter warnings surfaced by buildPlugin (rename to _id).
- [x] [F0.5] Replace deprecated IntelliJ API calls noted in buildPlugin output.

- [ ] [F1.1] Add NOTICE file referencing Apache 2.0 usage and third-party licenses.
- [ ] [F1.2] Update README and docs to mention Apache 2.0 and link to NOTICE.
- [ ] [F1.3] Validate bundled deps (e.g., gson) for license compatibility.
- [ ] [F1.4] Draft privacy statement and usage disclaimer for marketplace listing.
- [ ] [F1.5] Define support contact and security disclosure channel for submission forms.

- [x] [F2.1] Re-run ./gradlew buildPlugin after metadata/icon work to ensure clean output.
- [ ] [F2.2] Smoke install ZIP in IntelliJ IDEA 2025.2 (Windows) and capture screenshots.
- [ ] [F2.3] Smoke install ZIP in Rider 2025.2 (Windows) and note IDE-specific issues.
- [ ] [F2.4] Validate sandbox run via ./gradlew runIde with CLI session handshake.
- [ ] [F2.5] Confirm diagnostics tab, diff viewer, and approvals in the sandbox.
- [ ] [F2.6] Finalize parity checklist vs VSIX manifest under docs/ or parity-report.
- [ ] [F2.7] Verify keybinding, ToolWindow anchor, and context actions vs VSIX.
- [ ] [F2.8] Document install/uninstall steps for README and release notes.

- [ ] [F3.1] Close TODO_JetBrains.md T10.x gaps once tasks above are verified.
- [ ] [F3.2] Expand test plan covering CLI reconnect, approvals, diff apply, MCP flows.
- [x] [F3.3] Ensure ./gradlew test passes on clean checkout and record run log in repo.
- [ ] [F3.4] Add targeted UI tests for icon presence and ToolWindow default state.
- [x] [F3.5] Review CI workflow (.github/workflows/ci.yml) for release build/test steps.
- [ ] [F3.6] Add publishing workflow (sign, upload ZIP) or document manual release process.
- [ ] [F3.7] Capture performance benchmarks (perf.csv) after final build for regressions.

- [ ] [F4.1] Refresh README with install instructions, feature matrix, troubleshooting.
- [ ] [F4.2] Create CHANGELOG.md entry for 0.1.0 with key features and known gaps.
- [ ] [F4.3] Update todo-release.md to reflect blockers resolved and remaining steps.
- [ ] [F4.4] Produce quick-start guide or FAQ covering CLI setup and approvals workflow.
- [ ] [F4.5] Prepare demo GIFs/screen recordings for marketplace gallery and README.
- [ ] [F4.6] Document configuration knobs (CLI path, WSL, approvals) in settings guide.

- [ ] [F5.1] Set plugin version, name, and change notes aligned with JetBrains rules.
- [ ] [F5.2] Collect marketplace assets (icon 128/512, header, screenshots, video).
- [ ] [F5.3] Draft marketplace description, feature highlights, installation steps.
- [ ] [F5.4] Complete marketplace compliance checklist (privacy, support, EULA refs).
- [ ] [F5.5] Validate JetBrains account publisher profile and upload permissions.
- [ ] [F5.6] Plan release cadence (alpha/beta/stable) and set milestone dates.

- [ ] [F6.1] Define monitoring plan for diagnostics, telemetry, and failure alerts.
- [ ] [F6.2] Create issue templates and contribution guidelines for release.
- [ ] [F6.3] Outline roadmap for next milestones (settings UI polish, IDE support).
- [ ] [F6.4] Schedule retrospective once marketplace submission is accepted.

## B. VS Code parity (v1.0.0) — phase-level tracking

- [!] Phase 1: TODO CodeLens Provider (status needs reconciliation with repo)
- [ ] Phase 2: Context Menu Integration
- [ ] Phase 3: Settings Simplification
- [ ] Phase 4: New Chat Keybinding
- [ ] Phase 5: Verify Git Handlers
- [ ] Phase 6: Open on Startup
- [ ] Phase 7: Git Apply Integration
- [ ] Phase 8: Integration Testing
- [ ] Phase 9: Documentation
- [ ] Phase 10: Release Preparation

## C. Code TODOs / technical debt

- [x] Approvals: implement JSON-RPC approval handling and reset UI.
  - CodexToolWindowFactory.kt:681, CodexToolWindowFactory.kt:684
- [x] Protocol: map legacy envelope ops to JSON-RPC methods.
  - ChatPanel.kt:495
- [x] Tools: align tool op handling with CLI op (currently a user hint).
  - ChatPanel.kt:523
- [x] Approvals UI: show dialog instead of auto-approving exec/patch.
  - AppServerProtocol.kt:73, AppServerProtocol.kt:79
- [x] Tests: re-implement debounce tests with proper mocking.
  - ChatPanelDebounceTest.kt:31, ChatPanelDebounceTest.kt:46
