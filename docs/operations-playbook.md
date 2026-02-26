# Post-Release Operations Playbook

## Scope

Operational checks after each plugin release to detect regressions quickly and keep user
support responsive.

## 1. Build and Verification Monitoring

For each release candidate and post-release hotfix:

1. Run `./gradlew test verifyPlugin buildPlugin`.
2. Archive outputs:
   - test report
   - Plugin Verifier report
   - built distribution ZIP
3. Confirm verifier compatibility target still includes IC-242.*

## 2. Runtime Diagnostics Monitoring

Primary runtime signals:

- Diagnostics panel stderr stream health
- Process health monitor transitions (healthy/stale/restarting/error)
- Approval prompts and patch apply success paths

When failures are reported:

1. Request issue bundle from `Codex (Unofficial): Report Issue`.
2. Review sandbox `idea.log` plus Diagnostics panel output.
3. Reproduce using `runIde` with matching platform version.

## 3. Triage and Escalation

Severity bands:

- `P0` data-loss/security boundary failures
- `P1` plugin unusable for core workflow
- `P2` degraded UX with workaround
- `P3` minor docs/polish issues

Escalation:

- `P0/P1`: open mitigation issue immediately and patch branch.
- `P2/P3`: queue for next minor/patch release.

## 4. Alerting and Owner Rotation

Current lightweight process:

- Weekly issue/diagnostic review rotation among maintainers.
- Tag urgent issues with `release-blocker`.
- Track open release blockers in `todo-release.md`.

## 5. Release Health Checklist

After publishing:

1. Install from marketplace in IDEA 2025.2 and validate startup.
2. Run one prompt/response turn and one approval-required action.
3. Confirm no critical diagnostics errors are emitted.
4. Capture outcomes in release retrospective notes.
