# TODO / Plan

## Active Run: Orchestrator + Standardization Gate + Code TODO Batch

- [x] Read `Justfile` and current governance docs.
- [x] Detect missing canonical planning docs (`masterchecklist.md`, `execution-plan.md`).
- [x] Generate canonical planning docs from active backlog sources.
- [x] Save an execution plan snapshot in `.AGENTS/plans/`.
- [x] Implement code TODO batch C.1-C.5 (approvals + JSON-RPC op mapping + debounce tests).
- [x] Run formatter, lint/build, and targeted tests for touched modules.
- [x] Run full test suite verification (`./gradlew test`) if environment permits.
- [x] Update backlog trackers and progress docs with outcomes.

## Review

- Status: Complete for C2.1-C2.5 batch.
- Verification:
  - Targeted: `ChatPanelDebounceTest`, `ChatPanelTest`, `ApprovalsTest` passed.
  - Full: `./gradlew test` passed.
- Notes:
  - Existing uncommitted work was detected before this run and preserved.
  - Build/test needed JDK 21 override because system default JDK is 25.
