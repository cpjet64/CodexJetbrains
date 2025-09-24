package dev.curt.codexjb.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryServiceTest {
    
    @Test
    fun recordsPatchApplySuccess() {
        val initialStats = TelemetryService.getPatchApplyStats()
        val initialSuccess = initialStats.success
        
        TelemetryService.recordPatchApplySuccess()
        
        val stats = TelemetryService.getPatchApplyStats()
        assertEquals(initialSuccess + 1, stats.success)
        assertEquals(initialStats.failure, stats.failure)
        assertEquals(initialStats.total + 1, stats.total)
    }
    
    @Test
    fun recordsPatchApplyFailure() {
        val initialStats = TelemetryService.getPatchApplyStats()
        val initialFailure = initialStats.failure
        
        TelemetryService.recordPatchApplyFailure()
        
        val stats = TelemetryService.getPatchApplyStats()
        assertEquals(initialStats.success, stats.success)
        assertEquals(initialFailure + 1, stats.failure)
        assertEquals(initialStats.total + 1, stats.total)
    }
    
    @Test
    fun recordsExecCommandSuccess() {
        val initialStats = TelemetryService.getExecCommandStats()
        val initialSuccess = initialStats.success
        
        TelemetryService.recordExecCommandSuccess()
        
        val stats = TelemetryService.getExecCommandStats()
        assertEquals(initialSuccess + 1, stats.success)
        assertEquals(initialStats.failure, stats.failure)
        assertEquals(initialStats.total + 1, stats.total)
    }
    
    @Test
    fun recordsExecCommandFailure() {
        val initialStats = TelemetryService.getExecCommandStats()
        val initialFailure = initialStats.failure
        
        TelemetryService.recordExecCommandFailure()
        
        val stats = TelemetryService.getExecCommandStats()
        assertEquals(initialStats.success, stats.success)
        assertEquals(initialFailure + 1, stats.failure)
        assertEquals(initialStats.total + 1, stats.total)
    }
    
    @Test
    fun recordsMcpToolInvocation() {
        val initialStats = TelemetryService.getMcpToolStats()
        val initialInvocations = initialStats.invocations
        
        TelemetryService.recordMcpToolInvocation()
        
        val stats = TelemetryService.getMcpToolStats()
        assertEquals(initialInvocations + 1, stats.invocations)
        assertEquals(initialStats.failures, stats.failures)
    }
    
    @Test
    fun recordsMcpToolFailure() {
        val initialStats = TelemetryService.getMcpToolStats()
        val initialFailures = initialStats.failures
        
        TelemetryService.recordMcpToolFailure()
        
        val stats = TelemetryService.getMcpToolStats()
        assertEquals(initialStats.invocations, stats.invocations)
        assertEquals(initialFailures + 1, stats.failures)
    }
    
    @Test
    fun recordsSessionStart() {
        val initialStats = TelemetryService.getSessionStats()
        val initialStarts = initialStats.starts
        
        TelemetryService.recordSessionStart()
        
        val stats = TelemetryService.getSessionStats()
        assertEquals(initialStarts + 1, stats.starts)
        assertEquals(initialStats.ends, stats.ends)
        assertEquals(initialStats.errors, stats.errors)
    }
    
    @Test
    fun recordsSessionEnd() {
        val initialStats = TelemetryService.getSessionStats()
        val initialEnds = initialStats.ends
        
        TelemetryService.recordSessionEnd()
        
        val stats = TelemetryService.getSessionStats()
        assertEquals(initialStats.starts, stats.starts)
        assertEquals(initialEnds + 1, stats.ends)
        assertEquals(initialStats.errors, stats.errors)
    }
    
    @Test
    fun recordsSessionError() {
        val initialStats = TelemetryService.getSessionStats()
        val initialErrors = initialStats.errors
        
        TelemetryService.recordSessionError()
        
        val stats = TelemetryService.getSessionStats()
        assertEquals(initialStats.starts, stats.starts)
        assertEquals(initialStats.ends, stats.ends)
        assertEquals(initialErrors + 1, stats.errors)
    }
    
    @Test
    fun getAllStatsReturnsCompleteStats() {
        val stats = TelemetryService.getAllStats()
        
        // Verify all stats are present
        assert(stats.patchApply != null)
        assert(stats.execCommand != null)
        assert(stats.mcpTool != null)
        assert(stats.session != null)
        
        // Verify stats are consistent
        assertEquals(stats.patchApply.total, stats.patchApply.success + stats.patchApply.failure)
        assertEquals(stats.execCommand.total, stats.execCommand.success + stats.execCommand.failure)
    }
}
