package dev.curt.codexjb.core

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Utility for discovering executables on the system PATH.
 *
 * Supports multiple installation methods:
 * - Official installers (Program Files)
 * - Version managers (NVM, fnm, Volta)
 * - Package managers (Scoop, Chocolatey, Homebrew)
 * - Manual installations
 */
object ExecutableDiscovery {

    /**
     * Find an executable on PATH.
     *
     * On Windows, automatically checks PATHEXT extensions (.exe, .cmd, .bat, etc.)
     *
     * @param name The executable name (e.g., "node", "npm", "git")
     * @param env Environment variables (defaults to System.getenv())
     * @return Path to the executable, or null if not found
     *
     * Examples:
     * - findOnPath("node") -> C:\nvm4w\nodejs\node.exe
     * - findOnPath("npm") -> C:\Program Files\nodejs\npm.cmd
     * - findOnPath("git") -> C:\Program Files\Git\cmd\git.exe
     */
    fun findOnPath(name: String, env: Map<String, String> = System.getenv()): Path? {
        val os = EnvironmentInfo.os

        // Get PATH variable (case-insensitive on Windows)
        val pathVar = when (os) {
            OperatingSystem.WINDOWS -> env.entries.firstOrNull { it.key.equals("Path", ignoreCase = true) }?.value
            else -> env["PATH"]
        } ?: return null

        val sep = if (os == OperatingSystem.WINDOWS) ';' else ':'
        val pathDirs = pathVar.split(sep).filter { it.isNotBlank() }

        // Get candidate names (with extensions on Windows)
        val candidates = getCandidateNames(name, os, env)

        // Search each PATH directory for each candidate
        for (dir in pathDirs) {
            val base = Paths.get(dir)
            for (candidate in candidates) {
                val path = base.resolve(candidate)
                if (path.exists() && path.isRegularFile()) {
                    return path
                }
            }
        }

        return null
    }

    /**
     * Get candidate executable names for a given base name.
     *
     * On Windows, appends PATHEXT extensions (.exe, .cmd, .bat, .com, etc.)
     * On Unix, returns the bare name.
     */
    private fun getCandidateNames(name: String, os: OperatingSystem, env: Map<String, String>): List<String> {
        return when (os) {
            OperatingSystem.WINDOWS -> {
                // Get PATHEXT (defaults if not found)
                val pathExt = env["PATHEXT"] ?: ".COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC"
                val extensions = pathExt.split(';').filter { it.isNotBlank() }

                // Try bare name first (might be a symlink), then with extensions
                buildList {
                    add(name)
                    extensions.forEach { ext -> add("$name$ext") }
                }
            }
            else -> listOf(name)
        }
    }

    /**
     * Find node.exe on PATH, handling all common installation methods:
     *
     * - Standard installer: C:\Program Files\nodejs\node.exe
     * - NVM for Windows: C:\nvm4w\nodejs\node.exe (symlink managed by NVM)
     * - fnm: C:\Users\{user}\AppData\Local\fnm_multishells\{version}\node.exe
     * - Volta: C:\Users\{user}\.volta\bin\node.exe
     * - Scoop: C:\Users\{user}\scoop\apps\nodejs\current\node.exe
     * - Chocolatey: C:\ProgramData\chocolatey\bin\node.exe
     *
     * @return Path to node.exe, or null if not found on PATH
     */
    fun findNode(): Path? {
        val nodePath = findOnPath("node")
        if (nodePath != null) {
            DiagnosticsService.append("Found node.exe on PATH: $nodePath")
        } else {
            DiagnosticsService.append("node.exe not found on PATH")
        }
        return nodePath
    }

    /**
     * Find the codex.js script that corresponds to a PowerShell wrapper.
     *
     * Given a path like: C:\Users\{user}\AppData\Roaming\npm\codex.ps1
     * Returns: C:\Users\{user}\AppData\Roaming\npm\node_modules\@openai\codex\bin\codex.js
     *
     * @param wrapperPath Path to the .ps1 wrapper script
     * @return Path to codex.js if it exists, null otherwise
     */
    fun findCodexScript(wrapperPath: Path): Path? {
        val scriptDir = wrapperPath.parent ?: return null
        val jsScript = scriptDir.resolve("node_modules/@openai/codex/bin/codex.js")
        return if (jsScript.exists() && jsScript.isRegularFile()) {
            jsScript
        } else {
            null
        }
    }
}
