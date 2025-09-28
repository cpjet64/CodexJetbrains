package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.curt.codexjb.core.*
import dev.curt.codexjb.proto.*
import java.awt.BorderLayout
import java.awt.Component
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class CodexToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val bus = EventBus()
    val turns = TurnRegistry()
    val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
    val proc = ApplicationManager.getApplication().getService(CodexProcessService::class.java)
    val log = CodexLogger.forClass(CodexToolWindowFactory::class.java)
    val sessionState = SessionState(log)
    bus.addListener("SessionConfigured") { id, msg -> sessionState.onEvent(id, msg) }

    val effectiveSettings = cfg.effectiveSettings(project)
    val exe = effectiveSettings.cliPath ?: cfg.resolveExecutable(project.basePath?.let { Path.of(it) })
    if (exe == null) {
      val content = ContentFactory.getInstance()
        .createContent(JLabel("Codex CLI not found in PATH"), "Chat", false)
      toolWindow.contentManager.addContent(content)
      return
    }
    val baseConfig = CodexProcessConfig(executable = exe)
    val useWsl = effectiveSettings.useWsl && EnvironmentInfo.os == OperatingSystem.WINDOWS
    val processConfig = if (useWsl) {
      CodexProcessConfig(
        executable = Path.of("wsl"),
        arguments = listOf("codex") + baseConfig.arguments,
        workingDirectory = baseConfig.workingDirectory,
        environment = baseConfig.environment,
        inheritParentEnvironment = baseConfig.inheritParentEnvironment
      )
    } else baseConfig
    proc.start(processConfig)
    attachReader(proc, bus, log)

    val models = CodexDefaults.MODELS.toTypedArray()
    val efforts = CodexDefaults.EFFORTS.toTypedArray()
    val sandboxOptions = CodexDefaults.SANDBOX_POLICIES.toTypedArray()
    val modelCombo = JComboBox(models)
    val effortCombo = JComboBox(efforts)
    val approvalModes = ApprovalMode.values()
    val approvalCombo = JComboBox(approvalModes)
    val sandboxCombo = JComboBox(DefaultComboBoxModel(sandboxOptions))

    fun selectModel(initial: String?) {
      initial?.let { models.indexOf(it).takeIf { idx -> idx >= 0 }?.let(modelCombo::setSelectedIndex) }
    }
    fun selectEffort(initial: String?) {
      initial?.let { efforts.indexOf(it).takeIf { idx -> idx >= 0 }?.let(effortCombo::setSelectedIndex) }
    }
    fun selectApproval(initial: String?) {
      initial?.let {
        approvalModes.indexOfFirst { mode -> mode.name == it }
          .takeIf { idx -> idx >= 0 }
          ?.let(approvalCombo::setSelectedIndex)
      }
    }

    selectModel(cfg.lastModel ?: effectiveSettings.defaultModel)
    selectEffort(cfg.lastEffort ?: effectiveSettings.defaultEffort)
    selectApproval(cfg.lastApprovalMode ?: effectiveSettings.defaultApprovalMode)

    if (modelCombo.selectedIndex < 0) modelCombo.selectedIndex = 0
    if (effortCombo.selectedIndex < 0) effortCombo.selectedIndex = 0
    if (approvalCombo.selectedIndex < 0) approvalCombo.selectedItem = ApprovalMode.CHAT

    sandboxCombo.renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: javax.swing.JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
      ): Component {
        val renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is CodexDefaults.SandboxOption) {
          text = value.label
        }
        return renderer
      }
    }
    val initialSandboxId = cfg.lastSandboxPolicy
      ?: effectiveSettings.defaultSandboxPolicy
      ?: sandboxOptions.first().id
    val initialSandbox = sandboxOptions.firstOrNull { it.id == initialSandboxId } ?: sandboxOptions.first()
    sandboxCombo.selectedItem = initialSandbox
    cfg.lastSandboxPolicy = initialSandbox.id
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
      add(JLabel("Sandbox:"))
      add(sandboxCombo)
    }
    val approvalWarn = JLabel("Full Access mode")
    approvalWarn.foreground = java.awt.Color(0xB0, 0, 0)
    approvalWarn.isVisible = (approvalCombo.selectedItem as ApprovalMode) == ApprovalMode.FULL_ACCESS
    header.add(approvalWarn)
    val sandboxWarn = JLabel("Sandbox: Full Access")
    sandboxWarn.foreground = java.awt.Color(0xB0, 0, 0)
    sandboxWarn.isVisible = initialSandbox.id == "danger-full-access"
    header.add(sandboxWarn)
    modelCombo.accessibleContext.accessibleName = "Model selector"
    effortCombo.accessibleContext.accessibleName = "Effort selector"
    approvalCombo.accessibleContext.accessibleName = "Approval mode selector"
    sandboxCombo.accessibleContext.accessibleName = "Sandbox policy selector"
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
      approvalWarn.isVisible = (approvalCombo.selectedItem as ApprovalMode) == ApprovalMode.FULL_ACCESS
    }
    sandboxCombo.addActionListener {
      val option = sandboxCombo.selectedItem as CodexDefaults.SandboxOption
      cfg.lastSandboxPolicy = option.id
      sandboxWarn.isVisible = option.id == "danger-full-access"
    }
    val infoBanner = InfoBanner()
    val sender = ProtoSender(
      backend = ServiceBackend(proc),
      config = processConfig,
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
      project = project,
      modelProvider = { modelCombo.selectedItem as String },
      effortProvider = { effortCombo.selectedItem as String },
      cwdProvider = { project.basePath?.let { Path.of(it) } },
      sandboxProvider = { (sandboxCombo.selectedItem as CodexDefaults.SandboxOption).id }
    )
    panel.add(chat, BorderLayout.CENTER)

    val consolePanel = ExecConsolePanel().apply { wire(bus) }
    val consoleWrapper = JPanel(BorderLayout()).apply {
      border = EmptyBorder(8, 8, 8, 8)
      add(consolePanel, BorderLayout.CENTER)
      isVisible = cfg.consoleVisible
      preferredSize = java.awt.Dimension(0, 180)
    }
    panel.add(consoleWrapper, BorderLayout.SOUTH)

    val content = ContentFactory.getInstance().createContent(panel, "Chat", false)
    content.setDisposer(object : Disposable {
      override fun dispose() {
        sender.setOnSendListener(null)
        heartbeat.dispose()
      }
    })
    toolWindow.contentManager.addContent(content)

    if (effectiveSettings.openToolWindowOnStartup) {
      SwingUtilities.invokeLater { toolWindow.show(null) }
    }

    val store = ApprovalStore()
    val approvals = ApprovalsController(
      modeProvider = { approvalCombo.selectedItem as ApprovalMode },
      store = store,
      onDecision = {
        val body = it.getAsJsonObject("body")
        if (body.get("type")?.asString == "ExecApprovalDecision") {
          val status = if (body.get("allow")?.asBoolean == true) "APPROVED" else "DENIED"
          SwingUtilities.invokeLater { consolePanel.setApprovalStatus(status) }
        }
        sender.send(it.toString())
      },
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

    consolePanel.setCancelHandler { execId ->
      sender.send(buildCancelExecSubmission(execId))
      infoBanner.show("Cancel request sent for exec command")
    }

    val consoleToggle = JToggleButton("Console").apply {
      isSelected = cfg.consoleVisible
      addActionListener {
        val visible = isSelected
        consoleWrapper.isVisible = visible
        cfg.consoleVisible = visible
        panel.revalidate()
        panel.repaint()
      }
      accessibleContext.accessibleName = "Toggle exec console visibility"
    }
    header.add(consoleToggle)

    val autoOpenConsoleCheck = JCheckBox("Auto-open exec console").apply {
      isSelected = cfg.autoOpenConsoleOnExec
      addActionListener { cfg.autoOpenConsoleOnExec = isSelected }
      accessibleContext.accessibleName = "Auto-open exec console"
    }
    header.add(autoOpenConsoleCheck)

    val ensureConsoleVisible = {
      if (!consoleWrapper.isVisible) {
        consoleWrapper.isVisible = true
        cfg.consoleVisible = true
      }
      if (!consoleToggle.isSelected) consoleToggle.isSelected = true
      panel.revalidate()
      panel.repaint()
    }

    bus.addListener("ExecCommandBegin") { _, _ ->
      SwingUtilities.invokeLater {
        if (cfg.autoOpenConsoleOnExec) ensureConsoleVisible()
      }
    }

    bus.addListener("ExecApprovalRequest") { id, msg ->
      val cwd = msg.get("cwd")?.asString ?: ""
      val command = msg.get("command")?.asString ?: ""
      SwingUtilities.invokeLater {
        if (cfg.autoOpenConsoleOnExec) ensureConsoleVisible()
        consolePanel.showPending(id, cwd, command)
        consolePanel.setApprovalStatus("PENDING")
      }
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

  private fun buildCancelExecSubmission(execId: String): String {
    val body = JsonObject().apply {
      addProperty("type", "CancelExecCommand")
      addProperty("exec_id", execId)
    }
    return EnvelopeJson.encodeSubmission(SubmissionEnvelope(Ids.newId(), "Submit", body))
  }
}
