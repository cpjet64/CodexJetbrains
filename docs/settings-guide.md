# Settings Guide

Path: `Settings > Tools > Codex`

## CLI Configuration

- `CLI path`: Absolute path to `codex` executable (recommended)
- `Prefer WSL` (Windows): Route CLI launch through WSL when enabled

## Session Defaults

- `Model`: Default model for new sessions
- `Reasoning/effort`: Default reasoning setting
- `Approval policy`: Controls command/patch approval behavior
- `Sandbox mode`: Local execution sandbox selection

## Project Overrides

Project-level settings can override global defaults for:

- CLI path
- WSL preference
- Approval and sandbox defaults

Use overrides when multiple repositories require different Codex behavior.

## Diagnostics and Recovery

- Diagnostics tab streams stderr output from the Codex process
- Process health state is reflected in status UI
- Use `Report Issue` action to capture a troubleshooting snapshot

## Common Setup Pitfalls

- JDK mismatch for build/test (use JDK 21)
- Non-executable CLI binary path
- PATH differences between shell and IDE sandbox process
- WSL path differences for Windows users
