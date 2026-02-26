# Development Progress

## Active Run (2026-02-26)

### Milestones

- [x] Phase 0 preflight: checklist/plan docs generated.
- [x] Phase B implementation: C2.1-C2.5 completed.
- [x] Phase C validation: targeted + full tests passed.
- [x] Phase D backlog sync and completion notes.

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
