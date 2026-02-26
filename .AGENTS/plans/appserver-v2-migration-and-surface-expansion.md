# App Server V2 Migration And Surface Expansion

## Objective
Move CodexJetbrains from legacy App Server usage (`newConversation`, `sendUserMessage`, `codex/event/*`) to V2 lifecycle + event semantics while preserving compatibility with older method names. Expose a broad App Server RPC surface so the plugin can fully leverage models/apps/skills/config/auth/system endpoints.

## Scope
- Protocol client (`AppServerClient`)
- Protocol orchestrator (`AppServerProtocol`)
- Event normalization into existing UI-facing bus events
- Targeted tests in `proto` package

## Plan
1. Add V2 lifecycle methods in `AppServerClient`:
   - `threadStart`, `threadResume`, `turnStart`, `turnSteer`, `turnInterrupt`
   - Keep legacy fallbacks (`newConversation`, `sendUserMessage`, `interruptConversation`) for compatibility.
2. Update `AppServerProtocol.start()` and message flow:
   - Prefer `thread/start`, store `threadId`.
   - `sendMessage()` prefers `turn/start`.
   - `interrupt()` prefers `turn/interrupt`.
3. Normalize V2 notifications in `AppServerClient.handleNotification()`:
   - Map key `turn/*` and `item/*` notifications to existing UI event types (`task_started`, `AgentMessageDelta`, `AgentMessage`, `turn_diff`, tool begin/end, reasoning deltas).
4. Extend server-request approval handling:
   - Support V2 approval request methods.
   - Return V2 decision payloads (`accept`, `acceptForSession`, `decline`, `cancel`) while preserving legacy response shape handling.
5. Expand RPC wrappers for broader App Server features:
   - `model/list`, `app/list`, `skills/list`, `mcpServerStatus/list`, `config/read`, `config/value/write`, `config/batchWrite`, `account/read`, `account/login/start`, `account/logout`, `account/rateLimits/read`, `command/exec`, `review/start`, `tool/requestUserInput`.
6. Add tests:
   - V2 start/turn request encoding and fallback behavior.
   - V2 notification mapping into EventBus.
   - V2 approval request/response mapping.
7. Verify:
   - `./gradlew.bat --no-daemon test`
   - `./gradlew.bat --no-daemon buildPlugin`
   - `./gradlew.bat --no-daemon verifyPlugin`

## Risks
- Different Codex CLI versions may vary in available methods and payload shapes.
- V2 notifications can include richer payloads than current UI expects.

## Mitigation
- Keep fallback logic for method names and legacy pathways.
- Normalize incoming events into existing event contracts consumed by the UI.
- Add focused tests for mapper behavior and maintain compatibility paths.
