# TODO / Plan

## Active Run: App Server V2 Migration + Surface Expansion

- [x] Migrate protocol startup/send/interrupt to App Server V2 (`thread/*`, `turn/*`) with legacy fallback.
- [x] Add V2 notification normalization (`thread/*`, `turn/*`, `item/*`) into existing EventBus event shapes used by UI.
- [x] Add V2 approval request handling (`item/commandExecution/requestApproval`, `item/fileChange/requestApproval`) with correct V2 decision payloads.
- [x] Expand `AppServerClient` coverage for major documented RPC surfaces (`model/list`, `app/list`, `skills/list`, `mcpServerStatus/list`, `config/*`, `account/*`, `command/exec`, `review/start`, `tool/requestUserInput`).
- [x] Add/extend tests for V2 lifecycle and notification/approval mapping.
- [x] Run verification (`test`, `buildPlugin`, `verifyPlugin`) and commit atomically.

## Active Run: Orchestrator + Backlog Normalization

- [x] Audit current planning docs and tracker freshness post-push.
- [x] Declare SSOT tracker model (`TODO_JetBrains.md` authoritative, `todo-final.md` mirror).
- [x] Reconcile parity completion-check contradictions.
- [x] Align docs index references to SSOT.
- [x] Select and execute next non-blocking implementation batch (F3.5 CI review).
- [x] Select and execute next non-blocking implementation batch (F3.3 full test + run log).
- [x] Select and execute next non-blocking implementation batch (F3.4 targeted UI tests).
- [x] Select and execute next non-blocking implementation batch (F3.6 manual release docs).
- [x] Select and execute next non-blocking implementation batch (F3.7 perf baseline).
- [x] Select and execute next non-blocking implementation batch (F3.1/F3.2 closure).
- [ ] Select and execute next non-blocking implementation batch (remaining M3/M4).

## Review

- Status: M1 normalization + F3.1-F3.7 (except manual smoke tasks) complete.
- Notes:
  - Tracker drift corrected in docs/planning files.
  - CI workflow hardened with release-oriented validation and artifacts.
  - Full test log recorded at `docs/test-runs/gradlew-test-2026-02-26.log`.
  - Targeted UI tests added for icon presence and default idle chat state.
  - Manual release/publish workflow now explicitly documented.
  - Baseline performance datapoint recorded in `perf.csv`.
  - Release test plan documented in `docs/test-plan.md`.
  - Next step is remaining M3/M4 implementation/test work.

## Review (in progress): App Server V2 Migration + Surface Expansion

- Status: implementation complete; verification complete.
- Notes:
  - Shifted runtime flow to V2 `thread/start` and `turn/start`/`turn/interrupt` with legacy fallback paths retained.
  - Added V2 notification-to-legacy event normalization so existing UI listeners continue to function.
  - Added V2 approval request/response mapping (`accept`, `acceptForSession`, `decline`, `cancel`).
  - Added App Server surface wrappers for models/apps/skills/mcp status/config/account/command/review/tool-input endpoints.
  - Added `AppServerClientV2Test` coverage for V2 notifications and approval response shape.
  - Legacy compatibility remains required while Codex versions vary.
