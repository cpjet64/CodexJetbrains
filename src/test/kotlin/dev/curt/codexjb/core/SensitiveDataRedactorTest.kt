package dev.curt.codexjb.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveDataRedactorTest {
    @Test
    fun `redacts sensitive values inside json env maps`() {
        val input = "env: Some({\"GITHUB_PERSONAL_ACCESS_TOKEN\": \"ghp_example_secret\"})"
        val result = SensitiveDataRedactor.redact(input)

        assertTrue(result.contains("\"GITHUB_PERSONAL_ACCESS_TOKEN\": \"[REDACTED]\""))
        assertFalse(result.contains("ghp_example_secret"))
    }

    @Test
    fun `redacts sensitive key value pairs`() {
        val input = "PATH=C:/bin GITHUB_TOKEN=gho_test123 OTHER=ok"
        val result = SensitiveDataRedactor.redact(input)

        assertTrue(result.contains("GITHUB_TOKEN=[REDACTED]"))
        assertFalse(result.contains("gho_test123"))
        assertTrue(result.contains("OTHER=ok"))
    }

    @Test
    fun `redacts standalone token patterns`() {
        val input = "Received bearer sk-verylongtokenvaluewithmorechars"
        val result = SensitiveDataRedactor.redact(input)

        assertEquals("Received bearer [REDACTED]", result)
    }
}
