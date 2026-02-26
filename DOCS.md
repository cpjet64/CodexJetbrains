# Documentation Index

This file provides an overview of all documentation in the repository.

## User Documentation

### Getting Started
- **[README.md](README.md)** - Main project documentation, installation, and usage
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - How to contribute to the project
- **[RELEASING.md](RELEASING.md)** - Release process and versioning

### Development
- **[CLAUDE.md](CLAUDE.md)** - Guidance for AI assistants working with this codebase
- **[AGENTS.md](AGENTS.md)** - Development workflow, commit format, and canonical commands
- **[MIGRATION.md](MIGRATION.md)** - Gradle IntelliJ Plugin 1.x → 2.x migration guide

## Planning Documents

### Current Implementation Plan (v1.0.0)
- **[VSCODE_PARITY_IMPLEMENTATION.md](VSCODE_PARITY_IMPLEMENTATION.md)** ⭐ **PRIMARY PLAN**
  - 194+ checkbox implementation plan
  - 10 detailed phases with timelines
  - Step-by-step tasks for achieving VS Code parity
  - 3-5 week timeline to v1.0.0

### Technical Reference
- **[VSCODE_IMPLEMENTATION_DETAILS.md](VSCODE_IMPLEMENTATION_DETAILS.md)**
  - Deminified code from VS Code extension
  - Exact TODO parser implementation
  - CodeLens provider details
  - API mapping (VS Code → IntelliJ)

- **[CLI_INTEGRATION_DEEP_DIVE.md](CLI_INTEGRATION_DEEP_DIVE.md)** 🔧 TECHNICAL
  - Complete guide to Codex CLI integration
  - Discovery, launch, and communication protocol
  - Platform-specific handling (Windows, macOS, Linux, WSL)
  - Default locations and search order
  - Configuration and error handling
  - Planned improvements (version checking, shell selection)

- **[CLI_LIFECYCLE_ANALYSIS.md](CLI_LIFECYCLE_ANALYSIS.md)** 🔍 ANALYSIS
  - Audit of CLI process lifecycle issues
  - 6 issues identified with risk levels
  - All critical issues resolved

- **[CLI_LIFECYCLE_FIXES.md](CLI_LIFECYCLE_FIXES.md)** ✅ IMPLEMENTED
  - Implementation summary of lifecycle fixes
  - 5 critical fixes completed
  - Multi-project support, graceful shutdown, startup validation
  - Testing checklist and results

### Project Decisions
- **[CODEX_LAUNCHER_STATUS.md](CODEX_LAUNCHER_STATUS.md)**
  - Clarifies codex-launcher features are NOT in v1.0.0
  - Explains focus on VS Code parity only
  - Documents feature comparison table

### Future Roadmap (Post v1.0.0)
- **[JETBRAINS_FULL_INTEGRATION_ROADMAP.md](JETBRAINS_FULL_INTEGRATION_ROADMAP.md)**
| TODO_JetBrains.md | ? Active | Authoritative backlog |
  - Vision for full JetBrains IDE integration
  - 11 supported IDEs (IntelliJ, PyCharm, WebStorm, etc.)
  - 3 integration levels (Basic → Platform → IDE-Specific)
  - 18-month timeline from v1.0.0 to v3.0.0

## Release Management

### Active Tasks
- **[TODO_JetBrains.md](TODO_JetBrains.md)** - Authoritative backlog and per-task status log
- **[todo-final.md](todo-final.md)** - Release blockers and marketplace preparation
  - Legal and policy readiness (Apache 2.0, licenses)
  - Build packaging and install verification
  - Quality assurance and automation
  - Documentation and communication
  - Marketplace submission preparation

### Templates
- **[RELEASE_NOTES_TEMPLATE.md](RELEASE_NOTES_TEMPLATE.md)** - Template for release notes

## Archived Documentation

The `archive/old-planning/` directory contains superseded planning documents kept for historical reference:

- **COMPARISON.md** - Original codex-launcher comparison (superseded by newer docs)
- **PARITY_GAPS.md** - Original gap analysis (replaced by VSCODE_PARITY_IMPLEMENTATION.md)
- **VSCODE_FEATURES.md** - VS Code feature inventory (details now in IMPLEMENTATION_DETAILS)
- **VSCODE_PARITY_PLAN.md** - Old parity plan (replaced by IMPLEMENTATION.md)
- **sourceoftruth.md** - Gradle 2.x migration guide (info moved to MIGRATION.md)
- **todo-source.md** - Old migration checklist (completed)
- **ubuntu-todo.md** - Ubuntu tester follow-up (resolved)

## Issue Templates

Located in `.github/ISSUE_TEMPLATE/`:
- **bug_report.md** - Bug report template
- **feature_request.md** - Feature request template
- **documentation_update.md** - Documentation improvement template
- **maintenance_task.md** - Maintenance task template

## Pull Request Template

- **.github/pull_request_template.md** - Standard PR template

## Documentation Status

| Document | Status | Purpose |
|----------|--------|---------|
| VSCODE_PARITY_IMPLEMENTATION.md | ✅ Active | Primary implementation plan |
| VSCODE_IMPLEMENTATION_DETAILS.md | ✅ Active | Technical reference |
| CLI_INTEGRATION_DEEP_DIVE.md | ✅ Active | CLI integration guide |
| CLI_LIFECYCLE_ANALYSIS.md | ✅ Active | CLI lifecycle audit |
| CLI_LIFECYCLE_FIXES.md | ✅ Active | Lifecycle fixes summary |
| CODEX_LAUNCHER_STATUS.md | ✅ Active | Project decisions |
| JETBRAINS_FULL_INTEGRATION_ROADMAP.md | ✅ Active | Future roadmap |
| TODO_JetBrains.md | ? Active | Authoritative backlog |
| todo-final.md | ✅ Active | Release blockers |
| README.md | ✅ Active | Main documentation |
| CLAUDE.md | ✅ Active | AI assistant guide |
| AGENTS.md | ✅ Active | Developer workflow |
| MIGRATION.md | ✅ Active | Build migration guide |
| CONTRIBUTING.md | ✅ Active | Contribution guidelines |
| RELEASING.md | ✅ Active | Release process |
| archive/* | 📦 Archived | Historical reference |

## Quick Links

**For New Contributors:**
1. Start with [README.md](README.md) for project overview
2. Read [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines
3. Check [AGENTS.md](AGENTS.md) for commit format and workflow

**For Development:**
1. [VSCODE_PARITY_IMPLEMENTATION.md](VSCODE_PARITY_IMPLEMENTATION.md) - What to implement next
2. [VSCODE_IMPLEMENTATION_DETAILS.md](VSCODE_IMPLEMENTATION_DETAILS.md) - How to implement it
3. [CLAUDE.md](CLAUDE.md) - Project architecture and build commands

**For Project Planning:**
1. [TODO_JetBrains.md](TODO_JetBrains.md) - Authoritative backlog and status
2. [VSCODE_PARITY_IMPLEMENTATION.md](VSCODE_PARITY_IMPLEMENTATION.md) - v1.0.0 (3-5 weeks)
3. [JETBRAINS_FULL_INTEGRATION_ROADMAP.md](JETBRAINS_FULL_INTEGRATION_ROADMAP.md) - v1.1.0-v3.0.0 (18 months)
4. [todo-final.md](todo-final.md) - Marketplace release requirements

## Maintenance

This index is maintained manually. When adding new documentation:
1. Add entry to this file in appropriate section
2. Update status table
3. Add quick link if applicable
4. Keep archive section up to date

Last updated: 2026-01-15
