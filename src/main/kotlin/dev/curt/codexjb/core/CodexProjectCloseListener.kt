package dev.curt.codexjb.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class CodexProjectCloseListener : ProjectManagerListener {
    override fun projectClosed(project: Project) {
        val service = ApplicationManager.getApplication()
            .getService(CodexProcessService::class.java)
        CodexShutdown(ServiceStopper(service)).onProjectClosed()
    }
}
