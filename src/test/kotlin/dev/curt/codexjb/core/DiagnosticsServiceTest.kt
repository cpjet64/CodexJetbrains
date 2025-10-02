package dev.curt.codexjb.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsServiceTest {
    private lateinit var tempLogDir: Path
    private var previousLogPath: String? = null

    @BeforeTest
    fun setUp() {
        previousLogPath = System.getProperty("idea.log.path")
        tempLogDir = Files.createTempDirectory("codex-diag-test")
        System.setProperty("idea.log.path", tempLogDir.toString())
        DiagnosticsService.clear()
    }

    @AfterTest
    fun tearDown() {
        DiagnosticsService.clear()
        previousLogPath?.let { System.setProperty("idea.log.path", it) } ?: System.clearProperty("idea.log.path")
        tempLogDir.toFile().deleteRecursively()
    }

    @Test
    fun `append redacts sensitive token values`() {
        val sensitiveLine = "Received bearer sk-testsecretvalue"

        DiagnosticsService.append(sensitiveLine)

        val snapshot = DiagnosticsService.snapshot()
        val last = snapshot.last()
        assertTrue(last.contains("[REDACTED]"), "Expected redacted marker in diagnostics line")
        assertFalse(last.contains("sk-testsecretvalue"), "Sensitive token should not appear in diagnostics")
    }
}
