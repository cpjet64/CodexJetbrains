# Development Progress

## Active Run (2026-02-26)

### Milestones

- [x] Phase 0 preflight: checklist/plan docs generated.
- [x] Phase B implementation: C2.1-C2.5 completed.
- [x] Phase C validation: targeted + full tests passed.
- [x] Phase D backlog sync and completion notes.
- [x] Phase E backlog normalization (M1) completed.
- [x] Phase F next implementation batch (F3.5 CI review) completed.
- [x] Phase G next implementation batch (F3.3 full test + run log) completed.
- [x] Phase H next implementation batch (F3.4 targeted UI tests) completed.
- [x] Phase I next implementation batch (F3.6 manual release docs) completed.
- [x] Phase J next implementation batch (F3.7 perf baseline) completed.
- [ ] Phase K next implementation batch (remaining M3/M4) pending.

### Completed Batch

- Focus: approvals flow and protocol bridge cleanup.
- Files expected to change:
  - `src/main/kotlin/dev/curt/codexjb/proto/AppServerProtocol.kt`
  - `src/main/kotlin/dev/curt/codexjb/proto/AppServerClient.kt`
  - `src/main/kotlin/dev/curt/codexjb/ui/ChatPanel.kt`
  - `src/test/kotlin/dev/curt/codexjb/ui/ChatPanelDebounceTest.kt`

### Notes

- Existing unrelated local edits were preserved.
- Full verification required `JAVA_HOME` override to JDK 21 because local default JDK is 25.
- SSOT model enforced: `TODO_JetBrains.md` is primary; `todo-final.md` is mirror.
- CI workflow hardened for release validation and diagnostics artifact collection.
- Full test run log captured: `docs/test-runs/gradlew-test-2026-02-26.log`.
- Added targeted UI tests for icon presence and default chat/toolwindow idle state.
- Added explicit manual release checklist in `RELEASING.md`.
- Baseline benchmark captured in `perf.csv` (`gradlew buildPlugin` duration).
