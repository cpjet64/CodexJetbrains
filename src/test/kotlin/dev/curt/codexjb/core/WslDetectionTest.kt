package dev.curt.codexjb.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WslDetectionTest {
    @Test
    fun detectsViaEnvVar() {
        val env = mapOf("WSL_DISTRO_NAME" to "Ubuntu-22.04")
        val isWsl = WslDetection.detect("Linux", env, null)
        assertTrue(isWsl)
    }

    @Test
    fun detectsViaProcVersion() {
        val text = "Linux version 5.15.90-microsoft-standard-WSL2"
        val isWsl = WslDetection.detect("Linux", emptyMap(), text)
        assertTrue(isWsl)
    }

    @Test
    fun notWslOnWindows() {
        val isWsl = WslDetection.detect("Windows 11", emptyMap(), null)
        assertFalse(isWsl)
    }

    @Test
    fun plainLinuxIsNotWsl() {
        val isWsl = WslDetection.detect("Linux", emptyMap(), "Linux version 6.1.0-generic")
        assertFalse(isWsl)
    }
}

