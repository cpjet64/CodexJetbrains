# Codex CLI Process Lifecycle Analysis

**Date**: 2025-10-02
**Updated**: 2025-10-02 (Fixes implemented)
**Purpose**: Audit of CLI process startup, running, and shutdown to ensure proper resource management

---

## ✅ All Critical Issues Fixed

All 5 priority issues have been implemented:

1. ✅ **Fixed multi-project issue** - Service is now project-scoped
2. ✅ **No app shutdown hook needed** - Project-scoped service cleans up automatically
3. ✅ **Improved process shutdown** - Graceful + forceful fallback with timeouts
4. ✅ **Added startup validation** - Waits for SessionConfigured with 5s timeout
5. ✅ **Reader threads cleanup** - Threads interrupted and joined on disposal

---

## Current State

### ✅ What's Working Well

#### 1. Project Close Handling
**Location**: `CodexProjectCloseListener.kt`

```kotlin
override fun projectClosed(project: Project) {
    val service = ApplicationManager.getApplication()
        .getService(CodexProcessService::class.java)
    CodexShutdown(ServiceStopper(service)).onProjectClosed()
}
```

**Status**: ✅ **GOOD**
- Registered in plugin.xml as `ProjectManagerListener`
- Calls `CodexProcessService.stop()` when project closes
- Clean shutdown pattern

#### 2. Process Management
**Location**: `CodexProcessService.kt`

**Start**:
```kotlin
fun start(config: CodexProcessConfig, restart: Boolean = false): Boolean {
    lock.withLock {
        // Destroy old process if restarting
        if (current != null) {
            toDestroy = current
        }
    }
    toDestroy?.destroy()  // Cleanup outside lock
    val handle = factory.start(config)
    // Store new handle
}
```

**Stop**:
```kotlin
fun stop() {
    val toDestroy = lock.withLock {
        val current = state?.handle
        state = null
        current
    }
    toDestroy?.destroy()
}
```

**Status**: ✅ **GOOD**
- Thread-safe with ReentrantLock
- Proper cleanup when stopping
- Handles restart scenario

#### 3. Tool Window Disposal
**Location**: `CodexToolWindowFactory.kt:246-251`

```kotlin
content.setDisposer(object : Disposable {
    override fun dispose() {
        sender.setOnSendListener(null)
        heartbeat.dispose()
        processMonitor.close()
    }
})
```

**Status**: ✅ **GOOD**
- Heartbeat scheduler shutdown
- Process health monitor cleanup
- Cleans up listeners

#### 4. Health Monitoring
**Location**: `ProcessHealthMonitor.kt`

```kotlin
class ProcessHealthMonitor : AutoCloseable {
    override fun close() {
        scheduler.shutdownNow()
    }
}
```

**Status**: ✅ **GOOD**
- Implements AutoCloseable
- Shuts down scheduler thread properly
- Called from tool window disposer

---

## ⚠️ Potential Issues

### Issue 1: IDE Shutdown - No Application-Level Cleanup

**Problem**: Process only stops on **project close**, not on **IDE shutdown**

**Scenario**:
1. User has project open with Codex running
2. User closes entire IDE (File → Exit)
3. IDE shuts down
4. ❓ What happens to the Codex CLI process?

**Current Behavior**:
- `projectClosed()` is called by IDE on shutdown (IntelliJ closes all projects first)
- **BUT**: If IDE crashes or is force-killed, process may be orphaned

**Risk Level**: 🟡 **MEDIUM**
- Normal shutdown: ✅ Likely OK (projectClosed is called)
- Crash/Force-kill: ❌ Process orphaned
- Multiple projects: ⚠️ Process stopped when LAST project closes

**Solution**: Add application-level shutdown hook

### Issue 2: Multiple Projects Sharing Single Process Service

**Problem**: `CodexProcessService` is an **application-level** service (not project-level)

**Impact**:
```
Project A opens → Starts CLI process
Project B opens → Same CLI process (already running)
Project A closes → Stops CLI process
Project B → Now broken (no CLI process)
```

**Current Code** (plugin.xml):
```xml
<applicationService serviceImplementation="dev.curt.codexjb.core.CodexProcessService"/>
```

**Risk Level**: 🔴 **HIGH**
- Multi-project workflows are broken
- Closing one project kills CLI for all

**Root Cause**: Process service is application-scoped but lifecycle is tied to project events

**Solution Options**:

**Option A: Project-Level Service (Recommended)**
```xml
<projectService serviceImplementation="dev.curt.codexjb.core.CodexProcessService"/>
```
- Each project gets its own CLI process
- Clean isolation
- No shared state issues

**Option B: Reference Counting**
```kotlin
class CodexProcessService {
    private var refCount = 0

    fun start(...) {
        lock.withLock {
            if (refCount == 0) {
                // Start process
            }
            refCount++
        }
    }

    fun stop() {
        lock.withLock {
            refCount--
            if (refCount == 0) {
                // Actually stop process
            }
        }
    }
}
```
- Keep application-level service
- Track how many projects are using it
- Only stop when last project closes

### Issue 3: Process.destroy() May Not Kill Child Processes

**Problem**: `Process.destroy()` kills the direct child but may leave grandchildren

**Scenario**:
```
Java Process (CodexJetbrains)
  └─ codex.exe process
       └─ node.exe (if codex is npm script)
            └─ actual codex CLI
```

**Risk**: Orphaned node.exe or codex CLI processes

**Platform-Specific**:
- **Windows**: `destroy()` may not kill process tree
- **Unix**: `destroy()` sends SIGTERM to direct child only

**Current Code**:
```kotlin
override fun destroy() {
    kotlin.runCatching { writer.flush() }
    kotlin.runCatching { writer.close() }
    process.destroy()  // ⚠️ May not kill children
}
```

**Risk Level**: 🟡 **MEDIUM**
- Depends on how Codex CLI spawns subprocesses
- Likely OK if CLI is native binary
- Problem if CLI is npm script wrapper

**Solution**: Use `destroyForcibly()` or platform-specific tree kill

### Issue 4: No Graceful Shutdown Signal

**Problem**: `Process.destroy()` immediately kills the process

**Current**:
```kotlin
process.destroy()  // SIGTERM (or forceful kill on Windows)
```

**Risk**: CLI may not flush buffers, save state, or cleanup temp files

**Risk Level**: 🟢 **LOW**
- MCP protocol is stateless
- CLI likely doesn't need graceful shutdown
- But good practice to allow it

**Solution**: Send shutdown message before destroy

```kotlin
fun stop() {
    // 1. Send graceful shutdown message (if protocol supports it)
    kotlin.runCatching {
        stdin.writeLine("""{"type":"Shutdown"}""")
        stdin.flush()
    }

    // 2. Wait briefly for graceful exit
    Thread.sleep(1000)

    // 3. Force kill if still alive
    if (process.isAlive) {
        process.destroy()
    }

    // 4. Last resort: force kill after timeout
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
    }
}
```

### Issue 5: No Startup Validation

**Problem**: Process starts but we don't verify it's actually working

**Current**:
```kotlin
val process = builder.start()
return RealCodexProcess(config, process)  // Assumes success
```

**Risk**: Process starts but:
- Wrong executable (error 193 on Windows)
- CLI crashes immediately
- CLI outputs error and exits
- No MCP protocol available

**Risk Level**: 🟡 **MEDIUM**
- User sees "CLI started" but nothing works
- Hard to debug

**Solution**: Wait for initial protocol handshake

```kotlin
fun start(config: CodexProcessConfig): CodexProcessHandle {
    val process = builder.start()
    val handle = RealCodexProcess(config, process)

    // Wait for initial protocol message (e.g., SessionConfigured)
    val initialized = waitForInitialization(handle, timeout = 5000)
    if (!initialized) {
        handle.destroy()
        throw CodexStartupException("CLI process started but did not respond")
    }

    return handle
}
```

### Issue 6: Reader Threads Not Cleaned Up

**Problem**: stdout/stderr reader threads may not stop cleanly

**Current** (CodexToolWindowFactory):
```kotlin
Thread {
    try {
        while (true) {
            val line = stdout.readLine() ?: break
            // Process line
        }
    } catch (t: Throwable) {
        log.warn("stdout reader ended: ${t.message}")
    }
}.apply { isDaemon = true; name = "codex-proto-stdout" }.start()
```

**Risk**: Threads continue running after tool window closes

**Why**:
- Threads are daemon (good for JVM shutdown)
- But `readLine()` blocks forever if process doesn't close stdout
- Tool window disposal doesn't interrupt these threads

**Risk Level**: 🟡 **MEDIUM**
- Threads may leak on tool window close/reopen
- Eventually cleaned up by daemon thread GC

**Solution**: Interrupt threads on disposal

```kotlin
private val stdoutThread: Thread = Thread { /* ... */ }
private val stderrThread: Thread = Thread { /* ... */ }

content.setDisposer(object : Disposable {
    override fun dispose() {
        // Stop process first
        processMonitor.close()

        // Interrupt reader threads
        stdoutThread.interrupt()
        stderrThread.interrupt()

        // Wait briefly for threads to exit
        stdoutThread.join(1000)
        stderrThread.join(1000)

        heartbeat.dispose()
    }
})
```

---

## Recommended Fixes

### Priority 1: Critical - Fix Multi-Project Issue

**Change**: Make `CodexProcessService` project-scoped

**Impact**: Breaking change but necessary

**Implementation**:

1. Change plugin.xml:
```xml
<!-- Before -->
<applicationService serviceImplementation="dev.curt.codexjb.core.CodexProcessService"/>

<!-- After -->
<projectService serviceImplementation="dev.curt.codexjb.core.CodexProcessService"/>
```

2. Update all service retrieval:
```kotlin
// Before
val service = ApplicationManager.getApplication()
    .getService(CodexProcessService::class.java)

// After
val service = project.getService(CodexProcessService::class.java)
```

3. Update CodexToolWindowFactory:
```kotlin
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // Use project-scoped service
    val proc = project.getService(CodexProcessService::class.java)
    // ...
}
```

**Files to Modify**:
- plugin.xml
- CodexToolWindowFactory.kt
- CodexProjectCloseListener.kt
- Any other files retrieving the service

### Priority 2: High - Add Application Shutdown Hook

**Change**: Register app-level listener to cleanup on IDE exit

**Implementation**:

1. Create AppShutdownListener:
```kotlin
package dev.curt.codexjb.core

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager

class CodexAppShutdownListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        // Cleanup all running processes before IDE shuts down
        val service = ApplicationManager.getApplication()
            .getService(CodexProcessService::class.java)
        service?.stop()
    }
}
```

2. Register in plugin.xml:
```xml
<applicationListeners>
    <listener class="dev.curt.codexjb.core.CodexAppShutdownListener"
              topic="com.intellij.ide.AppLifecycleListener"/>
</applicationListeners>
```

**Note**: Only needed if keeping application-level service. Not needed if moving to project-level.

### Priority 3: Medium - Improve Process Shutdown

**Change**: Graceful shutdown with forceful fallback

**Implementation**:

```kotlin
class RealCodexProcess : CodexProcessHandle {
    override fun destroy() {
        // 1. Flush and close stdin (signals no more input)
        kotlin.runCatching {
            writer.flush()
            writer.close()
        }

        // 2. Wait briefly for graceful exit
        val exited = process.waitFor(2, TimeUnit.SECONDS)

        // 3. Force kill if still alive
        if (!exited && process.isAlive) {
            process.destroy()
        }

        // 4. Last resort: destroy forcibly (kills process tree)
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()  // Wait indefinitely for forceful kill
        }
    }
}
```

**Trade-off**: Adds 5s max delay on shutdown, but ensures clean process termination

### Priority 4: Medium - Add Startup Validation

**Change**: Verify CLI responds after startup

**Implementation**:

```kotlin
class CodexToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // ... existing code ...

        proc.start(processConfig)

        // Wait for CLI to respond
        val initialized = CountDownLatch(1)
        val timeoutMs = 5000L

        bus.addListener("SessionConfigured") { _, _ ->
            initialized.countDown()
        }

        attachReader(proc, bus, log)

        // Block until initialization or timeout
        if (!initialized.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            DiagnosticsService.append("ERROR: Codex CLI did not respond within ${timeoutMs}ms")
            val content = ContentFactory.getInstance()
                .createContent(JLabel("Codex CLI failed to initialize"), "Chat", false)
            toolWindow.contentManager.addContent(content)
            return
        }

        DiagnosticsService.append("Codex CLI initialized successfully")
        // ... continue with UI setup ...
    }
}
```

### Priority 5: Low - Clean Reader Threads on Disposal

**Change**: Store thread references and interrupt on disposal

**Implementation**: See Issue 6 solution above

---

## Testing Checklist

### Startup Tests
- [ ] Single project opens → CLI starts
- [ ] Multiple projects open → Each has own CLI (if project-scoped)
- [ ] CLI not found → Error shown, tool window graceful
- [ ] CLI crashes on start → Error detected, user notified
- [ ] CLI slow to start → Timeout handled, error shown

### Shutdown Tests
- [ ] Close project → CLI stops cleanly
- [ ] Close IDE → All CLI processes stop
- [ ] Force-kill IDE → No orphaned processes
- [ ] Multiple projects, close one → Other projects' CLI unaffected
- [ ] Restart IDE → Old processes cleaned, new ones start fresh

### Multi-Project Tests
- [ ] Open 2 projects → 2 separate CLI processes
- [ ] Close project 1 → Only project 1 CLI stops
- [ ] Project 2 still works after project 1 closes
- [ ] Reopen project 1 → New CLI starts

### Error Handling Tests
- [ ] CLI process dies unexpectedly → Auto-restart triggered
- [ ] CLI becomes unresponsive → Stale detection, restart
- [ ] Kill CLI externally → Health monitor detects, restarts
- [ ] Rapid open/close project → No process leaks

---

## Summary

### Current State
✅ **Good**: Project close handling, thread safety, health monitoring
⚠️ **Needs Attention**: Multi-project support, IDE shutdown, graceful process termination

### Critical Path
1. **Make service project-scoped** (fixes multi-project issue)
2. **Add startup validation** (catch failures early)
3. **Improve shutdown** (graceful + forceful fallback)
4. **Test thoroughly** (especially multi-project scenarios)

### Estimated Effort
- Priority 1 (Multi-project fix): 2-4 hours
- Priority 2 (App shutdown): 1 hour
- Priority 3 (Better shutdown): 2 hours
- Priority 4 (Startup validation): 2-3 hours
- Testing: 4-6 hours

**Total**: 1-2 days of focused work

---

## References

- [IntelliJ Platform Plugin SDK - Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html)
- [IntelliJ Platform Plugin SDK - Listeners](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html)
- [Java Process API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html)
