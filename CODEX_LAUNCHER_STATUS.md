# Codex-Launcher Improvements Status

**Question**: Did the improvements from codex-launcher get implemented?
**Answer**: **NO - They were explicitly excluded from the VS Code parity plan**

---

## Decision Rationale

After auditing the VS Code extension (openai.chatgpt v0.4.15), we determined that:

1. **VS Code extension does NOT have the codex-launcher features**
2. **Goal is 1:1 parity with VS Code** (not codex-launcher)
3. **Therefore: codex-launcher features are OUT OF SCOPE for v1.0.0**

From VSCODE_PARITY_PLAN.md:
> **Phase 3: codex-launcher Evaluation ⏭️ SKIPPED**
>
> **Rationale**: After VS Code audit, determined most codex-launcher recommendations are not in VS Code extension. Maintaining VS Code parity only (no extra features).

---

## Codex-Launcher Features Analysis

### What codex-launcher Has (that VS Code doesn't)

From COMPARISON.md, codex-launcher proposed 4 main features:

#### 1. HTTP Trigger Service ❌
**Status**: NOT in VS Code, NOT in current plan
**Description**: Local HTTP server on localhost for external automation
**File**: `HttpTriggerService.kt` (in codex-launcher)
**Use case**: Trigger IDE actions from external scripts

**VS Code**: No HTTP server

#### 2. File Auto-Open on Changes ❌
**Status**: NOT in VS Code, NOT in current plan
**Description**: Automatically open files in editor after patch application
**Setting**: `codex.autoOpenModifiedFiles`

**VS Code**: Does not auto-open files after patches

#### 3. Terminal Integration ❌
**Status**: NOT in VS Code, NOT in current plan
**Description**: Launch Codex in IDE terminal instead of tool window
**File**: `CodexTerminalManager.kt` (in codex-launcher)

**VS Code**: Only has webview sidebar, no terminal mode

#### 4. Windows Shell Flexibility ⚠️
**Status**: Partially mentioned in VS Code parity plan
**Description**: Explicit shell selection (PowerShell, CMD, WSL, Git Bash)
**Setting**: `codex.windowsShell`

**VS Code**: Does not expose shell selection setting

---

## Where Would These Features Go?

Since these are NOT in VS Code, they belong in the **JetBrains Full Integration Roadmap** (post-v1.0.0):

### JETBRAINS_FULL_INTEGRATION_ROADMAP.md Location

#### HTTP Trigger Service
**Would be in**: Level 1: Platform Integration (v1.1.0-v1.3.0)
**Section**: "Cross-IDE Features → D. Plugin API"
- [ ] Public API for other plugins
- [ ] Extension points for customization
- [ ] **Webhook support** ← HTTP trigger would go here
- [ ] Custom tool providers

**Rationale**: This is a JetBrains-specific enhancement, not VS Code parity

---

#### File Auto-Open Setting
**Would be in**: Level 0: Basic (v1.0.0 enhancements) or Level 1
**Section**: Could be added as optional setting
**Status**: Low priority - not essential for parity

---

#### Terminal Integration
**Would be in**: Level 1: Platform Integration (v1.2.0)
**Section**: "1.2: Editor Integration" or "Cross-IDE Features"
**Implementation**:
- [ ] Alternative UI mode (terminal vs. tool window)
- [ ] Terminal tab for Codex
- [ ] REPL-style interaction

**Rationale**: Provides alternative UX for power users who prefer terminal

---

#### Windows Shell Selection
**Would be in**: Level 0: Basic (v1.0.0 polish)
**Section**: Settings enhancement
**Status**: Defensive coding, cross-platform robustness

This one is mentioned in VSCODE_PARITY_PLAN.md as "Feature C: Enhanced Windows Shell Support (IF in VS Code)" but marked as Unknown priority since it's not in VS Code.

---

## Current Implementation Status

### ✅ Already Implemented (Better than codex-launcher)

CodexJetbrains already has several features that codex-launcher lacks:

1. **Patch Application** ✅
   - Files: `PatchApplier.kt`, `UnifiedDiffParser.kt`, `PatchEngine.kt`
   - Better than codex-launcher (which has no patch system)

2. **Process Health Monitoring** ✅
   - Files: `ProcessHealthMonitor.kt`, `ProcessHealthMonitor.kt`
   - Better than codex-launcher (which just uses terminal)

3. **Diagnostics Panel** ✅
   - File: `DiagnosticsPanel.kt`
   - Better than codex-launcher (no dedicated diagnostics)

4. **Security Features** ✅
   - Sensitive data redaction
   - Path traversal protection
   - Command injection protection
   - Approval workflows

5. **Telemetry Service** ✅
   - Usage metrics tracking
   - Better than codex-launcher

### ❌ NOT Implemented (codex-launcher specific)

1. **HTTP Trigger Service** - Out of scope for v1.0.0
2. **Terminal Integration** - Out of scope for v1.0.0
3. **File Auto-Open** - Out of scope for v1.0.0
4. **Shell Selection UI** - Out of scope for v1.0.0

---

## Recommendation: Two-Phase Approach

### Phase 1: VS Code Parity (v1.0.0) ← **CURRENT FOCUS**
**Goal**: Match VS Code extension exactly
**Timeline**: 3-5 weeks
**Features**: Only what VS Code has (TODO CodeLens, context menu, etc.)
**codex-launcher features**: ❌ None

### Phase 2: JetBrains Enhancement (v1.1.0+) ← **FUTURE**
**Goal**: Go beyond VS Code with JetBrains-specific features
**Timeline**: Post-v1.0.0
**Features**: Platform integration + selective codex-launcher features
**codex-launcher features**: ✅ Some (where they make sense)

---

## Detailed Feature Mapping

| Feature | VS Code | codex-launcher | CodexJetbrains v1.0.0 | Future (v1.x+) |
|---------|---------|----------------|----------------------|----------------|
| TODO CodeLens | ✅ | ❌ | 🎯 Implementing | ✅ |
| Context Menu | ✅ | ❌ | 🎯 Implementing | ✅ |
| Settings (3 core) | ✅ | ❌ | 🎯 Implementing | ✅ |
| Keybindings | ✅ | ❌ | 🎯 Implementing | ✅ |
| Git Apply | ✅ | ❌ | 🎯 Implementing | ✅ |
| HTTP Trigger | ❌ | ✅ | ❌ Excluded | 💡 Consider |
| Terminal Mode | ❌ | ✅ | ❌ Excluded | 💡 Consider |
| Auto-Open Files | ❌ | ✅ | ❌ Excluded | 💡 Maybe |
| Shell Selection | ❌ | ✅ | ❌ Excluded | 💡 Maybe |
| Patch System | ⚠️ git apply | ❌ | ✅ Has better | ✅ |
| Health Monitor | ⚠️ Basic | ❌ | ✅ Has better | ✅ |
| Diagnostics | ⚠️ Output only | ❌ | ✅ Has better | ✅ |
| Security | ✅ | ⚠️ Basic | ✅ Has better | ✅ |

**Legend**:
- ✅ Has feature
- ❌ Doesn't have
- ⚠️ Partial/basic
- 🎯 Currently implementing
- 💡 Consider for future

---

## Why Exclude codex-launcher Features from v1.0.0?

### Reason 1: Goal Clarity
**Stated Goal**: "1:1 parity with VS Code extension"
- Adding non-VS Code features violates this goal
- v1.0.0 should mean "VS Code feature complete"

### Reason 2: Scope Management
- v1.0.0 already has 194+ checklist items
- Adding 4 more features = +2-3 weeks
- Risk of scope creep and delays

### Reason 3: User Expectations
- VS Code users migrating expect VS Code features
- Extra features might confuse migration
- Better to add enhancements AFTER parity achieved

### Reason 4: Quality Focus
- Better to ship excellent VS Code parity
- Than ship "VS Code + some codex-launcher" with bugs
- Can iterate post-release

---

## When to Add codex-launcher Features?

### v1.1.0-v1.3.0 (2-3 months after v1.0.0)
**Good candidates**:
- ✅ HTTP Trigger Service (if users request automation)
- ✅ Terminal Integration (alternative UX)
- ⚠️ Shell Selection (defensive coding)

**Add if**:
1. Users specifically request them
2. They align with JetBrains platform patterns
3. They don't conflict with VS Code parity

### v1.4.0+ (4-6 months after v1.0.0)
**Good candidates**:
- File Auto-Open (user preference)
- Advanced configuration options
- Power user features

---

## How to Add Them Later

If you want to add codex-launcher features post-v1.0.0:

### Step 1: Validate Need
- [ ] Gather user feedback post-v1.0.0
- [ ] Create GitHub issues for feature requests
- [ ] Survey: "What features from codex-launcher do you want?"
- [ ] Prioritize by demand

### Step 2: Design Integration
- [ ] Ensure doesn't break VS Code parity
- [ ] Design as optional/advanced features
- [ ] Mark clearly as "JetBrains enhancements"
- [ ] Add feature flags for easy disable

### Step 3: Implement in Phases
- [ ] v1.1.0: HTTP Trigger + Terminal Mode
- [ ] v1.2.0: Shell Selection + Auto-Open
- [ ] v1.3.0: Other codex-launcher features as requested

### Step 4: Document Differences
- [ ] Update COMPARISON.md with "JetBrains Enhancements" section
- [ ] Clearly mark what's in VS Code vs. what's extra
- [ ] Migration guide: "Features beyond VS Code"

---

## Specific Recommendations

### Implement Later (v1.1.0+)

#### 1. HTTP Trigger Service
**Priority**: MEDIUM (if users request automation)
**Location in roadmap**: JETBRAINS_FULL_INTEGRATION_ROADMAP.md
- Level 1, Section D (Plugin API)
**Effort**: 2-3 days
**Value**: Enables CI/CD integration, automation workflows

#### 2. Terminal Integration
**Priority**: LOW-MEDIUM (niche power user feature)
**Location in roadmap**: JETBRAINS_FULL_INTEGRATION_ROADMAP.md
- Level 1, Section 1.2 (Editor Integration)
**Effort**: 3-5 days
**Value**: Alternative UX for developers who prefer terminals

### Consider Much Later (v1.3.0+)

#### 3. File Auto-Open Setting
**Priority**: LOW
**Effort**: 2-3 hours
**Value**: User preference, not critical

#### 4. Shell Selection
**Priority**: LOW (unless Windows users report issues)
**Effort**: 1 day
**Value**: Better Windows compatibility

---

## Summary

### Current Status (2025-10-02)

**codex-launcher features**: ❌ **NOT implemented**

**Rationale**: Focused on VS Code parity (v1.0.0)

**Timeline**:
- v1.0.0 (3-5 weeks): VS Code parity only
- v1.1.0+ (post-v1.0.0): Consider selective codex-launcher features

### Where to Find in Plans

**VS Code Parity Plan** (VSCODE_PARITY_IMPLEMENTATION.md):
- Phase 3 explicitly SKIPPED codex-launcher evaluation
- Focus: 194+ checklist items for VS Code parity

**JetBrains Integration Roadmap** (JETBRAINS_FULL_INTEGRATION_ROADMAP.md):
- Level 1: Platform Integration (v1.1.0-v1.3.0)
  - HTTP Trigger → "Plugin API" section
  - Terminal Integration → "Editor Integration" section
  - Shell Selection → "Cross-IDE Features" section

### Decision

✅ **Correct approach**: VS Code parity first, JetBrains enhancements second

❌ **Avoid**: Mixing VS Code parity with codex-launcher features in v1.0.0

💡 **Future**: Revisit codex-launcher features after v1.0.0 based on user feedback

---

## Quick Reference

**Need to find codex-launcher feature implementation?**

1. Check COMPARISON.md (lines 900-999) for original recommendations
2. Check JETBRAINS_FULL_INTEGRATION_ROADMAP.md for where they'd fit
3. They are **NOT** in VSCODE_PARITY_IMPLEMENTATION.md (deliberately excluded)

**Want to implement them?**

1. Wait for v1.0.0 release
2. Gather user feedback
3. Follow JETBRAINS_FULL_INTEGRATION_ROADMAP.md
4. Implement in v1.1.0-v1.3.0 as "JetBrains enhancements"

---

**Bottom Line**: codex-launcher features are good ideas for the future, but intentionally excluded from v1.0.0 to maintain focus on VS Code parity. They'll be considered post-v1.0.0 as JetBrains-specific enhancements.
