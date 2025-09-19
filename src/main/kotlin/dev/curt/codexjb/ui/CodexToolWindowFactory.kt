package dev.curt.codexjb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JLabel

/**
 * Minimal stub that will be replaced with the full-featured chat UI in T3.
 */
class CodexToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val content = ContentFactory.getInstance()
      .createContent(JLabel("Codex chat coming soon"), "Chat", false)
    toolWindow.contentManager.addContent(content)
  }
}
