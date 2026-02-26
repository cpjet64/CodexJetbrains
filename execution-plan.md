# Execution Plan

Last updated: 2026-02-26 (post-M1 normalization)
Primary tracker: `masterchecklist.md`
Backlog source: `TODO_JetBrains.md`

## 1. Governance and Constraints

- Use smallest reversible change sets.
- Preserve security boundaries and approval safeguards.
- Preserve or improve tests and coverage for touched areas.
- Never push without explicit user instruction.
- Keep docs and checklist in sync with code reality at each milestone close.

## 2. Current State

- Canonical planning docs are in place and synchronized.
- M2 (protocol + approvals technical debt) is complete.
- M1 backlog normalization is complete:
  - SSOT declared as `TODO_JetBrains.md`
  - parity completion checklist contradictions corrected
  - docs index aligned to SSOT
- Next engineering priorities are M3/M4 tasks that can be executed non-interactively.

## 3. Phase Plan

### Phase A: Standardization Gate (complete)

1. Generate `masterchecklist.md`.
2. Generate `execution-plan.md`.
3. Seed `.AGENTS/todo.md` and `.AGENTS/plans/*`.
4. Record actions in `docs/standardization-report.md` and `docs/development-progress.md`.

### Phase B: Code TODO Batch C2.1-C2.5 (complete)

1. Implement approval prompt UI path in protocol layer.
2. Remove auto-approval fallback and return explicit decisions.
3. Add JSON-RPC method bridge for legacy `sendOp` calls.
4. Align tool invocation path to concrete protocol method.
5. Rework debounce tests to assert behavior with protocol call counting.

Acceptance criteria:
- No TODO markers remain for C2.1-C2.5 in touched files.
- Refresh tools/prompts emits real protocol requests.
- Approval requests show deterministic dialog decision path.
- New/updated tests pass.

### Phase C: Validation (complete)

Required command sequence (Windows):

```powershell
& "C:\Users\curtp\.codex\scripts\ensure-vcvars.ps1" -Quiet; .\gradlew.bat test --tests "dev.curt.codexjb.ui.ChatPanelDebounceTest"
& "C:\Users\curtp\.codex\scripts\ensure-vcvars.ps1" -Quiet; .\gradlew.bat test --tests "dev.curt.codexjb.ui.ChatPanelTest"
& "C:\Users\curtp\.codex\scripts\ensure-vcvars.ps1" -Quiet; .\gradlew.bat test --tests "dev.curt.codexjb.proto.*"
& "C:\Users\curtp\.codex\scripts\ensure-vcvars.ps1" -Quiet; .\gradlew.bat test
```

If any test fails:
1. Fix failing behavior.
2. Re-run affected tests and full suite.
3. Update this plan with the root cause and resolution.

Validation outcomes:
- Targeted tests passed:
  - `dev.curt.codexjb.ui.ChatPanelDebounceTest`
  - `dev.curt.codexjb.ui.ChatPanelTest`
  - `dev.curt.codexjb.proto.ApprovalsTest`
- Full suite passed: `./gradlew test` (run with JDK 21 via `JAVA_HOME` override).

### Phase D: Backlog Sync

1. Update `TODO_JetBrains.md` status for completed C tasks.
2. Update `masterchecklist.md` M2 statuses.
3. Append milestone outcome to `docs/development-progress.md`.

### Phase E: Backlog Normalization M1 (complete)

1. Declare and document `TODO_JetBrains.md` as SSOT.
2. Reconcile parity checklist contradictions in `VSCODE_PARITY_IMPLEMENTATION.md`.
3. Align docs index references with the active tracker model.

Outcome:
- M1 checklist items marked complete.
- Tracker drift reduced between `TODO_JetBrains.md`, `todo-final.md`, and docs index.

## 4. Worktree Execution Model

- Default: one worktree per task batch under `.worktrees/`.
- Because this repo is currently dirty, preserve existing local edits and apply surgical
  in-place changes for this batch to avoid overwriting unrelated work.
- After workspace is clean and batched commits exist, resume strict per-task worktree fanout.

## 5. Exit Criteria for Next Batch

- Execute next non-blocking milestone batch from M3/M4.
- Keep `masterchecklist.md`, `execution-plan.md`, and `.AGENTS/todo.md` synchronized.
- Run verification commands appropriate to touched scope and document outcomes.
