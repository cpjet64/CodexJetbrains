package dev.curt.codexjb.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.Disposable
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
    val sessionState = SessionState(log)
    bus.addListener("SessionConfigured") { id, msg -> sessionState.onEvent(id, msg) }

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
    val approvalModes = ApprovalMode.values()
    val approvalCombo = JComboBox(approvalModes)
    cfg.lastModel?.let { m -> models.indexOf(m).takeIf { it >= 0 }?.let(modelCombo::setSelectedIndex) }
    cfg.lastEffort?.let { r -> efforts.indexOf(r).takeIf { it >= 0 }?.let(effortCombo::setSelectedIndex) }
    cfg.lastApprovalMode?.let { name ->
      approvalModes.indexOfFirst { it.name == name }.takeIf { it >= 0 }
        ?.let(approvalCombo::setSelectedIndex)
    }
    val pushStatusBar = {
      CodexStatusBarController.update(
        modelCombo.selectedItem as String,
        effortCombo.selectedItem as String
      )
    }
    pushStatusBar()
    val header = JPanel().apply {
      add(JLabel("Model:"))
      add(modelCombo)
      add(JLabel("Effort:"))
      add(effortCombo)
      add(JLabel("Approvals:"))
      add(approvalCombo)
    }
    val warn = JLabel("Full Access mode")
    warn.foreground = java.awt.Color(0xB0, 0, 0)
    warn.isVisible = (approvalCombo.selectedItem as ApprovalMode) == ApprovalMode.FULL_ACCESS
    header.add(warn)
    modelCombo.accessibleContext.accessibleName = "Model selector"
    effortCombo.accessibleContext.accessibleName = "Effort selector"
    modelCombo.addActionListener {
      val model = modelCombo.selectedItem as String
      cfg.lastModel = model
      pushStatusBar()
    }
    effortCombo.addActionListener {
      val effort = effortCombo.selectedItem as String
      cfg.lastEffort = effort
      pushStatusBar()
    }
    approvalCombo.addActionListener {
      cfg.lastApprovalMode = (approvalCombo.selectedItem as ApprovalMode).name
      warn.isVisible = (approvalCombo.selectedItem as ApprovalMode) == ApprovalMode.FULL_ACCESS
    }
    approvalCombo.accessibleContext.accessibleName = "Approval mode selector"
    val infoBanner = InfoBanner()
    val sender = ProtoSender(
      backend = ServiceBackend(proc),
      config = config,
      log = log,
      onReconnect = { infoBanner.show("Reconnected to Codex CLI") }
    )
    val heartbeat = HeartbeatScheduler(
      sendHeartbeat = { sender.send(Heartbeat.buildPingSubmission()) },
      log = log
    )
    sender.setOnSendListener { heartbeat.markActivity() }
    heartbeat.start()
    val panel = JPanel(BorderLayout())
    val errorBanner = ErrorBanner().apply { wire(bus) }
    val top = JPanel()
    top.layout = javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS)
    top.add(errorBanner)
    top.add(infoBanner)
    top.add(header)
    panel.add(top, BorderLayout.NORTH)
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
    content.setDisposer(object : Disposable {
      override fun dispose() {
        sender.setOnSendListener(null)
        heartbeat.dispose()
      }
    })
    toolWindow.contentManager.addContent(content)

    val store = ApprovalStore()
    val approvals = ApprovalsController(
      modeProvider = { approvalCombo.selectedItem as ApprovalMode },
      store = store,
      onDecision = { sender.send(it.toString()) },
      log = log,
      prompt = { title, message ->
        javax.swing.JOptionPane.showConfirmDialog(
          panel,
          message,
          title,
          javax.swing.JOptionPane.YES_NO_OPTION
        ) == javax.swing.JOptionPane.YES_OPTION
      },
      promptRationale = { title, message ->
        javax.swing.JOptionPane.showInputDialog(
          panel,
          message,
          title,
          javax.swing.JOptionPane.PLAIN_MESSAGE,
          null,
          null,
          ""
        )?.toString()
      }
    )
    approvals.wire(bus)

    val resetBtn = javax.swing.JButton("Reset approvals")
    resetBtn.addActionListener { store.reset() }
    header.add(resetBtn)

    // Session header and token usage indicators
    val sessionLabel = JLabel("")
    sessionLabel.foreground = java.awt.Color(0x33, 0x33, 0x33)
    header.add(sessionLabel)
    val usageLabel = JLabel("")
    usageLabel.foreground = java.awt.Color(0x55, 0x55, 0x55)
    header.add(usageLabel)

    bus.addListener("SessionConfigured") { _, msg ->
      val m = msg.get("model")?.asString
      val e = msg.get("effort")?.asString
      val r = msg.get("rollout_path")?.asString
      val parts = listOfNotNull(m, e, r?.let { "rollout:" + it.substringAfterLast('/') })
      sessionLabel.text = if (parts.isNotEmpty()) "Session: ${parts.joinToString(" • ")}" else ""
    }

    bus.addListener("TokenCount") { _, msg ->
      val input = msg.get("input")?.asInt ?: 0
      val output = msg.get("output")?.asInt ?: 0
      val total = msg.get("total")?.asInt ?: (input + output)
      usageLabel.text = "Tokens: in=$input out=$output total=$total"
    }
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
