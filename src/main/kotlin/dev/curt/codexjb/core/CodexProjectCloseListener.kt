package dev.curt.codexjb.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

class CodexProjectCloseListener : ProjectManagerListener {
    // Note: projectOpened() is deprecated. Functionality moved to StartupActivity if needed.
    // Keeping only projectClosed() which is still valid.

    override fun projectClosed(project: Project) {
        val service = project.getService(CodexProcessService::class.java)
        CodexShutdown(ServiceStopper(service)).onProjectClosed()
    }
}
