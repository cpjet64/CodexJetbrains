package dev.curt.codexjb.core

import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * Checks if Codex CLI version meets minimum requirements.
 */
object CodexVersionCheck {

    /**
     * Minimum version required for app-server protocol (JSON-RPC).
     */
    private const val MIN_VERSION_FOR_APP_SERVER = "0.44.0"

    /**
     * Check if Codex CLI version supports app-server protocol.
     * This runs asynchronously to avoid blocking the EDT during IDE startup.
     *
     * @param codexPath Path to codex executable
     * @return CompletableFuture of Pair(isSupported, installedVersion)
     */
    fun checkAppServerSupport(codexPath: Path): CompletableFuture<Pair<Boolean, String?>> {
        return CompletableFuture.supplyAsync {
            try {
                // Handle PowerShell scripts on Windows - bypass the .ps1 wrapper to avoid hangs
                val isPowerShellScript = codexPath.toString().lowercase().endsWith(".ps1")
                val processBuilder = if (isPowerShellScript && EnvironmentInfo.os == OperatingSystem.WINDOWS) {
                    // Try to bypass the PowerShell wrapper by calling node.exe directly
                    val nodeExe = ExecutableDiscovery.findNode()
                    val jsScript = ExecutableDiscovery.findCodexScript(codexPath)

                    if (nodeExe != null && jsScript != null) {
                        DiagnosticsService.append("Version check: Bypassing .ps1 wrapper, using node=$nodeExe, script=$jsScript")
                        ProcessBuilder(nodeExe.toString(), jsScript.toString(), "--version")
                    } else {
                        DiagnosticsService.append("Version check: Using PowerShell wrapper (node=${nodeExe != null}, script=${jsScript != null})")
                        ProcessBuilder(
                            "powershell.exe",
                            "-NoProfile",
                            "-ExecutionPolicy", "Bypass",
                            "-File", codexPath.toString(),
                            "--version"
                        )
                    }
                } else {
                    ProcessBuilder(codexPath.toString(), "--version")
                }

                val process = processBuilder.redirectErrorStream(true).start()

                // Wait for process to complete with a 10 second timeout
                val completed = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    DiagnosticsService.append("Version check timed out after 10 seconds")
                    return@supplyAsync Pair(false, null)
                }

                val output = process.inputStream.bufferedReader().readText().trim()
                val exitCode = process.exitValue()

                if (exitCode != 0) {
                    DiagnosticsService.append("Failed to get Codex version (exit code: $exitCode)")
                    DiagnosticsService.append("Output: $output")
                    return@supplyAsync Pair(false, null)
                }

                // Parse version from output (e.g., "0.39.0" or "0.44.0")
                val versionRegex = Regex("""(\d+)\.(\d+)\.(\d+)""")
                val match = versionRegex.find(output)

                if (match == null) {
                    DiagnosticsService.append("Could not parse Codex version from: $output")
                    return@supplyAsync Pair(false, output)
                }

                val version = match.value
                val isSupported = compareVersions(version, MIN_VERSION_FOR_APP_SERVER) >= 0

                DiagnosticsService.append("Codex CLI version: $version (app-server support: $isSupported)")

                Pair(isSupported, version)
            } catch (e: Exception) {
                DiagnosticsService.append("Error checking Codex version: ${e.message}")
                DiagnosticsService.append("Stack trace: ${e.stackTraceToString()}")
                Pair(false, null)
            }
        }.orTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
    }

    /**
     * Compare two semantic versions.
     *
     * @return -1 if v1 < v2, 0 if equal, 1 if v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0

            if (p1 < p2) return -1
            if (p1 > p2) return 1
        }

        return 0
    }
}
