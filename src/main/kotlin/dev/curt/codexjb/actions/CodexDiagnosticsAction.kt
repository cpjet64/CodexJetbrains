package dev.curt.codexjb.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import dev.curt.codexjb.core.CodexCliExecutor
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.CodexPathDiscovery
import dev.curt.codexjb.core.DefaultCodexCliExecutor
import dev.curt.codexjb.core.EnvironmentInfo
import dev.curt.codexjb.core.run
import java.nio.file.Path

class CodexDiagnosticsAction : AnAction("Codex: Diagnostics") {
    private val executor: CodexCliExecutor = DefaultCodexCliExecutor()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val content = runDiagnostics(project)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Codex")
            .createNotification("Codex Diagnostics", content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun runDiagnostics(project: Project?): String {
        val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
        val base = project?.basePath?.let { Path.of(it) }
        val cli = cfg.resolveExecutable(base)
        val version = if (cli != null) executor.run(cli, "--version") else null
        val env = System.getenv()[if (EnvironmentInfo.os == dev.curt.codexjb.core.OperatingSystem.WINDOWS) "Path" else "PATH"]
        val data = DiagnosticsData(
            os = System.getProperty("os.name") ?: "",
            isWsl = EnvironmentInfo.isWsl,
            cliPath = cli?.toString() ?: "<not found>",
            versionExit = version?.exitCode,
            versionOut = version?.stdout?.trim() ?: "",
            versionErr = version?.stderr?.trim() ?: "",
            pathEnv = env ?: ""
        )
        return DiagnosticsBuilder.build(data)
    }
}

data class DiagnosticsData(
    val os: String,
    val isWsl: Boolean,
    val cliPath: String,
    val versionExit: Int?,
    val versionOut: String,
    val versionErr: String,
    val pathEnv: String
)

object DiagnosticsBuilder {
    fun build(d: DiagnosticsData): String {
        val b = StringBuilder()
        b.appendLine("OS: ${d.os}")
        b.appendLine("WSL: ${d.isWsl}")
        b.appendLine("CLI: ${d.cliPath}")
        if (d.versionExit != null) {
            b.appendLine("Version exit: ${d.versionExit}")
            if (d.versionOut.isNotEmpty()) b.appendLine("Version: ${d.versionOut}")
            if (d.versionErr.isNotEmpty()) b.appendLine("VersionErr: ${d.versionErr}")
        } else {
            b.appendLine("Version: <not run>")
        }
        b.appendLine("PATH: ${truncate(d.pathEnv, 300)}")
        return b.toString().trimEnd()
    }

    private fun truncate(s: String, max: Int): String {
        if (s.length <= max) return s
        return s.substring(0, max) + "…"
    }
}
