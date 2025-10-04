package dev.curt.codexjb.actions

import kotlin.test.Test
import kotlin.test.assertTrue

class DiagnosticsBuilderTest {
    @Test
    fun buildsMultiLineSummary() {
        val d = DiagnosticsData(
            os = "Linux",
            isWsl = false,
            cliPath = "/usr/bin/codex",
            versionExit = 0,
            versionOut = "codex 0.1.0",
            versionErr = "",
            pathEnv = "/usr/bin:/bin"
        )
        val s = DiagnosticsBuilder.build(d)
        assertTrue(s.contains("OS: Linux"))
        assertTrue(s.contains("CLI: /usr/bin/codex"))
        assertTrue(s.contains("Version: codex 0.1.0"))
        assertTrue(s.contains("PATH: /usr/bin:/bin"))
    }
}
