Legend
- [ ] Not started
- [/] In progress (annotate when partial)
- [x] Completed
- [!] Blocked (add blocker note)

Rules
- Lines must be 100 characters or fewer.
- Files must be 300 lines or fewer.
- Functions must serve a single purpose.
- Break long functions into smaller functions.
- Create a Git commit for each subtask after its post-test passes.
- Commit message format: "[T<task>.<sub>] <short>; post-test=<pass>; compare=<summary>".
- Example: "[T1.3] Create UI crate; post-test=pass; compare=N/A".
- For each subtask: mark [/] on start, [x] after post-test pass, [!] if blocked; update before next.


Execution Order
- Complete tasks from top to bottom. Do not skip ahead.

## T1. Project bootstrap and environment
- [x] [T1.1] Verify JDK 17; set JAVA_HOME; ensure Gradle wrapper works. (JAVA_HOME script added)
- [x] [T1.2] Run `./gradlew buildPlugin`. (pass: 2025-09-26)
- [x] [T1.3] Add `gson` dep; verify shaded plugin builds. (distribution bundles gson-2.10.1)
- [x] [T1.4] Implement `CodexProcessService` start/stop/send methods tests. (unit coverage added)
- [x] [T1.5] Create `whoami` check task that runs `codex login` if needed. (auto login helper)
- [x] [T1.6] Add PATH discovery for `codex` on macOS/Linux and Windows.
- [x] [T1.7] Add WSL detection; record a flag for later use.
- [x] [T1.8] Implement graceful shutdown hook on project close.
- [x] [T1.9] Add logger facade; route to `idea.log` with categories.
- [x] [T1.10] Add configuration service storing CLI path and flags.
- [x] [T1.11] Create diagnostics action to dump env and CLI version.
- [x] [T1.12] Add status bar widget: session model and effort. (updates with combo selections)

## T2. Protocol client and correlation
- [x] [T2.1] Model Submission and Event envelopes (id, op; id, msg).
- [x] [T2.2] Implement UUID id creation helper.
- [x] [T2.3] Add listener registry for JSON events.
- [x] [T2.4] Implement tolerant JSON parsing with try/catch.
- [x] [T2.5] Add in-flight map idÃ¢â€ â€™turn; expire on TaskComplete.
- [x] [T2.6] Render StreamError and Error banners in panel.
- [x] [T2.7] Implement heartbeat ping (optional no-op submission). (scheduled via HeartbeatScheduler)
- [x] [T2.8] Add retry on broken pipe; show reconnect UI.
- [x] [T2.9] Record SessionConfigured; show session header.
- [x] [T2.10] Collect TokenCount; update usage indicators.
- [x] [T2.11] Persist last session rollout_path in logs. (SessionState wired to EventBus logging)
- [x] [T2.12] Unit test correlation logic with fake stream.

## T3. ToolWindow UI and chat basics
- [x] [T3.1] Create ToolWindow with input box and send button. (ChatPanel live in tool window)
- [x] [T3.2] Render user bubble on submit. (renderUserBubble labels added)
- [x] [T3.3] Append AgentMessageDelta tokens live. (EventBus listener updates agent bubble)
- [x] [T3.4] Seal message on AgentMessage final event. (sealAgentMessage resets state)
- [x] [T3.5] Add clear chat action; confirm before clearing. (Clear button shows confirm dialog)
- [x] [T3.6] Add copy message to clipboard action. (Context menu copies bubbles)
- [x] [T3.7] Add model and reasoning selectors in header. (Combo boxes persisted)
- [x] [T3.8] Persist last model/effort to settings. (CodexConfigService updated on selection)
- [x] [T3.9] Disable Send while a turn is active. (setSending toggles button)
- [x] [T3.10] Show spinner while streaming. (Spinner visibility tied to setSending)
- [x] [T3.11] Add accessibility labels for controls. (Accessible names on inputs/buttons)
- [x] [T3.12] Write UI tests for basic flows. (ChatPanelTest + DebounceTest passing)

## T4. Context and approvals
- [x] [T4.1] Implement OverrideTurnContext with cwd/model/effort.
- [x] [T4.2] Wire approval policy toggle (Chat/Agent/Full Access).
- [x] [T4.3] On ExecApprovalRequest show modal with command and cwd.
- [x] [T4.4] Reply ExecApproval with decision and remember for session.
- [x] [T4.5] On ApplyPatchApprovalRequest show changed files list.
- [x] [T4.6] Reply PatchApproval with decision and rationale note.
- [x] [T4.7] Store per-session approval decisions in memory.
- [x] [T4.8] Expose a 'Reset approvals' action in panel.
- [x] [T4.9] Log denied approvals with reason code.
- [x] [T4.10] Unit test approval requestÃ¢â€ â€™response mapping.
- [x] [T4.11] Persist last approval mode to settings.
- [x] [T4.12] Add warning banner for Full Access mode.

## T5. Diff preview and apply
- [x] [T5.1] Parse TurnDiff unified diff to file hunks.
- [x] [T5.2] Render two-pane diff viewer for each file. (DiffPanel highlights file selection)
- [x] [T5.3] Show file selection UI with checkboxes. (Toggleable list controls now drive apply)
- [x] [T5.4] Apply patch using WriteCommandAction edits.
- [x] [T5.5] On PatchApplyBegin show progress bar.
- [x] [T5.6] On PatchApplyEnd show success or failure summary.
- [x] [T5.7] Add Discard Patch action to close the turn. (Diff panel discard clears UI/turn)
- [x] [T5.8] Auto-open changed files option (setting). (Diff panel checkbox updates config)
- [x] [T5.9] Add Git integration: stage applied changes (optional). (Diff panel checkbox toggles staging)
- [x] [T5.10] Handle patch conflicts gracefully. (Apply dialog surfaces conflict warning)
- [x] [T5.11] Unit tests with sample diffs and virtual files. (DiffPanel + PatchApplier integration tests)
- [x] [T5.12] Telemetry counters for apply success/failure.

## T6. Exec command streaming
- [x] [T6.1] Create console pane for ExecCommandBegin events. (Tool window wires ExecConsolePanel)
- [x] [T6.2] Append ExecCommandOutputDelta chunks to console. (EventBus delivers stream to console)
- [x] [T6.3] On ExecCommandEnd print exit code and duration. (Console renders exit summary)
- [x] [T6.4] Honor cwd; show it in console header. (Header reflects command and cwd)
- [x] [T6.5] Add Kill process action to send cancel (if supported). (Cancel submission sent to CLI)
- [x] [T6.6] Write ANSI color handling for basic codes. (ANSI parser renders colored output)
- [x] [T6.7] Allow copy all to clipboard from console. (Copy action active on console pane)
- [x] [T6.8] Option to auto-open console on exec start. (Auto-open checkbox persists preference)
- [x] [T6.9] Limit buffer size to avoid memory pressure. (Console trims buffer over 100KB)
- [x] [T6.10] Unit test console with canned output events. (New tests cover lifecycle and ANSI)
- [x] [T6.11] Persist console visibility across sessions. (Visibility toggle synced to config)
- [x] [T6.12] Show approval decision status in console header. (Pending/decision state displayed)

## T7. MCP tools and prompts
- [x] [T7.1] Send ListMcpTools; render tool list. (auto-refresh on load/session with updated envelope names)
- [x] [T7.2] Add run tool action if CLI emits tool call ops. (double-click runs via submission; handles `mcp_tool_call_*` events)
- [x] [T7.3] Show tool call begin/end with timing in transcript.
- [x] [T7.4] ListCustomPrompts and render prompt library panel. (initial fetch wired to protocol response)
- [x] [T7.5] Insert prompt into input box on click.
- [x] [T7.6] Persist last used tool and prompt.
- [x] [T7.7] Handle missing MCP servers gracefully.
- [x] [T7.8] Add refresh tools button; debounce calls.
- [x] [T7.9] Unit tests: synthetic MCP list response.
- [x] [T7.10] Telemetry: count tool invocations per session. (counts on `mcp_tool_call_begin`, failures on end)
- [x] [T7.11] Expose MCP server config link in settings. (new settings entry + tool panel shortcut)
- [x] [T7.12] Add hover help for tool fields.

## T8. Settings and configuration
- [x] [T8.1] Add Settings page: CLI path, WSL toggle, open on startup. (new configurable with global options)
- [x] [T8.2] Add model default and reasoning default settings. (global defaults stored in config service)
- [x] [T8.3] Add approval mode default setting. (header combos seeded from configurable value)
- [x] [T8.4] Add sandbox policy presets (workspace-write, full). (sandbox selector wired into overrides)
- [x] [T8.5] Validate CLI path and version on save. (settings enforce executable check before apply)
- [x] [T8.6] Add 'Test connection' button to run whoami. (async CLI version check button added)
- [x] [T8.7] Persist settings per project and globally. (project service overrides layered over app defaults)
- [x] [T8.8] Export/import settings as JSON. (settings dialog supports JSON export/import)
- [x] [T8.9] Unit tests for settings serialization. (config/project service tests cover state reset)
- [x] [T8.10] Guard against invalid values with messages. (apply throws ConfigurationException on invalid paths)
- [x] [T8.11] Add reset-to-defaults button. (settings button clears all fields)
- [x] [T8.12] Ensure no UI freeze during validation. (test connection runs on pooled thread)

## T9. Resilience and diagnostics
- [ ] [T9.1] Add log file rotation and levels (info/warn/error). (logger facade only)
- [ ] [T9.2] Capture CLI stderr lines to a Diagnostics tab. (no diagnostics UI)
- [ ] [T9.3] Add copy Diagnostics to clipboard action. (no diagnostics UI)
- [ ] [T9.4] Detect stale process and auto-restart once. (no monitoring logic)
- [ ] [T9.5] Backoff strategy on repeated failures. (no retry/backoff)
- [ ] [T9.6] Expose a Report Issue action with environment dump. (action missing)
- [ ] [T9.7] Add a health indicator in the status bar. (status widget static)
- [ ] [T9.8] Track average tokens/sec for last turn. (no metrics code)
- [ ] [T9.9] Time each phase: stream, diff, apply, exec. (no timing code)
- [ ] [T9.10] Unit tests for restart/backoff logic. (logic missing)
- [ ] [T9.11] CI workflow for headless build with Gradle. (no workflow)
- [/] [T9.12] Document troubleshooting steps in README. (initial README scaffold)

## T10. Packaging and parity checks
- [ ] [T10.1] Add plugin icon and description polish. (no assets or copy updates)
- [!] [T10.2] Run buildPlugin; verify ZIP installs. (blocked: ChatPanel compile error)
- [ ] [T10.3] Smoke test on IntelliJ IDEA and Rider. (not attempted)
- [ ] [T10.4] Create parity checklist from VSIX manifest items. (not started)
- [ ] [T10.5] Verify editor context action behavior parity. (no verification done)
- [ ] [T10.6] Verify ToolWindow default placement parity. (no verification done)
- [ ] [T10.7] Verify keybinding availability and conflicts. (no verification done)
- [ ] [T10.8] Add EULA/branding notices and disclaimers. (not started)
- [ ] [T10.9] Write CHANGELOG for 0.1.0. (no changelog)
- [ ] [T10.10] Tag release and export artifacts. (not started)
- [ ] [T10.11] Post-tests for each earlier task must pass. (blocked by failing build)
- [ ] [T10.12] Prepare demo GIFs and docs. (not started)
