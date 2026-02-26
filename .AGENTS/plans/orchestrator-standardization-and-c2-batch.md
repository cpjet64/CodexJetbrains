# Orchestrator Plan: Standardization + C2 Batch

Date: 2026-02-26

## Scope

1. Satisfy mandatory docs gate for orchestrator workflow.
2. Execute code TODO batch C2.1-C2.5 from `masterchecklist.md`.

## Plan

- [x] Read project constraints and current backlog.
- [x] Create `masterchecklist.md`.
- [x] Create `execution-plan.md`.
- [x] Initialize progress/report docs.
- [x] Implement protocol approval dialog flow and reset handling.
- [x] Implement JSON-RPC bridge for tool/prompt refresh ops.
- [x] Update tool execution path from hint-mode to protocol-mode.
- [x] Rework debounce tests with deterministic assertions.
- [x] Run targeted tests, then full `./gradlew test`.
- [x] Update trackers and finalize review notes.

## Risks

- Existing local edits in core protocol/chat files may overlap with C2 work.
- Current untracked Justfile/hooks are stack-mismatched and out of scope for C2 batch.

## Mitigation

- Make minimal line-level edits only where required.
- Run focused tests first to isolate regressions quickly.
