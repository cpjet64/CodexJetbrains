package dev.curt.codexjb.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindowManager

class CodexProjectCloseListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
        val effective = cfg.effectiveSettings(project)
        if (effective.openToolWindowOnStartup) {
            ToolWindowManager.getInstance(project).invokeLater {
                ToolWindowManager.getInstance(project).getToolWindow("Codex")?.show(null)
            }
        }
    }

    override fun projectClosed(project: Project) {
        val service = ApplicationManager.getApplication()
            .getService(CodexProcessService::class.java)
        CodexShutdown(ServiceStopper(service)).onProjectClosed()
    }
}
