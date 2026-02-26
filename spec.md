# Spec: CodexJetbrains 0.1 Release Completion

## Objective
Bring CodexJetbrains to a release-ready 0.1 state by completing non-manual backlog items,
keeping protocol/app-server improvements stable, and producing complete operational and
marketplace-ready documentation.

## Scope
- Maintain App Server v2-first integration with legacy compatibility.
- Keep all completed M1/M2/M4/M5/M6 backlog work reflected in canonical trackers.
- Complete non-interactive F5 tasks (listing copy, compliance checklist, release cadence).
- Produce final pipeline status report documenting completed, partial, and blocked items.

## Constraints
- No direct push to protected branches without explicit user request.
- Preserve approval/security safeguards and existing quality gates.
- Manual tasks requiring local GUI/marketplace accounts remain documented and deferred.

## Acceptance Criteria
- `TODO_JetBrains.md`, `masterchecklist.md`, and `execution-plan.md` are synchronized.
- Non-manual F5 items are completed and documented.
- `PIPELINE-SUMMARY.md` exists with per-step status and metrics.
- Build/test baseline remains passing for targeted quality gates.
