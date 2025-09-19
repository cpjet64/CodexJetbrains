package dev.curt.codexjb.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

enum class OperatingSystem { WINDOWS, MAC, LINUX, UNKNOWN }

object CodexPathDiscovery {
    private val windowsExts = listOf(".exe", ".cmd", ".bat", ".ps1")

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
        val local = findLocalBin(workingDirectory, os)
        if (local != null) return local

        val fromPath = findOnPath(env, os)
        if (fromPath != null) return fromPath

        return null
    }

    private fun findLocalBin(workingDirectory: Path?, os: OperatingSystem): Path? {
        if (workingDirectory == null) return null
        val bin = workingDirectory.resolve("node_modules").resolve(".bin")
        return candidateNames(os)
            .map { bin.resolve(it) }
            .firstOrNull { isExecutableFile(it, os) }
    }

    private fun findOnPath(env: Map<String, String>, os: OperatingSystem): Path? {
        val pathVar = env[if (os == OperatingSystem.WINDOWS) "Path" else "PATH"] ?: return null
        val sep = if (os == OperatingSystem.WINDOWS) ';' else ':'
        val parts = pathVar.split(sep).filter { it.isNotBlank() }
        for (dir in parts) {
            val base = Paths.get(dir)
            for (name in candidateNames(os)) {
                val p = base.resolve(name)
                if (isExecutableFile(p, os)) return p
            }
        }
        return null
    }

    private fun candidateNames(os: OperatingSystem): List<String> = when (os) {
        OperatingSystem.WINDOWS -> buildList {
            add("codex")
            windowsExts.forEach { add("codex$it") }
        }
        else -> listOf("codex")
    }

    private fun isExecutableFile(p: Path, os: OperatingSystem): Boolean {
        if (!p.exists() || !p.isRegularFile()) return false
        return when (os) {
            OperatingSystem.WINDOWS -> hasWindowsExecutableExt(p)
            else -> Files.isExecutable(p)
        }
    }

    private fun hasWindowsExecutableExt(p: Path): Boolean {
        val name = p.fileName.toString().lowercase()
        return windowsExts.any { name.endsWith(it) } || name == "codex"
    }
}

