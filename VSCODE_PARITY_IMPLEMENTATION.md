# VS Code Parity Implementation Checklist

**Goal**: Achieve 1:1 feature parity with VS Code Codex extension v0.4.15
**Status**: Ready to begin implementation
**Timeline**: 3-5 weeks to v1.0.0

---

## Legend
- [ ] Not started
- [→] In progress (add status note)
- [x] Completed
- [!] Blocked (add blocker details)

---

## Phase 1: TODO CodeLens Provider (Est: 3-4 days) ⭐ HIGHEST PRIORITY

### Step 1.1: Create TODO Parser
**File**: `src/main/kotlin/dev/curt/codexjb/codelens/TodoParser.kt`

- [ ] Create `TodoParser` object with `parseTodos(text: String): List<TodoItem>` function
- [ ] Implement data class `TodoItem(val body: String, val offset: Int)`
- [ ] Add string literal tracking (skip TODOs inside `"`, `'`, `` ` ``)
  - [ ] Track `inString` state variable
  - [ ] Handle escape sequences (`\"`, `\'`, etc.)
  - [ ] Test with: `val x = "TODO: not a real todo"`
- [ ] Implement single-line comment detection: `//`
  - [ ] Skip repeated comment chars (e.g., `///`)
  - [ ] Find TODO keyword after `//`
  - [ ] Extract text until end of line
  - [ ] Test with: `// TODO: implement this`
- [ ] Implement hash comment detection: `#`
  - [ ] Find TODO keyword after `#`
  - [ ] Test with: `# TODO: Python style`
- [ ] Implement SQL/Lua comment detection: `--`
  - [ ] Find TODO keyword after `--`
  - [ ] Test with: `-- TODO: SQL style`
- [ ] Implement multi-line comment detection: `/* */`
  - [ ] Find `/*` and matching `*/`
  - [ ] Remove leading `*` from each line (regex: `/^(?:[ \t]*\*?[ \t]*(?:\r?\n|$))+/`)
  - [ ] Find TODO keyword in cleaned text
  - [ ] Test with: `/* TODO: multi-line */` and `/** * TODO: javadoc */`
- [ ] Implement `skipWhitespace(text, pos)` helper
  - [ ] Skip space (32) and tab (9) only
- [ ] Implement `isWordChar(charCode)` helper
  - [ ] Return true for: 0-9, A-Z, a-z, underscore
- [ ] Implement `matchKeyword(text, pos, keyword, ignoreCase)` helper
  - [ ] Case-insensitive matching (if enabled)
  - [ ] Check word boundary (next char must not be word char)
  - [ ] Skip optional `:` or `-` after keyword
  - [ ] Skip whitespace after punctuation
  - [ ] Return position of TODO text start, or -1 if no match
  - [ ] Test: "TODO:" → match, "TODONT" → no match

### Step 1.2: Create TodoParser Tests
**File**: `src/test/kotlin/dev/curt/codexjb/codelens/TodoParserTest.kt`

- [ ] Test case: Single-line `//` comment
  - [ ] Input: `"// TODO: test"`
  - [ ] Expected: 1 item with body "test"
- [ ] Test case: Hash `#` comment
  - [ ] Input: `"# TODO: test"`
  - [ ] Expected: 1 item
- [ ] Test case: SQL `--` comment
  - [ ] Input: `"-- TODO: test"`
  - [ ] Expected: 1 item
- [ ] Test case: Multi-line `/* */` comment
  - [ ] Input: `"/* TODO: test */"`
  - [ ] Expected: 1 item
- [ ] Test case: Javadoc with leading `*`
  - [ ] Input: `"/**\n * TODO: test\n */"`
  - [ ] Expected: 1 item with body "test" (no leading `*`)
- [ ] Test case: TODO inside string should be ignored
  - [ ] Input: `"val x = \"TODO: not real\""`
  - [ ] Expected: 0 items
- [ ] Test case: Case insensitive
  - [ ] Input: `"// todo: lowercase"`, `"// Todo: mixed"`, `"// TODO: upper"`
  - [ ] Expected: 3 items
- [ ] Test case: Word boundary check
  - [ ] Input: `"// TODONT: should not match"`
  - [ ] Expected: 0 items
- [ ] Test case: Optional punctuation
  - [ ] Input: `"// TODO: with colon"`, `"// TODO- with dash"`, `"// TODO no punct"`
  - [ ] Expected: 3 items all matching
- [ ] Test case: Multiple TODOs in file
  - [ ] Input: File with 5 different TODOs
  - [ ] Expected: 5 items with correct offsets
- [ ] Run tests: `./gradlew test --tests "TodoParserTest"`
- [ ] Verify all tests pass

### Step 1.3: Create TodoCodeLensProvider
**File**: `src/main/kotlin/dev/curt/codexjb/codelens/TodoCodeLensProvider.kt`

- [ ] Create class implementing `CodeVisionProvider<Unit>`
- [ ] Implement `precomputeOnUiThread(editor: Editor): Unit`
  - [ ] Return `Unit` (no UI thread data needed)
- [ ] Implement `computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState`
  - [ ] Get document text: `editor.document.text`
  - [ ] Call `TodoParser.parseTodos(text)`
  - [ ] Map each `TodoItem` to `Pair<TextRange, CodeVisionEntry>`:
    - [ ] TextRange: `TextRange(item.offset, item.offset)` (zero-width)
    - [ ] CodeVisionEntry: Click action opens Codex with context
  - [ ] Return `CodeVisionState.Ready(lenses)`
- [ ] Implement click handler
  - [ ] Get file path from editor
  - [ ] Get line number from offset: `document.getLineNumber(offset)`
  - [ ] Convert to 1-indexed: `lineNumber + 1`
  - [ ] Create context: `TodoContext(file, line, comment)`
  - [ ] Open Codex tool window
  - [ ] Send "implement-todo" message to webview
- [ ] Add proper error handling
  - [ ] Wrap in try-catch
  - [ ] Log errors to CodexLogger

### Step 1.4: Register TodoCodeLensProvider
**File**: `src/main/resources/META-INF/plugin.xml`

- [ ] Add extension point:
```xml
<extensions defaultExtensionNs="com.intellij">
    <codeInsight.codeVisionProvider
        implementation="dev.curt.codexjb.codelens.TodoCodeLensProvider"/>
</extensions>
```
- [ ] Add setting to configuration service
  - [ ] `codex.commentCodeLensEnabled: Boolean = true`
- [ ] Conditional registration based on setting
  - [ ] Check setting in provider
  - [ ] Return empty list if disabled

### Step 1.5: Integration with Codex Tool Window
**File**: `src/main/kotlin/dev/curt/codexjb/ui/CodexToolWindowFactory.kt` (or similar)

- [ ] Add `handleImplementTodo(file, line, comment)` method
  - [ ] Open tool window if closed
  - [ ] Focus tool window
  - [ ] Create message: `{ type: "implement-todo", fileName, line, comment }`
  - [ ] Send to webview via bridge
- [ ] Handle pending TODOs (if webview not ready)
  - [ ] Queue TODO in `pendingImplementTodo` variable
  - [ ] Send when webview signals "ready"

### Step 1.6: Testing TODO CodeLens
- [ ] Create test file with TODO comments
  - [ ] Add `// TODO: test single line`
  - [ ] Add `/* TODO: test multi line */`
  - [ ] Add `# TODO: test hash`
  - [ ] Add `-- TODO: test sql`
- [ ] Run plugin via `./gradlew runIde`
- [ ] Open test file in sandbox IDE
- [ ] Verify CodeLens appears above each TODO
  - [ ] Check text: "Implement with Codex"
  - [ ] Check it's clickable
- [ ] Click CodeLens
  - [ ] Verify Codex tool window opens
  - [ ] Verify TODO context is sent to webview
- [ ] Test with setting disabled
  - [ ] Set `codex.commentCodeLensEnabled = false`
  - [ ] Verify no CodeLens appears
- [ ] Performance test
  - [ ] Open large file (5000+ lines)
  - [ ] Verify no UI lag
  - [ ] Check CodeLens appears within 1 second

---

## Phase 2: Context Menu Integration (Est: 1 day)

### Step 2.1: Create AddToChatAction
**File**: `src/main/kotlin/dev/curt/codexjb/actions/AddToChatAction.kt`

- [ ] Create class extending `AnAction`
- [ ] Override `actionPerformed(e: AnActionEvent)`
  - [ ] Get editor: `e.getData(CommonDataKeys.EDITOR) ?: return`
  - [ ] Get file: `e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return`
  - [ ] Get selection model: `editor.selectionModel`
  - [ ] Determine if has selection: `selectionModel.hasSelection()`
- [ ] If has selection:
  - [ ] Get start line: `document.getLineNumber(selectionModel.selectionStart) + 1`
  - [ ] Get end line: `document.getLineNumber(selectionModel.selectionEnd) + 1`
  - [ ] Get selected text: `selectionModel.selectedText`
  - [ ] If selection ends at column 0 and multi-line, exclude last line: `endLine--`
- [ ] If no selection:
  - [ ] startLine = null
  - [ ] endLine = null
  - [ ] text = full file content
- [ ] Create label
  - [ ] If startLine != null: `"${fileName}:${startLine}"`
  - [ ] If endLine != null and different: append `:${endLine}`
  - [ ] Else: just `fileName`
- [ ] Create context file object:
```kotlin
ContextFile(
    label = label,
    path = /* workspace relative path */,
    fsPath = file.path,
    startLine = startLine,
    endLine = endLine
)
```
- [ ] Open Codex tool window
- [ ] Send "add-context-file" message to webview
- [ ] Handle if webview not ready (queue message)
- [ ] Override `update(e: AnActionEvent)` for visibility
  - [ ] Show only if editor is active
  - [ ] Show only for file:// scheme

### Step 2.2: Register AddToChatAction
**File**: `src/main/resources/META-INF/plugin.xml`

- [ ] Add action definition:
```xml
<actions>
    <action id="codex.addToChat"
            class="dev.curt.codexjb.actions.AddToChatAction"
            text="Add to Codex Chat"
            description="Add selected code or file to Codex chat">
        <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        <add-to-group group-id="EditorTabPopupMenu" anchor="last"/>
    </action>
</actions>
```

### Step 2.3: Testing Context Menu
- [ ] Run plugin via `./gradlew runIde`
- [ ] Open any file in sandbox IDE
- [ ] Right-click in editor
  - [ ] Verify "Add to Codex Chat" appears at bottom
  - [ ] Click it
  - [ ] Verify Codex tool window opens
  - [ ] Verify full file added to context
- [ ] Select some code
  - [ ] Right-click on selection
  - [ ] Click "Add to Codex Chat"
  - [ ] Verify only selection added with line numbers
- [ ] Right-click on editor tab
  - [ ] Verify "Add to Codex Chat" appears
  - [ ] Click it
  - [ ] Verify full file added

---

## Phase 3: Settings Simplification (Est: 2-3 days)

### Step 3.1: Audit Current Settings
- [ ] List all current settings in `CodexConfigService`
- [ ] Categorize as "Simple" (user-facing) vs "Advanced" (power user)
- [ ] Identify which settings match VS Code's 3 core settings:
  - [ ] `cliExecutable` - custom CLI path
  - [ ] `commentCodeLensEnabled` - enable TODO CodeLens
  - [ ] `openOnStartup` - auto-open on project open

### Step 3.2: Add Missing Core Settings
**File**: Settings configuration

- [ ] Add `codex.commentCodeLensEnabled: Boolean`
  - [ ] Default: `true`
  - [ ] Description: "Show 'Implement with Codex' above TODO comments"
- [ ] Add `codex.openOnStartup: Boolean`
  - [ ] Default: `false`
  - [ ] Description: "Automatically open Codex tool window when project opens"
- [ ] Verify `codex.cliExecutable: String?` exists
  - [ ] Default: `null` (use bundled or PATH)
  - [ ] Description: "Custom path to Codex CLI executable"

### Step 3.3: Implement Settings UI Modes
**File**: Settings UI configuration

- [ ] Create two presentation modes:
  - [ ] **Simple Mode** (default): Show only 3 core settings
  - [ ] **Advanced Mode**: Show all settings
- [ ] Add toggle at top of settings panel:
  - [ ] Checkbox: "Show advanced settings"
  - [ ] Stored in persistent state component
  - [ ] Default: unchecked (simple mode)
- [ ] In Simple Mode:
  - [ ] Show: cliExecutable
  - [ ] Show: commentCodeLensEnabled
  - [ ] Show: openOnStartup
  - [ ] Hide: all other settings
- [ ] In Advanced Mode:
  - [ ] Show all settings grouped by category:
    - [ ] Core (3 settings)
    - [ ] Diagnostics
    - [ ] Process Management
    - [ ] Telemetry
    - [ ] UI/UX
    - [ ] Experimental

### Step 3.4: Testing Settings
- [ ] Run plugin via `./gradlew runIde`
- [ ] Open Settings → Tools → Codex
- [ ] Verify only 3 settings visible by default
- [ ] Toggle "Show advanced settings"
  - [ ] Verify all settings now visible
  - [ ] Verify grouped by category
- [ ] Change each core setting:
  - [ ] Verify changes persist
  - [ ] Verify behavior changes (e.g., CodeLens on/off)
- [ ] Compare with VS Code:
  - [ ] Open VS Code settings for Codex
  - [ ] Verify same 3 settings at top
  - [ ] Verify similar layout

---

## Phase 4: New Chat Keybinding (Est: 1 hour)

### Step 4.1: Create NewChatAction
**File**: `src/main/kotlin/dev/curt/codexjb/actions/NewChatAction.kt`

- [ ] Create class extending `AnAction`
- [ ] Override `actionPerformed(e: AnActionEvent)`
  - [ ] Get project: `e.project ?: return`
  - [ ] Open Codex tool window
  - [ ] Send "new-chat" message to webview
- [ ] Override `update(e: AnActionEvent)` for visibility
  - [ ] Enable only when Codex tool window is visible/focused

### Step 4.2: Register Keybinding
**File**: `src/main/resources/META-INF/plugin.xml`

- [ ] Add action with keybinding:
```xml
<actions>
    <action id="codex.newChat"
            class="dev.curt.codexjb.actions.NewChatAction"
            text="New Chat"
            description="Start a new Codex chat">
        <keyboard-shortcut keymap="$default" first-keystroke="ctrl N"/>
        <keyboard-shortcut keymap="Mac OS X" first-keystroke="meta N"/>
    </action>
</actions>
```
- [ ] Add condition to only trigger when Codex focused
  - [ ] Check if tool window has focus
  - [ ] Disable action otherwise to avoid conflicts

### Step 4.3: Testing Keybinding
- [ ] Run plugin via `./gradlew runIde`
- [ ] Open Codex tool window
- [ ] Press Ctrl+N (Cmd+N on Mac)
  - [ ] Verify new chat is created
  - [ ] Verify old chat is preserved
- [ ] Test in editor (without Codex focused)
  - [ ] Press Ctrl+N
  - [ ] Verify normal Ctrl+N behavior (new file)
  - [ ] Verify doesn't trigger new chat
- [ ] Check keymap settings
  - [ ] Open Settings → Keymap
  - [ ] Search for "New Chat"
  - [ ] Verify keybinding shows correctly

---

## Phase 5: Verify Git Handlers (Est: 1-2 days)

### Step 5.1: Audit Git Handler Implementations
**File**: `src/main/kotlin/dev/curt/codexjb/tooling/GitStager.kt`

- [ ] Find `git-origins` handler
  - [ ] Verify returns: `{ origins: [{root, originUrl}], homeDir }`
  - [ ] Test with multi-repo workspace
- [ ] Find `git-roots` handler
  - [ ] Verify returns: `{ roots: [String], homeDir }`
  - [ ] Test with multi-root workspace
- [ ] Find `git-current-branch` handler
  - [ ] Verify returns: `{ branch: String? }`
  - [ ] Test with detached HEAD
  - [ ] Test with no git repo

### Step 5.2: Verify Protocol Registration
- [ ] Find protocol message router/handler
- [ ] Verify all 3 git handlers are registered:
  - [ ] `git-origins`
  - [ ] `git-roots`
  - [ ] `git-current-branch`
- [ ] Add if missing

### Step 5.3: Testing Git Handlers
- [ ] Create test project with git repo
- [ ] Run plugin via `./gradlew runIde`
- [ ] Open test project
- [ ] Trigger `git-origins` via Codex interaction
  - [ ] Verify returns correct remote URL
  - [ ] Verify returns home directory
- [ ] Trigger `git-roots`
  - [ ] Verify returns correct git root path
- [ ] Trigger `git-current-branch`
  - [ ] Verify returns current branch name
- [ ] Test edge cases:
  - [ ] Project without git → handlers return null gracefully
  - [ ] Detached HEAD → returns null for branch
  - [ ] Multiple git roots → all roots returned

---

## Phase 6: Open on Startup (Est: 4 hours)

### Step 6.1: Create Startup Activity
**File**: `src/main/kotlin/dev/curt/codexjb/startup/CodexStartupActivity.kt`

- [ ] Create class implementing `StartupActivity`
- [ ] Override `runActivity(project: Project)`
  - [ ] Read setting: `CodexConfigService.openOnStartup`
  - [ ] If true:
    - [ ] Wait 1 second (let IDE finish startup)
    - [ ] Get Codex tool window
    - [ ] Call `toolWindow.show(null)`
- [ ] Add proper synchronization
  - [ ] Use `ApplicationManager.getApplication().invokeLater`
  - [ ] Ensure doesn't block startup

### Step 6.2: Register Startup Activity
**File**: `src/main/resources/META-INF/plugin.xml`

- [ ] Add startup activity:
```xml
<extensions defaultExtensionNs="com.intellij">
    <postStartupActivity
        implementation="dev.curt.codexjb.startup.CodexStartupActivity"/>
</extensions>
```

### Step 6.3: Testing Open on Startup
- [ ] Build plugin: `./gradlew buildPlugin`
- [ ] Install plugin in test IDE
- [ ] Enable setting: `codex.openOnStartup = true`
- [ ] Close project
- [ ] Reopen project
  - [ ] Verify Codex tool window opens automatically
  - [ ] Verify doesn't interfere with workspace restoration
  - [ ] Verify waits for IDE to finish loading
- [ ] Disable setting: `codex.openOnStartup = false`
- [ ] Close and reopen project
  - [ ] Verify Codex does NOT open automatically

---

## Phase 7: Git Apply Integration (Est: 2-3 days)

### Step 7.1: Add Git Detection
**File**: `src/main/kotlin/dev/curt/codexjb/tooling/PatchApplier.kt`

- [ ] Create `isGitRepository(basePath: Path): Boolean`
  - [ ] Check if `.git` directory exists
  - [ ] Use `GitRepositoryManager` if available
- [ ] Modify `doApply()` signature
  - [ ] Add parameter: `useGit: Boolean = true`

### Step 7.2: Implement Git Apply Method
**File**: `src/main/kotlin/dev/curt/codexjb/tooling/PatchApplier.kt`

- [ ] Create `applyViaGit(diff: String, basePath: Path, revert: Boolean)`
  - [ ] Write diff to temp file
  - [ ] Build command: `git apply --3way [-R] <tempFile>`
  - [ ] Execute via ProcessBuilder
  - [ ] Capture stdout and stderr
  - [ ] Parse output for status (see VS Code parser)
  - [ ] Delete temp file
  - [ ] Return: `ApplyResult(status, appliedPaths, skippedPaths, conflictedPaths)`
- [ ] Implement output parser
  - [ ] Detect: "Applied patch ... cleanly" → appliedPaths
  - [ ] Detect: "Applied patch ... with conflicts" → conflictedPaths
  - [ ] Detect: "error: patch failed" → skippedPaths
  - [ ] Detect: "U <path>" (unmerged) → conflictedPaths
  - [ ] Handle multiple files in single diff
- [ ] If revert=true: Auto-stage files
  - [ ] Run: `git add <appliedPaths>`
  - [ ] Log result

### Step 7.3: Implement Fallback Path
- [ ] In `doApply()`:
  - [ ] If `isGitRepository() && useGit`:
    - [ ] Try `applyViaGit()`
    - [ ] If fails: log warning, fall through to VFS method
  - [ ] Else:
    - [ ] Use existing VirtualFile direct write method
- [ ] Preserve existing path traversal protection
- [ ] Preserve existing VFS refresh logic

### Step 7.4: Testing Git Apply
- [ ] Create git repository test project
- [ ] Generate unified diff
- [ ] Test clean apply:
  - [ ] Apply diff
  - [ ] Verify files changed correctly
  - [ ] Verify status = "success"
  - [ ] Verify appliedPaths lists all files
- [ ] Test conflict:
  - [ ] Modify file to conflict with diff
  - [ ] Apply diff
  - [ ] Verify status = "partial-success"
  - [ ] Verify conflictedPaths lists conflicted files
- [ ] Test revert:
  - [ ] Apply diff
  - [ ] Revert diff
  - [ ] Verify files restored
  - [ ] Verify files staged in git
- [ ] Test non-git project:
  - [ ] Apply diff in project without .git
  - [ ] Verify falls back to VFS method
  - [ ] Verify still works correctly

---

## Phase 8: Integration Testing (Est: 3-5 days)

### Step 8.1: Unit Tests
- [ ] Run all existing tests: `./gradlew test`
  - [ ] Verify all 114+ tests pass
- [ ] Add tests for new features:
  - [ ] TodoParserTest (10+ test cases)
  - [ ] AddToChatActionTest
  - [ ] NewChatActionTest
  - [ ] GitApplyTest
- [ ] Achieve 80%+ coverage for new code
  - [ ] Run: `./gradlew test jacocoTestReport`
  - [ ] Check coverage report

### Step 8.2: Manual Integration Testing
- [ ] Build plugin: `./gradlew buildPlugin`
- [ ] Install in IntelliJ IDEA 2025.2
- [ ] Test each feature end-to-end:
  - [ ] TODO CodeLens: Open file → See CodeLens → Click → Codex opens
  - [ ] Context Menu: Right-click → Add to Chat → Context added
  - [ ] Keybinding: Ctrl+N → New chat created
  - [ ] Settings: Toggle each setting → Verify behavior changes
  - [ ] Open on Startup: Enable → Restart → Codex opens
  - [ ] Git Apply: Apply patch → Verify 3-way merge works
- [ ] Test error scenarios:
  - [ ] Invalid diff → Graceful error
  - [ ] No CLI installed → Clear error message
  - [ ] Network issues → Retry logic works

### Step 8.3: Side-by-Side Comparison with VS Code
- [ ] Install VS Code with Codex extension
- [ ] Open same project in both IDEs
- [ ] Compare each feature:
  - [ ] TODO CodeLens: Same appearance? Same behavior?
  - [ ] Context menu: Same location? Same text?
  - [ ] Keybindings: Same keys? Same behavior?
  - [ ] Settings: Same 3 core settings visible?
  - [ ] Apply patch: Same results? Same conflict detection?
- [ ] Document any intentional differences
- [ ] Fix any unintentional differences

### Step 8.4: Performance Testing
- [ ] Open large project (10,000+ files)
- [ ] Open file with many TODOs (100+)
  - [ ] Measure CodeLens computation time
  - [ ] Verify < 1 second
- [ ] Apply large diff (50+ files)
  - [ ] Measure apply time
  - [ ] Verify < 10 seconds
- [ ] Check memory usage
  - [ ] Verify no memory leaks
  - [ ] Check heap size after 1 hour use

---

## Phase 9: Documentation (Est: 2-3 days)

### Step 9.1: Update README.md
- [ ] Add "VS Code Parity" badge/section
- [ ] Add feature comparison table
  - [ ] List all features
  - [ ] Mark VS Code vs CodexJetbrains support
- [ ] Add screenshots of new features:
  - [ ] TODO CodeLens in action
  - [ ] Context menu integration
  - [ ] Settings panel (simple mode)
- [ ] Update installation instructions
- [ ] Add keybinding reference table

### Step 9.2: Create Migration Guide
**File**: `VSCODE_MIGRATION_GUIDE.md`

- [ ] Write introduction for VS Code users
- [ ] Create settings mapping table:
  - [ ] VS Code setting → IntelliJ setting
- [ ] Create keybinding equivalents:
  - [ ] VS Code key → IntelliJ key
- [ ] Document feature locations:
  - [ ] Where to find TODO CodeLens
  - [ ] Where to find context menu items
- [ ] Add troubleshooting section:
  - [ ] Common migration issues
  - [ ] How to import settings

### Step 9.3: Create CHANGELOG.md
- [ ] Document v1.0.0 release
- [ ] List all new features:
  - [ ] TODO CodeLens Provider
  - [ ] Context menu integration
  - [ ] Settings simplification
  - [ ] New chat keybinding
  - [ ] Git apply integration
  - [ ] Open on startup
- [ ] List breaking changes (if any)
- [ ] List bug fixes from this work

### Step 9.4: Update Inline Documentation
- [ ] Add KDoc comments to all new classes
- [ ] Add KDoc comments to all new public methods
- [ ] Reference VS Code implementation where relevant
- [ ] Add usage examples in comments

---

## Phase 10: Release Preparation (Est: 1 week)

### Step 10.1: Version Updates
- [ ] Update version in `build.gradle.kts`
  - [ ] Change to `0.9.0` (feature complete)
- [ ] Update version in `plugin.xml`
- [ ] Update changelog dates
- [ ] Tag commit: `git tag v0.9.0`

### Step 10.2: Beta Release (v0.9.0)
- [ ] Build plugin: `./gradlew buildPlugin`
- [ ] Test installation ZIP in fresh IDE
- [ ] Share with beta testers
- [ ] Collect feedback (1 week)
- [ ] Fix critical bugs
- [ ] Fix high-priority bugs

### Step 10.3: Release Candidate (v0.9.5)
- [ ] Apply all bug fixes from beta
- [ ] Update version to `0.9.5`
- [ ] Run full test suite again
- [ ] Build plugin: `./gradlew buildPlugin`
- [ ] Test installation in multiple IDEs:
  - [ ] IntelliJ IDEA 2025.2
  - [ ] PyCharm 2025.2
  - [ ] WebStorm 2025.2
- [ ] Final review of documentation
- [ ] Share RC with testers (3-5 days)
- [ ] Fix any remaining issues

### Step 10.4: Production Release (v1.0.0)
- [ ] Update version to `1.0.0`
- [ ] Final build: `./gradlew buildPlugin`
- [ ] Final test: Install and smoke test in clean IDE
- [ ] Create GitHub release
  - [ ] Upload plugin ZIP
  - [ ] Copy CHANGELOG to release notes
  - [ ] Mark as "Full Parity Release"
- [ ] Submit to JetBrains Marketplace
  - [ ] Fill out submission form
  - [ ] Upload plugin ZIP
  - [ ] Wait for approval
- [ ] Announce release:
  - [ ] GitHub announcement
  - [ ] Update README
  - [ ] Social media (optional)

---

## Completion Checklist

### Definition of Done
- [ ] Phase 1: TODO CodeLens working and tested
- [ ] Phase 2: Context menu integration working
- [ ] Phase 3: Settings simplified to 3 core + advanced mode
- [ ] Phase 4: New chat keybinding working
- [ ] Phase 5: Git handlers verified
- [ ] Phase 6: Open on startup working
- [ ] Phase 7: Git apply integration working
- [ ] Phase 8: All tests passing (114+ tests)
- [ ] Phase 9: Documentation complete
- [ ] Phase 10: v1.0.0 released

### Parity Verification
- [ ] Side-by-side test with VS Code passed
- [ ] All 6 VS Code commands implemented
- [ ] All 3 VS Code settings available
- [ ] TODO CodeLens matches VS Code exactly
- [ ] Context menu matches VS Code locations
- [ ] Keybindings match VS Code (adjusted for IntelliJ)
- [ ] Git integration matches VS Code behavior

### Quality Gates
- [ ] Code coverage ≥ 80% for new code
- [ ] No compiler warnings
- [ ] No deprecation warnings
- [ ] All tests pass in CI
- [ ] Plugin loads without errors in sandbox
- [ ] No performance regressions
- [ ] Memory usage within normal limits

---

## Current Status

**Overall Progress**: 0% (Ready to begin)

**Phase Status**:
- Phase 1 (TODO CodeLens): Not started
- Phase 2 (Context Menu): Not started
- Phase 3 (Settings): Not started
- Phase 4 (Keybinding): Not started
- Phase 5 (Git Handlers): Not started
- Phase 6 (Open on Startup): Not started
- Phase 7 (Git Apply): Not started
- Phase 8 (Testing): Not started
- Phase 9 (Documentation): Not started
- Phase 10 (Release): Not started

**Estimated Timeline**: 3-5 weeks to v1.0.0

---

## Notes

- Update checkboxes as you complete each step
- Add status notes for in-progress items: `[→] Step X.Y (70% done, debugging issue)`
- Mark blockers immediately: `[!] Step X.Y (Blocked: waiting on API clarification)`
- Run tests after each major step
- Commit frequently with descriptive messages
- Reference this doc in commit messages: `feat: implement TODO parser (VSCODE_PARITY_IMPLEMENTATION.md Phase 1.1)`

**Ready to begin! Start with Phase 1: TODO CodeLens Provider** 🚀
