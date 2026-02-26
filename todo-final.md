> Tracking note: `TODO_JetBrains.md` is the authoritative execution tracker.
> This file is a release-focused mirror and should stay synchronized with it.

Legend
- [ ] Not started
- [/] In progress (annotate status details)
- [x] Completed
- [!] Blocked (add blocker note and owner)

Rules
- Keep lines at or below 100 characters.
- Ensure files stay below 300 lines in length.
- Maintain single-purpose functions under 40 lines for new code.
- Update status markers before switching tasks.
- Run targeted tests when a task touches code or build logic.

Execution Order
- Follow sections from top to bottom; do not skip ahead.
- Within a section, complete tasks sequentially.

## F0. Immediate release blockers
- [x] [F0.1] Replace placeholder vendor name/email in plugin.xml with real maintainer info. (Done)
- [x] [F0.2] Supply polished description copy in plugin.xml that matches marketplace tone. (Done)
- [x] [F0.3] Ship pluginIcon.svg (1x/2x) and ensure Gradle packaging picks them up. (Done)
- [x] [F0.4] Resolve unused parameter warnings surfaced by buildPlugin (Done: renamed to _id).
- [x] [F0.5] Replace deprecated IntelliJ API calls noted in buildPlugin output. (Done)

## F1. Legal and policy readiness
- [ ] [F1.1] Add NOTICE file referencing Apache 2.0 usage and bundled third-party licenses.
- [ ] [F1.2] Update README and docs to mention Apache 2.0 and link to NOTICE.
- [ ] [F1.3] Validate bundled deps (e.g., gson) for license compatibility and document findings.
- [ ] [F1.4] Draft privacy statement and usage disclaimer for marketplace listing.
- [ ] [F1.5] Define support contact and security disclosure channel for submission forms.

## F2. Build, packaging, and install verification
- [x] [F2.1] Re-run ./gradlew buildPlugin after metadata/icon work to ensure clean output. (Done)
- [ ] [F2.2] Smoke install ZIP in IntelliJ IDEA 2025.2 (Windows) and capture screenshots.
- [ ] [F2.3] Smoke install ZIP in Rider 2025.2 (Windows) and note any IDE-specific issues.
- [ ] [F2.4] Validate sandbox run via ./gradlew runIde with CLI session handshake.
- [ ] [F2.5] Confirm diagnostics tab, diff viewer, and approvals work in the sandbox.
- [ ] [F2.6] Finalize parity checklist vs VSIX manifest and store under docs/ or parity-report.
- [ ] [F2.7] Verify keybinding, ToolWindow anchor, and context actions against VSIX behavior.
- [ ] [F2.8] Document install/uninstall steps for README and release notes.

## F3. Quality assurance and automation
- [ ] [F3.1] Close TODO_JetBrains.md T10.x gaps once tasks above are verified.
- [ ] [F3.2] Expand test plan covering CLI reconnect, approvals, diff apply, MCP flows.
- [ ] [F3.3] Ensure ./gradlew test passes on clean checkout and record run log in repo.
- [ ] [F3.4] Add targeted UI tests for icon presence and ToolWindow default state.
- [ ] [F3.5] Review CI workflow (.github/workflows/ci.yml) for release build/test steps.
- [ ] [F3.6] Add publishing workflow (sign, upload ZIP) or document manual release process.
- [ ] [F3.7] Capture performance benchmarks (perf.csv) after final build for regression tracking.

## F4. Documentation and communication
- [ ] [F4.1] Refresh README with install instructions, feature matrix, and troubleshooting.
- [ ] [F4.2] Create CHANGELOG.md entry for 0.1.0 with key features and known gaps.
- [ ] [F4.3] Update todo-release.md to reflect new blockers resolved and remaining steps.
- [ ] [F4.4] Produce quick-start guide or FAQ covering CLI setup and approvals workflow.
- [ ] [F4.5] Prepare demo GIFs/screens recording for marketplace gallery and README.
- [ ] [F4.6] Document configuration knobs (CLI path, WSL, approvals) in settings guide.

## F5. Marketplace release preparation
- [ ] [F5.1] Set plugin version, name, and change notes aligned with JetBrains requirements.
- [ ] [F5.2] Collect marketplace assets (icon 128/512, header, screenshots, video links).
- [ ] [F5.3] Draft marketplace description, feature highlights, and installation steps.
- [ ] [F5.4] Complete marketplace compliance checklist (privacy, support, EULA references).
- [ ] [F5.5] Validate JetBrains account publisher profile and permissions for upload.
- [ ] [F5.6] Plan release cadence (alpha/beta/stable) and set milestone dates.

## F6. Post-release readiness
- [ ] [F6.1] Define monitoring plan for diagnostics, telemetry, and failure alerts.
- [ ] [F6.2] Create issue templates and contribution guidelines aligned with new release.
- [ ] [F6.3] Outline roadmap for next milestones (settings UI polish, additional IDE support).
- [ ] [F6.4] Schedule retrospective once marketplace submission is accepted.
