# Master Checklist

Source of truth inputs:
- `TODO_JetBrains.md` (authoritative backlog)
- `todo-final.md` (release mirror)
- `VSCODE_PARITY_IMPLEMENTATION.md` (phase detail)

Legend:
- `[ ]` not started
- `[/]` in progress
- `[x]` completed
- `[!]` blocked

## M1. Backlog Normalization

- [x] Declare one execution SSOT and remove tracker drift.
- [x] Reconcile parity-status contradictions in parity docs.
- [x] Keep `.AGENTS/todo.md` synchronized with active phase.

## M2. Protocol and Approval Gaps (Code TODO Batch)

- [x] C2.1 Implement JSON-RPC approval handling and reset-approvals UX wiring.
- [x] C2.2 Map legacy envelope ops to JSON-RPC methods in chat flow.
- [x] C2.3 Align MCP tool execution with supported CLI protocol path.
- [x] C2.4 Replace auto-approve behavior with explicit approval dialog decisions.
- [x] C2.5 Re-implement debounce refresh tests with deterministic mocking.

## M3. Build and Install Verification (F2)

- [ ] F2.2 Smoke install ZIP in IntelliJ IDEA 2025.2 (Windows) with screenshots.
- [ ] F2.3 Smoke install ZIP in Rider 2025.2 (Windows) and log IDE-specific issues.
- [ ] F2.4 Validate `runIde` + CLI handshake.
- [ ] F2.5 Validate diagnostics, diff viewer, and approvals end-to-end.
- [ ] F2.6 Finalize parity checklist against VSIX behavior.
- [ ] F2.7 Verify keybinding, ToolWindow anchor, and context actions.
- [ ] F2.8 Document install/uninstall flow.

## M4. QA and Automation (F3)

- [ ] F3.1 Close remaining TODO tracker gaps.
- [ ] F3.2 Expand tests for reconnect, approvals, diff apply, MCP flows.
- [x] F3.3 Run clean-checkout `./gradlew test` and log outcome.
- [x] F3.4 Add targeted UI tests (icon presence and ToolWindow default state).
- [x] F3.5 Review CI release gates.
- [ ] F3.6 Define publish workflow or document manual release.
- [ ] F3.7 Capture baseline performance metrics.

## M5. Legal, Docs, and Marketplace Readiness (F1/F4/F5)

- [ ] Add NOTICE and dependency license validation outputs.
- [ ] Add privacy statement, disclaimer, support, and security disclosure channel.
- [ ] Refresh README, changelog, quick-start/FAQ, and settings guide.
- [ ] Prepare marketplace assets and listing copy.
- [ ] Validate publisher permissions and release cadence.

## M6. Post-Release Operations (F6)

- [ ] Define monitoring/diagnostics/failure-alert playbook.
- [ ] Finalize issue templates and contribution guidance.
- [ ] Publish near-term roadmap update.
- [ ] Schedule and document release retrospective.
