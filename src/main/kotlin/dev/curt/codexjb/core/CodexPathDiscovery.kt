package dev.curt.codexjb.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

enum class OperatingSystem { WINDOWS, MAC, LINUX, UNKNOWN }

object CodexPathDiscovery {
    // Prefer modern shells over legacy ones
    // Order: native binary, PowerShell 7+, PowerShell 5.1, CMD
    private val windowsShells = listOf(
        ShellPreference(".exe", null),           // Native executable (no shell needed)
        ShellPreference(".ps1", "pwsh.exe"),     // PowerShell 7+ (modern)
        ShellPreference(".ps1", "powershell.exe"), // PowerShell 5.1 (Windows default)
        ShellPreference(".cmd", "cmd.exe"),      // CMD (legacy fallback)
        ShellPreference(".bat", "cmd.exe")       // Batch (oldest fallback)
    )

    private data class ShellPreference(
        val extension: String,
        val shellExecutable: String?  // null means direct execution (like .exe)
    )

    fun currentOs(): OperatingSystem {
        val name = System.getProperty("os.name").lowercase()
        return when {
            name.contains("win") -> OperatingSystem.WINDOWS
            name.contains("mac") || name.contains("darwin") -> OperatingSystem.MAC
            name.contains("nux") || name.contains("nix") -> OperatingSystem.LINUX
            else -> OperatingSystem.UNKNOWN
        }
    }

    fun discover(
        os: OperatingSystem = currentOs(),
        env: Map<String, String> = System.getenv(),
        workingDirectory: Path? = null
    ): Path? {
        val local = findLocalBin(workingDirectory, os, env)
        if (local != null) {
            dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Found local codex at: $local")
            return local
        }

        dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Searching PATH for codex...")
        val fromPath = findOnPath(env, os)
        if (fromPath != null) {
            dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Found codex on PATH at: $fromPath")
            return fromPath
        }

        dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: No codex found in local or PATH")
        return null
    }

    private fun isShellAvailable(shellExecutable: String?, env: Map<String, String>): Boolean {
        if (shellExecutable == null) return true  // No shell needed (direct execution)

        // Check if shell is in PATH (case-insensitive lookup for Windows)
        val pathVar = env.entries.firstOrNull { it.key.equals("Path", ignoreCase = true) }?.value
            ?: env.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value
            ?: return false
        // Determine separator based on OS (Windows uses ';', Unix uses ':')
        val sep = if (pathVar.contains(';')) ';' else ':'
        val paths = pathVar.split(sep).filter { it.isNotBlank() }

        val found = paths.any { dir ->
            val shellPath = Paths.get(dir).resolve(shellExecutable)
            val exists = shellPath.exists() && shellPath.isRegularFile()

            // Debug: log what we're checking
            if (dir.contains("system32", ignoreCase = true) || dir.contains("WindowsPowerShell", ignoreCase = true)) {
                dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Checking $shellPath -> exists=${shellPath.exists()}, isFile=${try { shellPath.isRegularFile() } catch (e: Exception) { "error: ${e.message}" }}")
            }

            if (exists) {
                dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Found shell $shellExecutable at $shellPath")
            }
            exists
        }

        if (!found) {
            dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Shell $shellExecutable NOT found in ${paths.size} PATH directories")
        }

        return found
    }

    private fun findLocalBin(workingDirectory: Path?, os: OperatingSystem, env: Map<String, String>): Path? {
        if (workingDirectory == null) return null
        val bin = workingDirectory.resolve("node_modules").resolve(".bin")
        return candidateNames(os, env)
            .map { bin.resolve(it) }
            .firstOrNull { isExecutableFile(it, os) }
    }

    private fun findOnPath(env: Map<String, String>, os: OperatingSystem): Path? {
        // Case-insensitive PATH lookup for Windows
        val pathVar = when (os) {
            OperatingSystem.WINDOWS -> env.entries.firstOrNull { it.key.equals("Path", ignoreCase = true) }?.value
            else -> env["PATH"]
        } ?: return null

        val sep = if (os == OperatingSystem.WINDOWS) ';' else ':'
        val parts = pathVar.split(sep).filter { it.isNotBlank() }
        dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Searching ${parts.size} PATH directories")
        val candidates = candidateNames(os, env)
        for ((idx, dir) in parts.withIndex()) {
            val base = Paths.get(dir)
            for (name in candidates) {
                val p = base.resolve(name)
                if (isExecutableFile(p, os)) {
                    dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Found executable at PATH[$idx]: $p")
                    return p
                }
            }
        }
        dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: No executable found in any PATH directory")
        return null
    }

    private fun candidateNames(os: OperatingSystem, env: Map<String, String>): List<String> = when (os) {
        OperatingSystem.WINDOWS -> buildList {
            add("codex")  // Try bare name first (might be a symlink or native executable)
            // Add launcher scripts in order of shell preference
            val availableShells = windowsShells.filter { isShellAvailable(it.shellExecutable, env) }
            dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Available shells: ${availableShells.map { it.shellExecutable ?: "native" }}")
            availableShells.forEach { add("codex${it.extension}") }

            // Fallback: if no shells detected, include all launchers anyway
            // (shell detection might fail but launchers might still work)
            if (availableShells.isEmpty()) {
                dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: No shells detected, using fallback (all launchers)")
                windowsShells.forEach { add("codex${it.extension}") }
            }
            dev.curt.codexjb.core.DiagnosticsService.append("DEBUG: Candidate names: $this")
        }
        else -> listOf("codex")
    }

    private fun isExecutableFile(p: Path, os: OperatingSystem): Boolean {
        if (!p.exists() || !p.isRegularFile()) return false
        return when (os) {
            OperatingSystem.WINDOWS -> hasWindowsExecutableExt(p)
            else -> {
                // For Unix-like systems, check if file is executable
                // On Windows, Files.isExecutable might not work as expected
                try {
                    Files.isExecutable(p)
                } catch (e: Exception) {
                    // Fallback to checking if file can be executed
                    p.toFile().canExecute()
                }
            }
        }
    }

    private fun hasWindowsExecutableExt(p: Path): Boolean {
        val name = p.fileName.toString().lowercase()
        // Only accept files with known executable extensions on Windows.
        // Do NOT treat bare "codex" (no extension) as executable; that often points to a
        // Unix shim placed by package managers and will fail with CreateProcess error 193.
        return windowsShells.any { name.endsWith(it.extension) }
    }
}
