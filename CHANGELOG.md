# Changelog

All notable changes to this project are documented in this file.

## [0.1.0] - 2026-02-26

### Added

- App Server V2-first protocol flow (`thread/start`, `turn/start`, `turn/interrupt`)
  with legacy fallback compatibility.
- V2 event normalization for existing UI listeners.
- V2 approval request handling for command execution and file change flows.
- Expanded App Server RPC wrappers for models/apps/skills/config/account/review/tool-input.
- Additional protocol and UI tests, including V2 mapping coverage.
- CI hardening for workflow dispatch, concurrency, wrapper validation, and artifact capture.
- Manual release checklist and test plan documentation.

### Changed

- Stabilized refresh-debounce tests to avoid startup timing flakiness.
- Normalized tracker/documentation references to `TODO_JetBrains.md` as planning SSOT.

### Notes

- Plugin verifier is compatible with IC-242.* and currently reports one existing
  deprecated IntelliJ API usage in settings UI.
