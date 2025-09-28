package dev.curt.codexjb.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexProjectSettingsServiceTest {
    @Test
    fun storesOverrides() {
        val svc = CodexProjectSettingsService()
        svc.cliPathOverride = "/usr/bin/codex"
        svc.useWslOverride = true
        svc.openToolWindowOnStartup = false
        svc.defaultModel = "gpt-4o-mini"
        svc.defaultEffort = "high"
        svc.defaultApprovalMode = "FULL_ACCESS"
        svc.defaultSandboxPolicy = "danger-full-access"

        val state = svc.state
        assertEquals("/usr/bin/codex", state.cliPathOverride)
        assertEquals(true, state.useWslOverride)
        assertEquals(false, state.openToolWindowOnStartup)
        assertEquals("gpt-4o-mini", state.defaultModel)
        assertEquals("high", state.defaultEffort)
        assertEquals("FULL_ACCESS", state.defaultApprovalMode)
        assertEquals("danger-full-access", state.defaultSandboxPolicy)
    }

    @Test
    fun clearOverridesResetsState() {
        val svc = CodexProjectSettingsService()
        svc.cliPathOverride = "test"
        svc.clearOverrides()

        assertNull(svc.cliPathOverride)
        assertNull(svc.useWslOverride)
        assertNull(svc.openToolWindowOnStartup)
        assertNull(svc.defaultModel)
    }
}
