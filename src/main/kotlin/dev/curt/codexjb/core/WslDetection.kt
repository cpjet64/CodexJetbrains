package dev.curt.codexjb.core

import java.nio.file.Files
import java.nio.file.Paths

object WslDetection {
    fun isWsl(): Boolean = detect(
        System.getProperty("os.name") ?: "",
        System.getenv(),
        readProcVersion()
    )

    fun detect(osName: String, env: Map<String, String>, procVersion: String?): Boolean {
        val name = osName.lowercase()
        if (!name.contains("linux")) return false
        if (env["WSL_DISTRO_NAME"]?.isNotBlank() == true) return true
        val text = (procVersion ?: "").lowercase()
        return text.contains("microsoft") || text.contains("wsl")
    }

    private fun readProcVersion(): String? = try {
        val p = Paths.get("/proc/version")
        if (Files.exists(p)) Files.readString(p) else null
    } catch (_: Exception) {
        null
    }
}

object EnvironmentInfo {
    val os: OperatingSystem = CodexPathDiscovery.currentOs()
    val isWsl: Boolean = WslDetection.isWsl()
}

