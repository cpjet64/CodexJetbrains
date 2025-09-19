package dev.curt.codexjb.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.curt.codexjb.core.*
import dev.curt.codexjb.proto.*
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

class CodexToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val bus = EventBus()
    val turns = TurnRegistry()
    val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
    val proc = ApplicationManager.getApplication().getService(CodexProcessService::class.java)
    val log = CodexLogger.forClass(CodexToolWindowFactory::class.java)

    val exe = cfg.resolveExecutable(project.basePath?.let { Path.of(it) })
    if (exe == null) {
      val content = ContentFactory.getInstance()
        .createContent(JLabel("Codex CLI not found in PATH"), "Chat", false)
      toolWindow.contentManager.addContent(content)
      return
    }
    val config = CodexProcessConfig(executable = exe)
    proc.start(config)
    attachReader(proc, bus, log)

    val models = arrayOf("gpt-4.1-mini", "gpt-4o-mini")
    val efforts = arrayOf("low", "medium", "high")
    val modelCombo = JComboBox(models)
    val effortCombo = JComboBox(efforts)
    cfg.lastModel?.let { m -> models.indexOf(m).takeIf { it >= 0 }?.let(modelCombo::setSelectedIndex) }
    cfg.lastEffort?.let { r -> efforts.indexOf(r).takeIf { it >= 0 }?.let(effortCombo::setSelectedIndex) }
    val header = JPanel().apply {
      add(JLabel("Model:"))
      add(modelCombo)
      add(JLabel("Effort:"))
      add(effortCombo)
    }
    modelCombo.accessibleContext.accessibleName = "Model selector"
    effortCombo.accessibleContext.accessibleName = "Effort selector"
    modelCombo.addActionListener { cfg.lastModel = modelCombo.selectedItem as String }
    effortCombo.addActionListener { cfg.lastEffort = effortCombo.selectedItem as String }
    val sender = ProtoSender(
      backend = ServiceBackend(proc),
      config = config,
      log = log
    )
    val panel = JPanel(BorderLayout())
    panel.add(header, BorderLayout.NORTH)
    val chat = ChatPanel(
      sender = sender,
      bus = bus,
      turns = turns,
      modelProvider = { modelCombo.selectedItem as String },
      effortProvider = { effortCombo.selectedItem as String },
      cwdProvider = { project.basePath?.let { Path.of(it) } }
    )
    panel.add(chat, BorderLayout.CENTER)

    val content = ContentFactory.getInstance().createContent(panel, "Chat", false)
    toolWindow.contentManager.addContent(content)
  }

  private fun attachReader(service: CodexProcessService, bus: EventBus, log: LogSink) {
    val streams = service.streams() ?: return
    val input = streams.first
    val reader = input.bufferedReader(Charsets.UTF_8)
    Thread {
      try {
        while (true) {
          val line = reader.readLine() ?: break
          bus.dispatch(line)
        }
      } catch (t: Throwable) {
        log.warn("stdout reader ended: ${t.message}")
      }
    }.apply { isDaemon = true; name = "codex-proto-stdout" }.start()
  }
}
