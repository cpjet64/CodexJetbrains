package dev.curt.codexjb.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexPathDiscoveryTest {

    @Test
    fun findsCodexInUnixPath() {
        val tmp = Files.createTempDirectory("codex-path-unix-")
        val codex = tmp.resolve("codex")
        Files.writeString(codex, "#!/bin/sh\necho codex\n")
        codex.toFile().setExecutable(true)

        val env = mapOf("PATH" to tmp.toString())
        val found = CodexPathDiscovery.discover(
            os = OperatingSystem.LINUX,
            env = env,
            workingDirectory = null
        )

        // Normalize paths to handle Windows path differences
        val expectedPath = codex.toRealPath()
        val actualPath = found?.toRealPath()
        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun prefersLocalNodeBinOverPath() {
        val projectDir = Files.createTempDirectory("codex-proj-")
        val localBin = projectDir.resolve("node_modules/.bin").apply { createDirectories() }
        val localCodex = localBin.resolve("codex").apply { createFile() }
        localCodex.toFile().setExecutable(true)

        val anotherDir = Files.createTempDirectory("codex-other-")
        val pathCodex = anotherDir.resolve("codex").apply { createFile() }
        pathCodex.toFile().setExecutable(true)

        val env = mapOf("PATH" to anotherDir.toString())
        val found = CodexPathDiscovery.discover(
            os = OperatingSystem.MAC,
            env = env,
            workingDirectory = projectDir
        )

        assertEquals(localCodex, found)
    }

    @Test
  fun findsWindowsCmdInPath() {
        val tmp = Files.createTempDirectory("codex-path-win-")
        val cmd = tmp.resolve("codex.cmd")
        Files.writeString(cmd, "@echo off\necho codex\n")

        val env = mapOf("Path" to tmp.toString())
        val found = CodexPathDiscovery.discover(
            os = OperatingSystem.WINDOWS,
            env = env,
            workingDirectory = null
        )

        assertEquals(cmd, found)
  }

    @Test
    fun doesNotTreatExtensionlessAsWindowsExecutable() {
        val tmp = Files.createTempDirectory("codex-path-win-noext-")
        val bare = tmp.resolve("codex")
        Files.writeString(bare, "echo codex\n")

        val env = mapOf("Path" to tmp.toString())
        val found = CodexPathDiscovery.discover(
            os = OperatingSystem.WINDOWS,
            env = env,
            workingDirectory = null
        )

        // Should not pick the extensionless file on Windows
        assertNull(found)
    }

    @Test
    fun returnsNullWhenMissing() {
        val env = mapOf("PATH" to Paths.get("/nope").toString())
        val found = CodexPathDiscovery.discover(
            os = OperatingSystem.LINUX,
            env = env,
            workingDirectory = null
        )
        assertNull(found)
    }
}
