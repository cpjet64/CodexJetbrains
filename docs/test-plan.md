# Release Test Plan (v0.1.0)

This plan covers the QA scope called out in `TODO_JetBrains.md` F3.2.

## 1. Environment Matrix

- IntelliJ IDEA 2025.2 (Windows)
- Rider 2025.2 (Windows)
- Optional smoke pass: IntelliJ IDEA 2025.2 (macOS/Linux)

## 2. Core Scenarios

### 2.1 CLI Reconnect and Lifecycle

1. Start Codex tool window and submit a prompt.
2. Force-stop Codex process externally.
3. Verify process monitor reports failure and recovers.
4. Submit a second prompt after recovery.
5. Expected:
   - UI remains responsive.
   - New submission succeeds.
   - Diagnostics capture lifecycle transitions.

### 2.2 Approval Flows (Exec + Patch)

1. Trigger an action requiring exec approval.
2. Verify approval dialog appears with command/cwd context.
3. Test each action:
   - Approve once
   - Approve for session (AGENT mode)
   - Deny
4. Trigger patch approval request and repeat decision matrix.
5. Click "Reset approvals" and verify remembered decisions clear.
6. Expected:
   - No auto-approve behavior.
   - Decisions map correctly to protocol responses.

### 2.3 Diff Apply Workflow

1. Produce a patch/diff from an assistant response.
2. Apply full patch from the diff panel.
3. Apply subset selection for multi-file patch.
4. Validate file system and editor refresh after apply.
5. Expected:
   - Success/failure summary is accurate.
   - Applied files match selected scope.

### 2.4 MCP Tools and Prompts

1. Click "Refresh" in Tools tab and Prompts tab.
2. Verify list updates from JSON-RPC path (not legacy hint mode).
3. Trigger a known MCP tool from UI.
4. Validate begin/end tool call messages and telemetry behavior.
5. Expected:
   - Tools/prompts populate consistently.
   - Tool invocation routes through protocol APIs.

## 3. Automated Verification Commands

Run with JDK 21:

```powershell
& "C:\Users\curtp\.codex\scripts\ensure-vcvars.ps1" -Quiet
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon verifyPlugin
```

## 4. Evidence to Capture

- `docs/test-runs/gradlew-test-2026-02-26.log` (baseline full test run)
- plugin verifier report artifacts (`build/reports/pluginVerifier/`)
- screenshots for install/smoke checks on target IDEs

## 5. Exit Criteria

- All scenarios in section 2 pass on required matrix.
- No blocker-level regressions in approvals, reconnect, diff, or MCP flows.
- Automated checks in section 3 complete successfully.
