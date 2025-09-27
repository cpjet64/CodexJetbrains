package dev.curt.codexjb.core

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryServiceSessionTest {

    @BeforeTest
    fun reset() {
        TelemetryService.resetForTests()
    }


    @Test
    fun tracksToolInvocationsPerSession() {
        // Start first session
        TelemetryService.recordSessionStart("session1")
        TelemetryService.recordMcpToolInvocation()
        TelemetryService.recordMcpToolInvocation()
        
        // Start second session
        TelemetryService.recordSessionStart("session2")
        TelemetryService.recordMcpToolInvocation()
        
        // Check session-specific counts
        assertEquals(2L, TelemetryService.getToolInvocationsForSession("session1"))
        assertEquals(1L, TelemetryService.getToolInvocationsForSession("session2"))
        assertEquals(1L, TelemetryService.getCurrentSessionToolInvocations())
        
        // Check total count
        val stats = TelemetryService.getMcpToolStats()
        assertEquals(3L, stats.invocations)
    }
    
    @Test
    fun handlesUnknownSessionId() {
        assertEquals(0L, TelemetryService.getToolInvocationsForSession("nonexistent"))
    }
    
    @Test
    fun generatesSessionIdWhenNotProvided() {
        TelemetryService.recordSessionStart()
        
        val allSessions = TelemetryService.getAllSessionToolInvocations()
        assertEquals(1, allSessions.size)
        
        val sessionId = allSessions.keys.first()
        assertTrue(sessionId.startsWith("session_"))
    }
    
    @Test
    fun tracksMultipleSessions() {
        TelemetryService.recordSessionStart("session1")
        TelemetryService.recordMcpToolInvocation()
        
        TelemetryService.recordSessionStart("session2")
        TelemetryService.recordMcpToolInvocation()
        TelemetryService.recordMcpToolInvocation()
        
        TelemetryService.recordSessionStart("session3")
        // No tool invocations for session3
        
        val allSessions = TelemetryService.getAllSessionToolInvocations()
        assertEquals(3, allSessions.size)
        assertEquals(1L, allSessions["session1"])
        assertEquals(2L, allSessions["session2"])
        assertEquals(0L, allSessions["session3"])
    }
    
    @Test
    fun currentSessionTracking() {
        // No current session initially
        assertEquals(0L, TelemetryService.getCurrentSessionToolInvocations())
        
        // Start session and record invocations
        TelemetryService.recordSessionStart("current_session")
        TelemetryService.recordMcpToolInvocation()
        TelemetryService.recordMcpToolInvocation()
        
        assertEquals(2L, TelemetryService.getCurrentSessionToolInvocations())
        
        // Switch to new session
        TelemetryService.recordSessionStart("new_session")
        assertEquals(0L, TelemetryService.getCurrentSessionToolInvocations())
        
        TelemetryService.recordMcpToolInvocation()
        assertEquals(1L, TelemetryService.getCurrentSessionToolInvocations())
    }
}
