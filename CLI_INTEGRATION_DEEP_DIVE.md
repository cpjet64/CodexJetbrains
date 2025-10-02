# Codex CLI Integration - Deep Dive

**Last Updated**: 2025-10-02
**Purpose**: Complete technical reference for how CodexJetbrains discovers, launches, and interacts with the Codex CLI

---

## Table of Contents

1. [Overview](#overview)
2. [CLI Discovery](#cli-discovery)
3. [Process Lifecycle](#process-lifecycle)
4. [Configuration](#configuration)
5. [Communication Protocol](#communication-protocol)
6. [Platform-Specific Handling](#platform-specific-handling)
7. [Default Locations & Search Order](#default-locations--search-order)
8. [Error Handling](#error-handling)
9. [Testing Strategy](#testing-strategy)

---

## Overview

### Architecture Layers

```
┌─────────────────────────────────────────┐
│  UI Layer (CodexToolWindowFactory)      │
│  - Creates tool window                  │
│  - Wires components together            │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Service Layer                          │
│  - CodexConfigService (settings)        │
│  - CodexProcessService (lifecycle)      │
│  - ProcessHealthMonitor (monitoring)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Discovery & Execution Layer            │
│  - CodexPathDiscovery (find CLI)        │
│  - CodexCliExecutor (one-off commands)  │
│  - DefaultCodexProcessFactory (spawn)   │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  OS Process (Codex CLI)                 │
│  - stdin: JSON protocol messages        │
│  - stdout: JSON protocol responses      │
│  - stderr: Diagnostic logs              │
└─────────────────────────────────────────┘
```

### Key Classes

| Class | Purpose | Location |
|-------|---------|----------|
| `CodexPathDiscovery` | Finds Codex CLI executable | `core/CodexPathDiscovery.kt` |
| `CodexConfigService` | Stores user settings | `core/CodexConfigService.kt` |
| `CodexProcessService` | Manages CLI process lifecycle | `core/CodexProcessService.kt` |
| `CodexProcessConfig` | Process launch configuration | `core/CodexProcessConfig.kt` |
| `DefaultCodexProcessFactory` | Creates OS processes | `core/DefaultCodexProcessFactory.kt` |
| `CodexCliExecutor` | Executes one-off CLI commands | `core/CodexCliExecutor.kt` |
| `WslDetection` | Detects WSL environment | `core/WslDetection.kt` |

---

## CLI Discovery

### How It Works

**Entry Point**: `CodexConfigService.resolveExecutable(workingDirectory: Path?): Path?`

**Search Order**:
1. **User-configured path** (`CodexConfigService.cliPath`)
   - Stored in `~/.config/JetBrains/<IDE>/options/codex-config.xml`
   - Takes absolute priority if set
2. **Local node_modules** (`workingDirectory/node_modules/.bin/codex[.exe]`)
   - Project-local installation via npm
3. **System PATH** environment variable
   - Standard OS search paths

### Implementation: CodexPathDiscovery

```kotlin
object CodexPathDiscovery {
    fun discover(
        os: OperatingSystem = currentOs(),
        env: Map<String, String> = System.getenv(),
        workingDirectory: Path? = null
    ): Path?
}
```

**Algorithm**:
```
1. Check local node_modules/.bin/
   ├─ Windows: codex, codex.exe, codex.cmd, codex.bat, codex.ps1
   └─ Unix: codex (must have executable bit)

2. Check PATH environment variable
   ├─ Windows: Split on ';', check each dir for codex[.exe|.cmd|.bat|.ps1]
   └─ Unix: Split on ':', check each dir for executable 'codex'

3. Return first match or null
```

### Platform-Specific Discovery

#### Windows

**Executable Check**:
```kotlin
private fun isExecutableFile(p: Path, os: OperatingSystem): Boolean {
    if (!p.exists() || !p.isRegularFile()) return false
    return when (os) {
        OperatingSystem.WINDOWS -> hasWindowsExecutableExt(p)
        else -> Files.isExecutable(p)
    }
}

private fun hasWindowsExecutableExt(p: Path): Boolean {
    val name = p.fileName.toString().lowercase()
    // IMPORTANT: Do NOT accept bare "codex" without extension
    // That's often a Unix shim and fails with CreateProcess error 193
    return windowsExts.any { name.endsWith(it) }
}
```

**Accepted Extensions**: `.exe`, `.cmd`, `.bat`, `.ps1`

**Rejected**: Bare `codex` without extension (Unix shim, causes error 193)

#### macOS / Linux

**Executable Check**:
```kotlin
Files.isExecutable(p) || p.toFile().canExecute()
```

**Only Name**: `codex`

### Discovery Diagnostics

**Logged to Diagnostics Panel**:
```
Codex CLI not found; PATH="C:\Windows\system32;..."
```

```
Resolved Codex CLI: C:\Users\...\codex.exe | os=WINDOWS | wsl_preference=false | project_base="C:\Projects\MyApp"
```

---

## Process Lifecycle

### 1. Configuration

**Data Class**: `CodexProcessConfig`

```kotlin
data class CodexProcessConfig(
    val executable: Path,                    // Path to codex executable
    val arguments: List<String> = listOf("proto"),  // CLI arguments
    val workingDirectory: Path? = null,      // CWD for process
    val environment: Map<String, String> = emptyMap(),  // Extra env vars
    val inheritParentEnvironment: Boolean = true  // Inherit IDE's env
)
```

**Default Arguments**: `["proto"]`
- Launches Codex CLI in protocol mode (JSON-RPC over stdio)

### 2. Starting the Process

**Service**: `CodexProcessService.start(config: CodexProcessConfig, restart: Boolean = false): Boolean`

**Thread Safety**: Uses `ReentrantLock` for state management

**Algorithm**:
```kotlin
fun start(config: CodexProcessConfig, restart: Boolean = false): Boolean {
    lock.withLock {
        // 1. Check if already running
        if (isRunning() && !restart) return false

        // 2. Destroy existing process if restarting
        if (isRunning()) {
            val old = state?.handle
            state = null
            old?.destroy()  // Cleanup happens outside lock
        }
    }

    // 3. Start new process
    val handle = factory.start(config)

    lock.withLock {
        state = ProcessState(handle)
    }

    return true
}
```

**Factory**: `DefaultCodexProcessFactory`

```kotlin
override fun start(config: CodexProcessConfig): CodexProcessHandle {
    val command = listOf(config.executable.toString()) + config.arguments
    val builder = ProcessBuilder(command)
    config.workingDirectory?.let { builder.directory(it.toFile()) }

    if (!config.inheritParentEnvironment) {
        builder.environment().clear()
    }
    builder.environment().putAll(config.environment)
    builder.redirectErrorStream(false)  // Keep stdout/stderr separate

    val process = builder.start()
    return RealCodexProcess(config, process)
}
```

### 3. Stream Management

**Streams Exposed**:
- **stdin** (`AppendableProcessWriter`): Send JSON protocol messages
- **stdout** (`ProcessStream`): Receive JSON protocol responses
- **stderr** (`ProcessStream`): Receive diagnostic logs

**Reader Threads** (spawned in `CodexToolWindowFactory.attachReader`):

```kotlin
// stdout reader (protocol messages)
Thread {
    while (true) {
        val line = stdout.readLine() ?: break
        ProcessHealth.onStdout()  // Health check
        CodexStatusBarController.updateHealth(ProcessHealth.Status.OK)
        bus.dispatch(line)  // Parse and route protocol message
    }
}.apply { isDaemon = true; name = "codex-proto-stdout" }.start()

// stderr reader (diagnostics)
Thread {
    while (true) {
        val line = stderr.readLine() ?: break
        DiagnosticsService.append(line)  // Append to diagnostics panel
    }
}.apply { isDaemon = true; name = "codex-proto-stderr" }.start()
```

### 4. Sending Messages

**Service**: `CodexProcessService.send(line: String)`

```kotlin
fun send(line: String) {
    val handle = lock.withLock { state?.handle }
        ?: error("Codex CLI process is not running.")
    handle.stdin.writeLine(line)
    handle.stdin.flush()
}
```

**Thread Safety**: Lock ensures atomic access to process handle

### 5. Stopping the Process

**Service**: `CodexProcessService.stop()`

```kotlin
fun stop() {
    val toDestroy = lock.withLock {
        val current = state?.handle
        state = null
        current
    }
    toDestroy?.destroy()  // Cleanup outside lock
}
```

**When Called**:
- User closes IDE
- Project closes (`CodexProjectCloseListener`)
- Manual restart request

---

## Configuration

### Storage Locations

#### Application-Level Settings

**File**: `~/.config/JetBrains/<IDE>/options/codex-config.xml`

**Service**: `CodexConfigService` (implements `PersistentStateComponent`)

**Stored Settings**:
```xml
<application>
  <component name="CodexConfigService">
    <option name="cliPathStr" value="C:\path\to\codex.exe" />
    <option name="useWsl" value="false" />
    <option name="openToolWindowOnStartup" value="false" />
    <option name="defaultModel" value="gpt-5-codex" />
    <option name="defaultEffort" value="medium" />
    <option name="defaultApprovalMode" value="AGENT" />
    <option name="defaultSandboxPolicy" value="workspace-write" />
    <option name="autoOpenChangedFiles" value="false" />
    <option name="autoStageAppliedChanges" value="false" />
    <option name="autoOpenConsoleOnExec" value="false" />
    <option name="consoleVisible" value="false" />
    <option name="lastModel" value="gpt-5-codex" />
    <option name="lastEffort" value="high" />
    <option name="lastApprovalMode" value="FULL_ACCESS" />
    <option name="lastSandboxPolicy" value="workspace-write" />
    <option name="lastUsedTool" value="..." />
    <option name="lastUsedPrompt" value="..." />
    <option name="customModels">
      <list>
        <option value="gpt-5-codex" />
        <option value="gpt-5" />
        <option value="custom-model-id" />
      </list>
    </option>
  </component>
</application>
```

#### Project-Level Overrides

**File**: `.idea/codex-project-settings.xml` (per-project)

**Service**: `CodexProjectSettingsService`

**Overridable Settings**:
- `cliPathOverride`: Project-specific CLI path
- `useWslOverride`: Project-specific WSL preference
- `openToolWindowOnStartup`: Project-specific auto-open
- `defaultModel`: Project-specific default model
- `defaultEffort`: Project-specific reasoning level
- `defaultApprovalMode`: Project-specific approval mode
- `defaultSandboxPolicy`: Project-specific sandbox policy

### Effective Settings Resolution

**Method**: `CodexConfigService.effectiveSettings(project: Project?): EffectiveSettings`

**Priority**: Project overrides > Application defaults

```kotlin
fun effectiveSettings(project: Project?): EffectiveSettings {
    val projectSettings = project?.getService(CodexProjectSettingsService::class.java)
    return EffectiveSettings(
        cliPath = projectSettings?.cliPathOverride?.let(Path::of) ?: cliPath,
        useWsl = projectSettings?.useWslOverride ?: useWsl,
        // ... etc
    )
}
```

### Default Values

**Models**:
```kotlin
val MODELS: List<String> = listOf(
    "gpt-5-codex",
    "gpt-5",
)
```

**Reasoning Levels** (model-dependent):
```kotlin
val REASONING_LEVELS: List<String> = listOf("minimal", "low", "medium", "high")

private val MODEL_REASONING: Map<String, List<String>> = mapOf(
    "gpt-5-codex" to listOf("low", "medium", "high"),
    "gpt-5" to listOf("minimal", "low", "medium", "high")
)
```

**Approval Modes**:
```kotlin
val APPROVAL_LEVELS: List<ApprovalLevelOption> = listOf(
    ApprovalLevelOption(ApprovalMode.CHAT, "Read Only"),
    ApprovalLevelOption(ApprovalMode.AGENT, "Auto"),
    ApprovalLevelOption(ApprovalMode.FULL_ACCESS, "Full Access")
)
```

**Sandbox Policies**:
```kotlin
val SANDBOX_POLICIES: List<SandboxOption> = listOf(
    SandboxOption("workspace-write", "Workspace Write (recommended)"),
    SandboxOption("read-only", "Read Only"),
    SandboxOption("danger-full-access", "Full Access (unsafe)")
)
```

---

## Communication Protocol

### Protocol: MCP (Model Context Protocol)

**Transport**: JSON-RPC 2.0 over stdio (stdin/stdout)

**Message Format**:
```json
{
  "id": "unique-message-id",
  "type": "Submit|AgentMessage|ToolUse|...",
  "body": {
    "type": "specific-message-type",
    ...fields
  }
}
```

### Message Flow

```
Plugin (CodexJetbrains)          CLI (codex proto)
       │                              │
       ├──────── JSON message ───────>│  (stdin)
       │          "Submit"             │
       │                               │
       │<────── JSON response ─────────┤  (stdout)
       │       "AgentMessage"          │
       │                               │
       │<────── Diagnostic logs ───────┤  (stderr)
       │                               │
```

### Key Message Types

**Outbound (Plugin → CLI)**:
- `Submit`: User message with model/reasoning/sandbox settings
- `ToolResponse`: Response to tool invocation request
- `ExecApprovalDecision`: Approval/denial for exec command
- `PatchApprovalDecision`: Approval/denial for file patch
- `Heartbeat`: Keep-alive ping
- `CancelExecCommand`: Cancel running exec command

**Inbound (CLI → Plugin)**:
- `SessionConfigured`: Session initialization complete
- `AgentMessage`: AI assistant message
- `ToolUse`: Request to invoke a tool
- `ToolResponse`: Tool invocation result
- `ExecCommandBegin`: Exec command starting
- `ExecCommandOutput`: Exec command output chunk
- `ExecCommandEnd`: Exec command finished
- `ExecApprovalRequest`: Request approval for exec
- `PatchApprovalRequest`: Request approval for file patch
- `TokenCount`: Token usage for current turn

### Parsing & Routing

**Parser**: `EventBus.dispatch(line: String)`

```kotlin
fun dispatch(line: String) {
    val envelope = EnvelopeJson.decode(line) ?: return
    val id = envelope.id
    val type = envelope.type
    val body = envelope.body

    // Route to all registered listeners for this message type
    listeners[type]?.forEach { listener ->
        listener(id, body)
    }
}
```

**Listener Registration**:
```kotlin
bus.addListener("AgentMessage") { id, msg ->
    // Handle agent message
    val content = msg.get("content")?.asString
    chatPanel.appendAgentMessage(content)
}
```

---

## Platform-Specific Handling

### Windows

#### Standard Execution

**Executable**: `codex.exe` (or `.cmd`, `.bat`, `.ps1`)

**Process Launch**:
```kotlin
ProcessBuilder(listOf("C:\\path\\to\\codex.exe", "proto"))
```

#### WSL Execution

**Detection**: `EnvironmentInfo.isWsl` (always false on native Windows)

**Preference**: `CodexConfigService.useWsl` (user setting)

**When Enabled**:
```kotlin
val useWsl = effectiveSettings.useWsl && EnvironmentInfo.os == OperatingSystem.WINDOWS

val processConfig = if (useWsl) {
    CodexProcessConfig(
        executable = Path.of("wsl"),  // Launch WSL binary
        arguments = listOf("codex") + baseConfig.arguments,  // Run 'codex' inside WSL
        workingDirectory = baseConfig.workingDirectory,
        environment = baseConfig.environment,
        inheritParentEnvironment = baseConfig.inheritParentEnvironment
    )
} else baseConfig
```

**Result**: `wsl codex proto` instead of `codex.exe proto`

**Use Cases**:
- Codex CLI installed via `npm` inside WSL
- User prefers Linux environment for file paths
- Project uses Unix-style paths

#### Windows Path Handling

**Critical Rule**: Bare `codex` (no extension) is **rejected**

**Reason**: Package managers (e.g., Chocolatey) may create Unix shim files named `codex` without extension. These fail with:
```
CreateProcess error=193, %1 is not a valid Win32 application
```

**Solution**: Only accept files with known Windows executable extensions.

### macOS

**Executable**: `codex`

**Location**: Typically `/usr/local/bin/codex` or `~/.local/bin/codex`

**Permission Check**: `Files.isExecutable(path)`

**No Special Handling**: Standard Unix process execution

### Linux

**Executable**: `codex`

**Location**: Typically `/usr/local/bin/codex`, `/usr/bin/codex`, or `~/.local/bin/codex`

**Permission Check**: `Files.isExecutable(path)` or `path.toFile().canExecute()`

**No Special Handling**: Standard Unix process execution

### WSL (Windows Subsystem for Linux)

**Detection**:
```kotlin
object WslDetection {
    fun detect(osName: String, env: Map<String, String>, procVersion: String?): Boolean {
        // 1. Check if running Linux
        if (!osName.lowercase().contains("linux")) return false

        // 2. Check WSL_DISTRO_NAME environment variable
        if (env["WSL_DISTRO_NAME"]?.isNotBlank() == true) return true

        // 3. Check /proc/version for "microsoft" or "wsl"
        val text = (procVersion ?: "").lowercase()
        return text.contains("microsoft") || text.contains("wsl")
    }
}
```

**When Detected**: `EnvironmentInfo.isWsl = true`

**Usage**: Currently logged for diagnostics only. WSL execution mode is controlled by user preference on native Windows.

---

## Default Locations & Search Order

### 1. User-Configured Path

**Priority**: HIGHEST

**Storage**: `CodexConfigService.cliPath`

**UI**: Settings > Tools > Codex > CLI Path

**Example**:
- Windows: `C:\Users\username\AppData\Roaming\npm\codex.exe`
- macOS: `/usr/local/bin/codex`
- Linux: `/home/username/.local/bin/codex`

**Behavior**: If set, discovery is skipped entirely. This path is used directly.

### 2. Local node_modules

**Priority**: HIGH

**Path**: `<workingDirectory>/node_modules/.bin/codex[.exe]`

**Use Case**: Project has local Codex CLI installation via `npm install codex-cli`

**Example**:
```
/home/user/project/node_modules/.bin/codex
C:\Users\user\project\node_modules\.bin\codex.exe
```

**Discovery**: Only checked if `workingDirectory` is provided (i.e., project context exists)

### 3. System PATH

**Priority**: MEDIUM

**Environment Variable**: `PATH` (Unix) or `Path` (Windows)

**Common Locations**:

**Windows**:
- `C:\Program Files\nodejs\` (npm global)
- `C:\Users\<username>\AppData\Roaming\npm\`
- `C:\ProgramData\chocolatey\bin\`

**macOS**:
- `/usr/local/bin/` (Homebrew, npm global)
- `/opt/homebrew/bin/` (Apple Silicon Homebrew)
- `~/.local/bin/` (user-local)

**Linux**:
- `/usr/bin/` (system package)
- `/usr/local/bin/` (manual install)
- `~/.local/bin/` (user-local)
- `~/.nvm/versions/node/*/bin/` (nvm)

**Discovery**: Splits PATH by delimiter (`;` on Windows, `:` on Unix), checks each directory

### 4. Not Found

**Priority**: LOWEST

**Result**: `CodexPathDiscovery.discover()` returns `null`

**UI Behavior**: Tool window shows error label: "Codex CLI not found in PATH"

**Diagnostic Log**: `Codex CLI not found; PATH="..."`

**User Action Required**: User must:
1. Install Codex CLI (`npm install -g codex-cli`)
2. OR configure custom path in Settings

---

## Error Handling

### Discovery Failures

**Scenario**: CLI not found on PATH

**Detection**: `CodexConfigService.resolveExecutable()` returns `null`

**Handling** (in `CodexToolWindowFactory`):
```kotlin
val exe = effectiveSettings.cliPath ?: cfg.resolveExecutable(project.basePath?.let { Path.of(it) })
if (exe == null) {
    DiagnosticsService.append("Codex CLI not found; PATH=\"" + (System.getenv("PATH") ?: "") + "\"")
    val content = ContentFactory.getInstance()
        .createContent(JLabel("Codex CLI not found in PATH"), "Chat", false)
    toolWindow.contentManager.addContent(content)
    return  // Abort tool window initialization
}
```

**User Experience**:
- Tool window opens but shows error message
- Diagnostics panel shows PATH contents
- User must install CLI or configure path in Settings

### Process Launch Failures

**Scenario**: `ProcessBuilder.start()` throws `IOException`

**Causes**:
- Executable doesn't exist
- No execute permission (Unix)
- Invalid executable format (Windows error 193)
- Missing dependencies (e.g., Node.js not installed)

**Handling** (in `DefaultCodexProcessFactory`):
```kotlin
try {
    val process = builder.start()
    return RealCodexProcess(config, process)
} catch (ex: IOException) {
    // No explicit handling - exception propagates
    throw ex
}
```

**Current Limitation**: Process launch errors are not gracefully caught. App crashes or shows stack trace.

**TODO**: Add try-catch in `CodexToolWindowFactory` to show user-friendly error.

### Process Crashes

**Detection**: `ProcessHealthMonitor`

**Monitoring**:
```kotlin
class ProcessHealthMonitor(
    private val service: CodexProcessService,
    private val config: CodexProcessConfig
) {
    fun start() {
        thread(isDaemon = true, name = "codex-health-monitor") {
            while (!stopped) {
                Thread.sleep(5000)  // Check every 5s

                if (!service.isRunning()) {
                    // Process died - attempt auto-restart
                    service.start(config, restart = true)
                }
            }
        }
    }
}
```

**Auto-Restart**: If CLI process dies unexpectedly, monitor restarts it automatically.

**Status Bar**: Health indicator shows process state (OK, Stale, Restarting, Error)

### Communication Failures

**Scenario**: CLI stops responding to heartbeat

**Detection**: `HeartbeatScheduler`

**Behavior**:
```kotlin
class HeartbeatScheduler(
    private val sendHeartbeat: () -> Unit,
    private val log: LogSink
) {
    fun start() {
        timer.scheduleAtFixedRate(timerTask {
            val now = System.currentTimeMillis()
            val elapsed = now - lastActivity.get()

            if (elapsed > HEARTBEAT_INTERVAL_MS) {
                sendHeartbeat()  // Send ping
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)
    }
}
```

**If No Response**: Process monitor will detect dead process and restart.

---

## Testing Strategy

### Unit Tests

**CodexPathDiscoveryTest** (exists):
- Mock OS detection
- Mock environment variables
- Mock file system (Files.exists, Files.isExecutable)
- Test search order
- Test platform-specific executable checks

**WslDetectionTest** (exists):
- Mock OS name, env vars, /proc/version
- Test detection logic
- Ensure no false positives/negatives

**CodexConfigServiceTest** (exists):
- Test settings persistence
- Test effective settings resolution
- Test import/export JSON

### Integration Tests

**CodexProcessServiceTest** (exists):
- Use `FakeCodexProcessFactory` to inject mock process handles
- Test start/stop/restart
- Test thread safety (concurrent access)

**End-to-End Tests** (manual):
1. Install Codex CLI via npm
2. Launch IDE, open project
3. Verify tool window initializes
4. Send chat message
5. Verify response appears
6. Stop CLI manually (kill process)
7. Verify auto-restart occurs

### Platform-Specific Tests

**Windows**:
- Test discovery with `.exe`, `.cmd`, `.bat`, `.ps1`
- Test rejection of bare `codex` file
- Test WSL execution mode

**macOS**:
- Test discovery in `/usr/local/bin`, Homebrew paths
- Test execute permission check

**Linux**:
- Test discovery in `/usr/bin`, `/usr/local/bin`, `~/.local/bin`
- Test execute permission check

**WSL**:
- Test detection via `WSL_DISTRO_NAME`
- Test detection via `/proc/version`
- Verify correct behavior when running inside WSL

---

## Decisions & Future Work

### 1. Bundled CLI ❌ DECIDED: NO

**VS Code Approach**: Bundles Codex CLI binaries for all platforms in `.vsix`

**CodexJetbrains Decision**: User installs CLI manually

**Rationale**:
- Avoids licensing complications with OpenAI
- Keeps plugin size small
- Users control CLI version and update cadence
- Standard practice for JetBrains plugins (e.g., Docker, Python require external tools)

**Decision**: Will NOT bundle CLI in any version

### 2. CLI Version Management ✅ PLANNED

**Current**: No version checking

**Target**: v1.1.0 (post-parity)

**Implementation Plan**:
1. Add `CodexCliExecutor.getVersion(executable: Path): String?`
   ```kotlin
   fun getVersion(executable: Path): String? {
       val result = run(executable, "--version")
       if (result.isSuccess) {
           // Parse version from stdout (e.g., "codex version 1.2.3")
           return result.stdout.trim()
       }
       return null
   }
   ```

2. Check version on startup (in `CodexToolWindowFactory`):
   ```kotlin
   val version = CodexCliExecutor.getVersion(exe)
   if (version != null) {
       DiagnosticsService.append("Codex CLI version: $version")
       // Optional: Compare against minimum required version
       if (!isVersionCompatible(version, MIN_CLI_VERSION)) {
           showWarning("Codex CLI $version detected. Minimum $MIN_CLI_VERSION required.")
       }
   }
   ```

3. Show version in Diagnostics panel
4. Add "Check for CLI Updates" action in Settings

**Benefits**:
- Early detection of version incompatibilities
- Better troubleshooting support
- User awareness of CLI updates

### 3. Multiple CLI Locations ✅ ALREADY IMPLEMENTED

**Current**: Per-project CLI paths supported via `CodexProjectSettingsService.cliPathOverride`

**Status**: ✅ Complete - No additional work needed

### 4. Auto-Download CLI ❌ DECIDED: NO

**Current**: User must manually install

**Decision**: Will NOT auto-download CLI

**Rationale**:
- Reduces security concerns (no downloading executables)
- Users should manage their own tools
- Install instructions in README are sufficient
- Avoids complexity of cross-platform download/extraction

**Alternative**: Link to installation docs in error message

### 5. Shell Selection (All Platforms) ✅ PLANNED

**Current**: Uses system default shell

**Target**: v1.1.0 (post-parity)

**Implementation Plan**:

#### Add Setting
```kotlin
// In CodexConfigService.State
var selectedShell: String? = null  // null = auto-detect, or shell ID from detection
```

#### Shell Detection & Registry
```kotlin
data class ShellInfo(
    val id: String,           // Unique identifier (e.g., "powershell", "bash", "git-bash")
    val displayName: String,  // User-facing name (e.g., "PowerShell 5.x")
    val executable: Path,     // Full path to shell executable
    val commandFlag: String,  // Flag to execute command (e.g., "-Command", "-c")
    val platform: Platform    // WINDOWS, WSL, MACOS, LINUX
)

enum class Platform {
    WINDOWS, WSL, MACOS, LINUX
}

object ShellDetector {
    fun detectAvailableShells(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()

        when (EnvironmentInfo.os) {
            OperatingSystem.WINDOWS -> {
                shells.addAll(detectWindowsShells())
                if (WslDetection.isWsl()) {
                    shells.addAll(detectWslShells())
                }
            }
            OperatingSystem.MAC -> shells.addAll(detectMacShells())
            OperatingSystem.LINUX -> shells.addAll(detectLinuxShells())
        }

        return shells
    }

    private fun detectWindowsShells(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()

        // PowerShell 7+
        findOnPath("pwsh.exe")?.let {
            shells.add(ShellInfo("pwsh", "PowerShell 7+", it, "-Command", Platform.WINDOWS))
        }

        // PowerShell 5.x
        findOnPath("powershell.exe")?.let {
            shells.add(ShellInfo("powershell", "PowerShell 5.x", it, "-Command", Platform.WINDOWS))
        }

        // CMD
        System.getenv("ComSpec")?.let { Path.of(it) }?.let {
            shells.add(ShellInfo("cmd", "Command Prompt", it, "/C", Platform.WINDOWS))
        }

        // Git Bash
        findGitBash()?.let {
            shells.add(ShellInfo("git-bash", "Git Bash", it, "-c", Platform.WINDOWS))
        }

        // Cygwin
        findOnPath("bash.exe")?.takeIf { isCygwinBash(it) }?.let {
            shells.add(ShellInfo("cygwin", "Cygwin Bash", it, "-c", Platform.WINDOWS))
        }

        return shells
    }

    private fun detectWslShells(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()

        // Bash (default WSL shell)
        shells.add(ShellInfo("wsl-bash", "WSL Bash", Path.of("wsl"), "bash -c", Platform.WSL))

        // Zsh (if installed in WSL)
        if (isWslCommandAvailable("zsh")) {
            shells.add(ShellInfo("wsl-zsh", "WSL Zsh", Path.of("wsl"), "zsh -c", Platform.WSL))
        }

        // Fish (if installed in WSL)
        if (isWslCommandAvailable("fish")) {
            shells.add(ShellInfo("wsl-fish", "WSL Fish", Path.of("wsl"), "fish -c", Platform.WSL))
        }

        return shells
    }

    private fun detectMacShells(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()

        // Zsh (default on macOS Catalina+)
        findOnPath("zsh")?.let {
            shells.add(ShellInfo("zsh", "Zsh", it, "-c", Platform.MACOS))
        }

        // Bash
        findOnPath("bash")?.let {
            shells.add(ShellInfo("bash", "Bash", it, "-c", Platform.MACOS))
        }

        // Fish
        findOnPath("fish")?.let {
            shells.add(ShellInfo("fish", "Fish", it, "-c", Platform.MACOS))
        }

        return shells
    }

    private fun detectLinuxShells(): List<ShellInfo> {
        val shells = mutableListOf<ShellInfo>()

        // Check $SHELL environment variable
        val defaultShell = System.getenv("SHELL")?.let { Path.of(it) }

        // Bash
        findOnPath("bash")?.let {
            shells.add(ShellInfo("bash", "Bash", it, "-c", Platform.LINUX))
        }

        // Zsh
        findOnPath("zsh")?.let {
            shells.add(ShellInfo("zsh", "Zsh", it, "-c", Platform.LINUX))
        }

        // Fish
        findOnPath("fish")?.let {
            shells.add(ShellInfo("fish", "Fish", it, "-c", Platform.LINUX))
        }

        // Dash
        findOnPath("dash")?.let {
            shells.add(ShellInfo("dash", "Dash", it, "-c", Platform.LINUX))
        }

        return shells
    }

    private fun findGitBash(): Path? {
        // Common Git Bash locations on Windows
        val gitBashPaths = listOf(
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
            System.getenv("ProgramFiles")?.let { "$it\\Git\\bin\\bash.exe" },
            System.getenv("ProgramFiles(x86)")?.let { "$it\\Git\\bin\\bash.exe" }
        ).mapNotNull { it?.let { Path.of(it) } }

        return gitBashPaths.firstOrNull { Files.exists(it) }
    }

    private fun isCygwinBash(path: Path): Boolean {
        // Check if bash is from Cygwin (not Git Bash)
        return path.toString().contains("cygwin", ignoreCase = true)
    }

    private fun isWslCommandAvailable(command: String): Boolean {
        return try {
            val result = ProcessBuilder("wsl", "which", command)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
            result.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun getPreferredShell(shells: List<ShellInfo>): ShellInfo? {
        // Priority order
        return shells.firstOrNull { it.id == "pwsh" }        // PowerShell 7+ (Windows)
            ?: shells.firstOrNull { it.id == "powershell" }  // PowerShell 5.x (Windows)
            ?: shells.firstOrNull { it.id == "zsh" }         // Zsh (macOS/Linux)
            ?: shells.firstOrNull { it.id == "bash" }        // Bash (macOS/Linux)
            ?: shells.firstOrNull { it.id == "wsl-bash" }    // WSL Bash
            ?: shells.firstOrNull { it.id == "cmd" }         // CMD (Windows fallback)
            ?: shells.firstOrNull()                          // Any available shell
    }
}
```

#### Add UI (Settings)
```kotlin
// In CodexSettingsConfigurable
class CodexSettingsConfigurable : Configurable {
    private lateinit var shellCombo: JComboBox<String>

    override fun createComponent(): JComponent {
        val panel = JPanel(BorderLayout())

        // Detect available shells
        val availableShells = ShellDetector.detectAvailableShells()
        val shellOptions = mutableListOf("Auto-detect")
        shellOptions.addAll(availableShells.map { "${it.displayName} (${it.executable})" })

        shellCombo = JComboBox(shellOptions.toTypedArray())

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Shell:", shellCombo)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.add(formPanel, BorderLayout.NORTH)
        return panel
    }

    override fun isModified(): Boolean {
        val config = CodexConfigService.getInstance().state
        val selected = shellCombo.selectedIndex
        return if (selected == 0) {
            config.selectedShell != null  // Auto-detect means null
        } else {
            val shells = ShellDetector.detectAvailableShells()
            config.selectedShell != shells.getOrNull(selected - 1)?.id
        }
    }

    override fun apply() {
        val config = CodexConfigService.getInstance().state
        val selected = shellCombo.selectedIndex
        config.selectedShell = if (selected == 0) {
            null  // Auto-detect
        } else {
            ShellDetector.detectAvailableShells().getOrNull(selected - 1)?.id
        }
    }
}
```

#### Execution Logic
```kotlin
// In CodexToolWindowFactory or new ShellExecutor class
fun createProcessConfig(exe: Path): CodexProcessConfig {
    val availableShells = ShellDetector.detectAvailableShells()
    val config = CodexConfigService.getInstance().state

    // Get selected shell or auto-detect
    val shell = if (config.selectedShell != null) {
        availableShells.firstOrNull { it.id == config.selectedShell }
    } else {
        ShellDetector.getPreferredShell(availableShells)
    }

    return if (shell != null) {
        when (shell.platform) {
            Platform.WSL -> CodexProcessConfig(
                executable = shell.executable,
                arguments = listOf(shell.commandFlag, "codex") + baseArgs
            )
            else -> CodexProcessConfig(
                executable = shell.executable,
                arguments = listOf(shell.commandFlag, exe.toString()) + baseArgs
            )
        }
    } else {
        // Fallback: direct execution
        CodexProcessConfig(executable = exe, arguments = baseArgs)
    }
}
```

**Benefits**:
- **Universal Shell Support**: Automatically detects ALL available shells on any platform
- **Git Bash Support**: Detected on Windows alongside PowerShell/CMD
- **WSL Shell Support**: Bash, Zsh, Fish detected in WSL environments
- **macOS Shell Support**: Zsh (default), Bash, Fish
- **Linux Shell Support**: Bash, Zsh, Fish, Dash
- **User Choice**: Users can select any detected shell or use auto-detection
- **Smart Auto-Detection**: Prefers modern shells (pwsh > zsh > bash)
- **Future-Proof**: New shells automatically detected without code changes

**Supported Shells**:

| Platform | Shells Detected |
|----------|----------------|
| Windows | PowerShell 7+ (pwsh), PowerShell 5.x, CMD, Git Bash, Cygwin Bash |
| WSL | Bash, Zsh, Fish |
| macOS | Zsh (default), Bash, Fish |
| Linux | Bash, Zsh, Fish, Dash |

**Note**: The shell dropdown will dynamically populate based on what's actually installed on the user's system.

---

## Summary

### Key Takeaways

1. **Discovery is robust**: Checks user config, local node_modules, and PATH
2. **Platform-aware**: Handles Windows/macOS/Linux/WSL differences
3. **Process management is solid**: Thread-safe, auto-restart, health monitoring
4. **Configuration is flexible**: Application + project-level overrides
5. **Communication is reliable**: JSON-RPC over stdio with heartbeat

### Critical Code Paths

**Startup**:
```
CodexToolWindowFactory.createToolWindowContent()
├─ CodexConfigService.effectiveSettings(project)
├─ CodexConfigService.resolveExecutable(workingDirectory)
│  └─ CodexPathDiscovery.discover(os, env, workingDirectory)
├─ CodexProcessService.start(processConfig)
│  └─ DefaultCodexProcessFactory.start(config)
│     └─ ProcessBuilder(command).start()
└─ CodexToolWindowFactory.attachReader(service, bus, log)
   ├─ stdout reader thread (protocol messages)
   └─ stderr reader thread (diagnostics)
```

**Message Send**:
```
ChatPanel.sendMessage()
├─ ProtoSender.send(jsonMessage)
│  └─ CodexProcessService.send(line)
│     └─ handle.stdin.writeLine(line)
│        └─ BufferedWriter.write(line) + newLine() + flush()
└─ HeartbeatScheduler.markActivity()
```

**Process Restart**:
```
ProcessHealthMonitor (every 5s)
├─ CodexProcessService.isRunning()
│  └─ returns false (process died)
└─ CodexProcessService.start(config, restart=true)
   ├─ Destroy old process
   └─ Start new process
```

---

## References

- **VS Code Extension**: See `VSCODE_IMPLEMENTATION_DETAILS.md`
- **codex-launcher**: See `CODEX_LAUNCHER_STATUS.md`
- **Platform SDK**: [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/)

---

**End of Document**
