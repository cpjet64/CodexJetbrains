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
}

