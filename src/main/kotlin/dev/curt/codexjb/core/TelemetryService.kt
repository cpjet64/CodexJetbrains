package dev.curt.codexjb.core

import dev.curt.codexjb.core.LogSink
import java.util.concurrent.atomic.AtomicLong

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
        log.info("MCP tool invocation: ${mcpToolInvocations.get()}")
    }
    
    fun recordMcpToolFailure() {
        mcpToolFailures.incrementAndGet()
        log.info("MCP tool failure: ${mcpToolFailures.get()}")
    }
    
    fun recordSessionStart() {
        sessionStarts.incrementAndGet()
        log.info("Session start: ${sessionStarts.get()}")
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
    
    fun getAllStats(): TelemetryStats {
        return TelemetryStats(
            patchApply = getPatchApplyStats(),
            execCommand = getExecCommandStats(),
            mcpTool = getMcpToolStats(),
            session = getSessionStats()
        )
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
