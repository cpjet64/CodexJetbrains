package dev.curt.codexjb.core

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexConfigServiceTest {
    @Test
    fun returnsConfiguredPathWhenSet() {
        val svc = CodexConfigService()
        val p = Paths.get("/usr/local/bin/codex")
        svc.cliPath = p

        val resolved = svc.resolveExecutable(null)

        assertEquals(p, resolved)
    }

    @Test
    fun fallsBackToDiscovery() {
        val svc = CodexConfigService()
        val fake = Paths.get("/tmp/codex")
        svc.installDiscoverer { _ -> fake }

        val resolved = svc.resolveExecutable(Paths.get("/work"))

        assertEquals(fake, resolved)
    }

    @Test
    fun resetClearsCustomSettings() {
        val svc = CodexConfigService()
        svc.useWsl = true
        svc.openToolWindowOnStartup = true
        svc.defaultModel = "gpt-4o-mini"
        svc.defaultSandboxPolicy = "danger-full-access"
        svc.availableModels = listOf("custom-model")

        svc.resetToDefaults()

        assertEquals(false, svc.useWsl)
        assertEquals(false, svc.openToolWindowOnStartup)
        assertEquals(null, svc.defaultModel)
        assertEquals(null, svc.defaultSandboxPolicy)
        assertEquals(CodexSettingsOptions.MODELS, svc.availableModels)
    }

    @Test
    fun customModelListOverridesDefaults() {
        val svc = CodexConfigService()
        assertEquals(CodexSettingsOptions.MODELS, svc.availableModels)

        svc.availableModels = listOf("alpha", "beta", "alpha", "")

        assertEquals(listOf("alpha", "beta"), svc.availableModels)

        svc.defaultModel = "legacy"
        svc.availableModels = listOf("x")

        assertEquals(listOf("x"), svc.availableModels)
        assertEquals("x", svc.defaultModel)
    }
}

