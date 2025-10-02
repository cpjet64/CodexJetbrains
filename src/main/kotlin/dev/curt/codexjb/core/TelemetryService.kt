package dev.curt.codexjb.core

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

object TelemetryService {
    private val log: LogSink = CodexLogger.forClass(TelemetryService::class.java)

    // Patch apply counters
    private val patchApplySuccess = AtomicLong(0)
    private val patchApplyFailure = AtomicLong(0)
    private val patchApplyTotal = AtomicLong(0)

    // Exec command counters
    private val execCommandSuccess = AtomicLong(0)
    private val execCommandFailure = AtomicLong(0)
    private val execCommandTotal = AtomicLong(0)

    // MCP tool counters
    private val mcpToolInvocations = AtomicLong(0)
    private val mcpToolFailures = AtomicLong(0)

    // Session counters
    private val sessionStarts = AtomicLong(0)
    private val sessionEnds = AtomicLong(0)
    private val sessionErrors = AtomicLong(0)

    // Session-based tool invocations (thread-safe)
    private val toolInvocationsPerSession = ConcurrentHashMap<String, AtomicLong>()
    @Volatile private var currentSessionId: String? = null
    
    fun recordPatchApplySuccess() {
        patchApplySuccess.incrementAndGet()
        patchApplyTotal.incrementAndGet()
        log.info("Patch apply success: ${patchApplySuccess.get()}")
    }
    
    fun recordPatchApplyFailure() {
        patchApplyFailure.incrementAndGet()
        patchApplyTotal.incrementAndGet()
        log.info("Patch apply failure: ${patchApplyFailure.get()}")
    }
    
    fun recordExecCommandSuccess() {
        execCommandSuccess.incrementAndGet()
        execCommandTotal.incrementAndGet()
        log.info("Exec command success: ${execCommandSuccess.get()}")
    }
    
    fun recordExecCommandFailure() {
        execCommandFailure.incrementAndGet()
        execCommandTotal.incrementAndGet()
        log.info("Exec command failure: ${execCommandFailure.get()}")
    }
    
    fun recordMcpToolInvocation() {
        mcpToolInvocations.incrementAndGet()
        
        // Track per-session invocations
        val sessionId = currentSessionId ?: "unknown"
        toolInvocationsPerSession.computeIfAbsent(sessionId) { AtomicLong(0) }.incrementAndGet()
        
        log.info("MCP tool invocation: ${mcpToolInvocations.get()} (session: $sessionId)")
    }
    
    fun recordMcpToolFailure() {
        mcpToolFailures.incrementAndGet()
        log.info("MCP tool failure: ${mcpToolFailures.get()}")
    }
    
    fun recordSessionStart(sessionId: String? = null) {
        sessionStarts.incrementAndGet()
        val id = sessionId ?: "session_${System.currentTimeMillis()}"
        currentSessionId = id
        val counter = toolInvocationsPerSession.computeIfAbsent(id) { AtomicLong(0) }
        counter.set(0)
        log.info("Session start: ${sessionStarts.get()} (id: $id)")
    }
    
    fun recordSessionEnd() {
        sessionEnds.incrementAndGet()
        log.info("Session end: ${sessionEnds.get()}")
    }
    
    fun recordSessionError() {
        sessionErrors.incrementAndGet()
        log.info("Session error: ${sessionErrors.get()}")
    }
    
    fun getPatchApplyStats(): PatchApplyStats {
        return PatchApplyStats(
            success = patchApplySuccess.get(),
            failure = patchApplyFailure.get(),
            total = patchApplyTotal.get()
        )
    }
    
    fun getExecCommandStats(): ExecCommandStats {
        return ExecCommandStats(
            success = execCommandSuccess.get(),
            failure = execCommandFailure.get(),
            total = execCommandTotal.get()
        )
    }
    
    fun getMcpToolStats(): McpToolStats {
        return McpToolStats(
            invocations = mcpToolInvocations.get(),
            failures = mcpToolFailures.get()
        )
    }
    
    fun getSessionStats(): SessionStats {
        return SessionStats(
            starts = sessionStarts.get(),
            ends = sessionEnds.get(),
            errors = sessionErrors.get()
        )
    }
    
    fun getCurrentSessionToolInvocations(): Long {
        return currentSessionId?.let { toolInvocationsPerSession[it]?.get() ?: 0 } ?: 0
    }
    
    fun getToolInvocationsForSession(sessionId: String): Long {
        return toolInvocationsPerSession[sessionId]?.get() ?: 0
    }
    
    fun getAllSessionToolInvocations(): Map<String, Long> {
        return toolInvocationsPerSession.mapValues { it.value.get() }
    }
    
    fun getAllStats(): TelemetryStats {
        return TelemetryStats(
            patchApply = getPatchApplyStats(),
            execCommand = getExecCommandStats(),
            mcpTool = getMcpToolStats(),
            session = getSessionStats()
        )
    }

    internal fun resetForTests() {
        patchApplySuccess.set(0)
        patchApplyFailure.set(0)
        patchApplyTotal.set(0)
        execCommandSuccess.set(0)
        execCommandFailure.set(0)
        execCommandTotal.set(0)
        mcpToolInvocations.set(0)
        mcpToolFailures.set(0)
        sessionStarts.set(0)
        sessionEnds.set(0)
        sessionErrors.set(0)
        toolInvocationsPerSession.clear()
        currentSessionId = null
    }

    data class PatchApplyStats(
        val success: Long,
        val failure: Long,
        val total: Long
    )
    
    data class ExecCommandStats(
        val success: Long,
        val failure: Long,
        val total: Long
    )
    
    data class McpToolStats(
        val invocations: Long,
        val failures: Long
    )
    
    data class SessionStats(
        val starts: Long,
        val ends: Long,
        val errors: Long
    )
    
    data class TelemetryStats(
        val patchApply: PatchApplyStats,
        val execCommand: ExecCommandStats,
        val mcpTool: McpToolStats,
        val session: SessionStats
    )
}
