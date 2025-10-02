package dev.curt.codexjb.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.DiagnosticsService
import dev.curt.codexjb.core.ProcessHealth
import dev.curt.codexjb.core.TurnMetricsService

class CodexReportIssueAction : AnAction("Codex: Report Issue") {
    override fun actionPerformed(e: AnActionEvent) {
        val appSettings = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
        val diagnostics = buildString {
            appendLine("Codex Report Snapshot")
            appendLine("Global CLI Path: ${appSettings.cliPath?.toString() ?: "(auto)"}")
            appendLine("WSL Enabled: ${appSettings.useWsl}")
            appendLine("Startup Auto-Open: ${appSettings.openToolWindowOnStartup}")
            appendLine("Default Model: ${appSettings.defaultModel ?: "(auto)"}")
            appendLine("Default Reasoning: ${appSettings.defaultEffort ?: "(auto)"}")
            appendLine("Default Approval: ${appSettings.defaultApprovalMode ?: "(auto)"}")
            appendLine("Default Sandbox: ${appSettings.defaultSandboxPolicy ?: "workspace-write"}")
            val health = ProcessHealth.snapshot()
            appendLine("Process Status: ${health.status}")
            appendLine("Restart Attempts: ${health.restartAttempts}")
            val metrics = TurnMetricsService.latestMetrics()
            appendLine("Last Stream Duration: ${metrics.streamDurationMs}ms")
            appendLine("Apply Duration: ${metrics.applyDurationMs}ms")
            appendLine("Exec Duration: ${metrics.execDurationMs}ms")
            appendLine("Tokens/sec: ${metrics.tokensPerSecond?.let { String.format("%.2f", it) } ?: "n/a"}")
            appendLine()
            appendLine("Recent Diagnostics:")
            appendLine(DiagnosticsService.snapshot().takeLast(200).joinToString(System.lineSeparator()))
        }

        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(diagnostics), null)
        DiagnosticsService.append("Generated issue report snapshot (${diagnostics.length} chars copied)")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Codex")
            .createNotification(
                "Codex",
                "Diagnostics copied to clipboard. Paste into your issue report.",
                NotificationType.INFORMATION
            )
            .notify(e.project)
    }
}


