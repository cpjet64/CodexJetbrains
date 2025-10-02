package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.curt.codexjb.core.*
import dev.curt.codexjb.core.DiagnosticsService
import dev.curt.codexjb.core.ProcessHealth
import dev.curt.codexjb.core.ProcessHealthMonitor
import dev.curt.codexjb.core.TurnMetricsService
import dev.curt.codexjb.proto.*
import java.awt.BorderLayout
import java.awt.Component
import java.nio.file.Path
import kotlin.io.path.exists
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
    val proc = project.getService(CodexProcessService::class.java)
    val log = CodexLogger.forClass(CodexToolWindowFactory::class.java)
    val sessionState = SessionState(log)
    bus.addListener("session_configured") { id, msg -> sessionState.onEvent(id, msg) }

    // Create diagnostics panel FIRST so it's always available
    val contentFactory = ContentFactory.getInstance()
    val diagnosticsPanel = DiagnosticsPanel()
    val diagnosticsContent = contentFactory.createContent(diagnosticsPanel, "Diagnostics", false)
    diagnosticsContent.setDisposer(Disposable { diagnosticsPanel.dispose() })
    toolWindow.contentManager.addContent(diagnosticsContent)

    val effectiveSettings = cfg.effectiveSettings(project)

    // Log all environment variables for debugging
    DiagnosticsService.append("=== Environment Variables ===")
    val env = System.getenv()

    // Log PATH/Path - full value first, then broken down
    val pathKey = when (EnvironmentInfo.os) {
      OperatingSystem.WINDOWS -> "Path"
      else -> "PATH"
    }
    val pathVar = env[pathKey] ?: env["PATH"] ?: env["Path"]
    if (pathVar != null) {
      DiagnosticsService.append("$pathKey=$pathVar")
      DiagnosticsService.append("$pathKey (broken down):")
      val sep = if (EnvironmentInfo.os == OperatingSystem.WINDOWS) ';' else ':'
      pathVar.split(sep).forEachIndexed { idx, dir ->
        DiagnosticsService.append("  [$idx] $dir")
      }
    } else {
      DiagnosticsService.append("WARNING: No PATH variable found")
    }

    // Log PATHEXT on Windows - full value first, then broken down
    if (EnvironmentInfo.os == OperatingSystem.WINDOWS) {
      val pathExt = env["PATHEXT"]
      if (pathExt != null) {
        DiagnosticsService.append("PATHEXT=$pathExt")
        DiagnosticsService.append("PATHEXT (broken down):")
        pathExt.split(';').forEachIndexed { idx, ext ->
          DiagnosticsService.append("  [$idx] $ext")
        }
      } else {
        DiagnosticsService.append("WARNING: No PATHEXT variable found")
      }
    }

    // Log critical OS-specific environment variables
    when (EnvironmentInfo.os) {
      OperatingSystem.WINDOWS -> {
        DiagnosticsService.append("Windows System Environment Variables:")

        val systemVars = listOf(
          "APPDATA", "COMSPEC", "HOMEDRIVE", "HOMEPATH", "LOCALAPPDATA",
          "POWERSHELL", "PROGRAMDATA", "PROGRAMFILES", "PWSH",
          "SYSTEMDRIVE", "SYSTEMROOT", "TEMP", "TMP", "USERPROFILE"
        )

        systemVars.forEach { key ->
          val value = env[key]
          if (value != null) {
            DiagnosticsService.append("  $key=$value")
          } else {
            DiagnosticsService.append("  $key=(not set)")
          }
        }

        DiagnosticsService.append("Windows Development Environment Variables:")

        val msvcVars = listOf(
          // Compiler/toolchain paths
          "INCLUDE", "LIB", "LIBPATH",
          // Visual Studio installation context
          "VSINSTALLDIR", "VCINSTALLDIR", "VCToolsInstallDir", "VCToolsVersion", "VCToolsRedistDir",
          "VisualStudioVersion", "VS170COMNTOOLS", "DevEnvDir", "VCIDEInstallDir",
          // Windows SDK context
          "WindowsSdkDir", "WindowsSDKVersion", "WindowsSDKLibVersion",
          "WindowsSdkBinPath", "WindowsSdkVerBinPath", "WindowsLibPath",
          "UCRTVersion", "UniversalCRTSdkDir", "ExtensionSdkDir",
          // .NET references
          "FrameworkDir", "FrameworkDir64", "FrameworkVersion", "FrameworkVersion64"
        )

        msvcVars.forEach { key ->
          val value = env[key]
          if (value != null) {
            DiagnosticsService.append("  $key=$value")
          } else {
            DiagnosticsService.append("  $key=(not set)")
          }
        }

        // Log VSCMD_ prefixed variables
        val vscmdVars = env.keys.filter { it.startsWith("VSCMD_") }.sorted()
        if (vscmdVars.isNotEmpty()) {
          DiagnosticsService.append("Developer PowerShell/Command Prompt variables:")
          vscmdVars.forEach { key ->
            DiagnosticsService.append("  $key=${env[key]}")
          }
        }
      }

      OperatingSystem.MAC -> {
        DiagnosticsService.append("macOS System Environment Variables:")

        val systemVars = listOf(
          "HOME", "USER", "LOGNAME", "SHELL", "TMPDIR", "TERM",
          "LANG", "LC_ALL", "LC_CTYPE"
        )

        systemVars.forEach { key ->
          val value = env[key]
          if (value != null) {
            DiagnosticsService.append("  $key=$value")
          } else {
            DiagnosticsService.append("  $key=(not set)")
          }
        }

        DiagnosticsService.append("macOS Development Environment Variables:")

        val devVars = listOf(
          // Xcode/Apple development tools
          "DEVELOPER_DIR", "SDKROOT", "XCODE_VERSION",
          // Homebrew
          "HOMEBREW_PREFIX", "HOMEBREW_CELLAR", "HOMEBREW_REPOSITORY",
          // Package managers
          "PKG_CONFIG_PATH", "CPATH", "LIBRARY_PATH",
          // Compilers
          "CC", "CXX", "CFLAGS", "CXXFLAGS", "LDFLAGS"
        )

        devVars.forEach { key ->
          val value = env[key]
          if (value != null) {
            DiagnosticsService.append("  $key=$value")
          } else {
            DiagnosticsService.append("  $key=(not set)")
          }
        }
      }

      OperatingSystem.LINUX -> {
        DiagnosticsService.append("Linux System Environment Variables:")

        val systemVars = listOf(
          "HOME", "USER", "LOGNAME", "SHELL", "TERM",
          "LANG", "LC_ALL", "LC_CTYPE", "DISPLAY", "WAYLAND_DISPLAY",
          "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_CACHE_HOME",
          "XDG_RUNTIME_DIR", "XDG_SESSION_TYPE"
        )

        systemVars.forEach { key ->
          val value = env[key]
          if (value != null) {
            DiagnosticsService.append("  $key=$value")
          } else {
            DiagnosticsService.append("  $key=(not set)")
          }
        }

        DiagnosticsService.append("Linux Development Environment Variables:")

        val devVars = listOf(
          // Package managers and build systems
          "PKG_CONFIG_PATH", "LD_LIBRARY_PATH", "LIBRARY_PATH", "CPATH",
          // Compilers and toolchains
          "CC", "CXX", "CFLAGS", "CXXFLAGS", "LDFLAGS",
          // Build system paths
          "CMAKE_PREFIX_PATH", "ACLOCAL_PATH",
          // Distribution-specific package managers
          "CARGO_HOME", "RUSTUP_HOME", "GOPATH", "GOROOT",
          "JAVA_HOME", "MAVEN_HOME", "GRADLE_HOME"
        )

        devVars.forEach { key ->
          val value = env[key]
          if (value != null) {
            DiagnosticsService.append("  $key=$value")
          } else {
            DiagnosticsService.append("  $key=(not set)")
          }
        }
      }

      else -> {
        DiagnosticsService.append("Unknown OS - skipping OS-specific variables")
      }
    }

    // Log all other environment variables (alphabetically, one per line)
    DiagnosticsService.append("All environment variables:")
    env.keys.sorted().filter { it != "Path" && it != "PATH" && it != "PATHEXT" }.forEach { key ->
      val value = env[key] ?: ""
      DiagnosticsService.append("$key=$value")
    }
    DiagnosticsService.append("=== End Environment Variables ===")

    val exe = effectiveSettings.cliPath ?: cfg.resolveExecutable(project.basePath?.let { Path.of(it) })
    if (exe == null) {
      DiagnosticsService.append("ERROR: Codex CLI not found")
      val content = contentFactory.createContent(JLabel("Codex CLI not found in PATH"), "Chat", false)
      toolWindow.contentManager.addContent(content)
      return
    }
    DiagnosticsService.append(
      "Resolved Codex CLI: " + exe.toString() +
        " | os=" + EnvironmentInfo.os +
        " | wsl_preference=" + effectiveSettings.useWsl +
        (project.basePath?.let { " | project_base=\"$it\"" } ?: "")
    )
    val baseConfig = CodexProcessConfig(executable = exe)
    val useWsl = effectiveSettings.useWsl && EnvironmentInfo.os == OperatingSystem.WINDOWS

    // PowerShell scripts (.ps1) are npm launcher wrappers that call node.exe
    // Instead of nesting PowerShell processes (which breaks stdout capture),
    // parse the .ps1 and call node.exe directly
    val isPowerShellScript = exe.toString().lowercase().endsWith(".ps1")

    val processConfig = when {
      useWsl -> CodexProcessConfig(
        executable = Path.of("wsl"),
        arguments = listOf("codex") + baseConfig.arguments,
        workingDirectory = baseConfig.workingDirectory,
        environment = baseConfig.environment,
        inheritParentEnvironment = baseConfig.inheritParentEnvironment
      )
      isPowerShellScript && EnvironmentInfo.os == OperatingSystem.WINDOWS -> {
        // Extract the actual node.exe command from the PowerShell wrapper
        // PowerShell launchers follow the pattern: node.exe path/to/script.js $args
        val scriptDir = exe.parent
        val nodeExe = scriptDir?.resolve("node.exe") ?: Path.of("node.exe")
        val jsScript = scriptDir?.resolve("node_modules/@openai/codex/bin/codex.js")

        if (jsScript != null && jsScript.exists()) {
          DiagnosticsService.append("DEBUG: Bypassing .ps1 wrapper, calling node.exe directly")
          DiagnosticsService.append("DEBUG: node: $nodeExe, script: $jsScript")
          CodexProcessConfig(
            executable = nodeExe,
            arguments = listOf(jsScript.toString()) + baseConfig.arguments,
            workingDirectory = baseConfig.workingDirectory,
            environment = baseConfig.environment,
            inheritParentEnvironment = baseConfig.inheritParentEnvironment
          )
        } else {
          DiagnosticsService.append("WARN: Could not find codex.js, falling back to PowerShell wrapper")
          CodexProcessConfig(
            executable = Path.of("powershell.exe"),
            arguments = listOf("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", exe.toString()) + baseConfig.arguments,
            workingDirectory = baseConfig.workingDirectory,
            environment = baseConfig.environment,
            inheritParentEnvironment = baseConfig.inheritParentEnvironment
          )
        }
      }
      else -> baseConfig
    }

    // Start process
    DiagnosticsService.append("Starting Codex CLI with shell: ${processConfig.executable}")
    DiagnosticsService.append("Full command: ${processConfig.executable} ${processConfig.arguments.joinToString(" ")}")
    proc.start(processConfig)
    DiagnosticsService.append("Process started, checking if running: ${proc.isRunning()}")
    val (stdoutThread, stderrThread) = attachReader(proc, bus, log)

    // Wait for CLI to initialize (session_configured message)
    // Note: Timeout must account for MCP server startup time (can take 10+ seconds)
    val initialized = java.util.concurrent.CountDownLatch(1)
    val initTimeoutMs = 20000L  // 20 seconds to allow for MCP server initialization

    bus.addListener("session_configured") { _, _ ->
      initialized.countDown()
    }

    // Block briefly to wait for initialization
    val ready = kotlin.runCatching {
      initialized.await(initTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }.getOrDefault(false)

    if (!ready) {
      DiagnosticsService.append("ERROR: Codex CLI did not respond within ${initTimeoutMs}ms")
      log.error("Codex CLI initialization timeout")
      val content = contentFactory.createContent(
          JLabel("<html>Codex CLI failed to initialize.<br>Check Diagnostics panel for details.</html>"),
          "Chat",
          false
        )
      toolWindow.contentManager.addContent(content)
      return
    }

    DiagnosticsService.append("Codex CLI initialized successfully")

    val models = cfg.availableModels.toTypedArray()
    val sandboxOptions = CodexSettingsOptions.SANDBOX_POLICIES.toTypedArray()
    val approvalLevels = CodexSettingsOptions.APPROVAL_LEVELS.toTypedArray()
    val modelCombo = JComboBox(models)
    val reasoningCombo = JComboBox<String>()
    val approvalCombo = JComboBox(approvalLevels)
    val sandboxCombo = JComboBox(DefaultComboBoxModel(sandboxOptions))

    fun selectModel(initial: String?) {
      initial?.let { models.indexOf(it).takeIf { idx -> idx >= 0 }?.let(modelCombo::setSelectedIndex) }
    }
    fun selectApproval(initial: String?) {
      initial?.let {
        approvalLevels.indexOfFirst { option -> option.mode.name == it }
          .takeIf { idx -> idx >= 0 }
          ?.let(approvalCombo::setSelectedIndex)
      }
    }

    fun refreshReasoningOptions(preferred: String? = null) {
      val selectedModel = modelCombo.selectedItem as? String
      val allowed = CodexSettingsOptions.reasoningLevelsForModel(selectedModel)
      val previous = preferred ?: (reasoningCombo.selectedItem as? String) ?: cfg.lastEffort ?: effectiveSettings.defaultEffort
      val model = DefaultComboBoxModel(allowed.toTypedArray())
      reasoningCombo.model = model
      val target = when {
        previous != null && allowed.contains(previous) -> previous
        allowed.isNotEmpty() -> allowed.first()
        else -> null
      }
      if (target != null) {
        reasoningCombo.selectedItem = target
      } else {
        reasoningCombo.selectedIndex = -1
      }
    }

    selectModel(cfg.lastModel ?: effectiveSettings.defaultModel)
    refreshReasoningOptions(cfg.lastEffort ?: effectiveSettings.defaultEffort)
    selectApproval(cfg.lastApprovalMode ?: effectiveSettings.defaultApprovalMode)

    if (modelCombo.selectedIndex < 0 && models.isNotEmpty()) modelCombo.selectedIndex = 0
    if (reasoningCombo.selectedIndex < 0) reasoningCombo.selectedIndex = 0
    if (approvalCombo.selectedIndex < 0) approvalCombo.selectedItem = CodexSettingsOptions.APPROVAL_LEVELS.first()
    sandboxCombo.renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: javax.swing.JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
      ): Component {
        val renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is CodexSettingsOptions.SandboxOption) {
          text = value.label
        }
        return renderer
      }
    }
    approvalCombo.renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: javax.swing.JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
      ): Component {
        val renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is CodexSettingsOptions.ApprovalLevelOption) {
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
      CodexStatusBarController.updateSession(
        modelCombo.selectedItem as String,
        reasoningCombo.selectedItem as String
      )
    }
    pushStatusBar()
    val header = JPanel().apply {
      add(JLabel("Model:"))
      add(modelCombo)
      add(JLabel("Reasoning:"))
      add(reasoningCombo)
      add(JLabel("Approval:"))
      add(approvalCombo)
      add(JLabel("Sandbox:"))
      add(sandboxCombo)
    }
    val approvalWarn = JLabel("Full Access mode")
    approvalWarn.foreground = java.awt.Color(0xB0, 0, 0)
    val initialApproval = approvalCombo.selectedItem as CodexSettingsOptions.ApprovalLevelOption
    approvalWarn.isVisible = initialApproval.mode == ApprovalMode.FULL_ACCESS
    header.add(approvalWarn)
    val sandboxWarn = JLabel("Sandbox: Full Access")
    sandboxWarn.foreground = java.awt.Color(0xB0, 0, 0)
    sandboxWarn.isVisible = initialSandbox.id == "danger-full-access"
    header.add(sandboxWarn)
    modelCombo.accessibleContext.accessibleName = "Model selector"
    reasoningCombo.accessibleContext.accessibleName = "Reasoning level selector"
    approvalCombo.accessibleContext.accessibleName = "Approval level selector"
    sandboxCombo.accessibleContext.accessibleName = "Sandbox policy selector"
    modelCombo.addActionListener {
      val model = modelCombo.selectedItem as String
      cfg.lastModel = model
      refreshReasoningOptions()
      pushStatusBar()
    }
    reasoningCombo.addActionListener {
      val reasoning = reasoningCombo.selectedItem as String
      cfg.lastEffort = reasoning
      pushStatusBar()
    }
    approvalCombo.addActionListener {
      val option = approvalCombo.selectedItem as CodexSettingsOptions.ApprovalLevelOption
      cfg.lastApprovalMode = option.mode.name
      approvalWarn.isVisible = option.mode == ApprovalMode.FULL_ACCESS
    }
    sandboxCombo.addActionListener {
      val option = sandboxCombo.selectedItem as CodexSettingsOptions.SandboxOption
      cfg.lastSandboxPolicy = option.id
      sandboxWarn.isVisible = option.id == "danger-full-access"
    }
    val infoBanner = InfoBanner()
    val processMonitor = ProcessHealthMonitor(proc, processConfig)
    processMonitor.start()

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
      reasoningProvider = { reasoningCombo.selectedItem as String },
      cwdProvider = { project.basePath?.let { Path.of(it) } },
      sandboxProvider = { (sandboxCombo.selectedItem as CodexSettingsOptions.SandboxOption).id }
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

    val content = contentFactory.createContent(panel, "Chat", false)
    content.setDisposer(object : Disposable {
      override fun dispose() {
        sender.setOnSendListener(null)
        heartbeat.dispose()
        processMonitor.close()

        // Interrupt reader threads to ensure clean shutdown
        stdoutThread.interrupt()
        stderrThread.interrupt()

        // Wait briefly for threads to exit
        kotlin.runCatching { stdoutThread.join(1000) }
        kotlin.runCatching { stderrThread.join(1000) }
      }
    })
    toolWindow.contentManager.addContent(content)

    if (effectiveSettings.openToolWindowOnStartup) {
      SwingUtilities.invokeLater { toolWindow.show(null) }
    }

    val store = ApprovalStore()
    val approvals = ApprovalsController(
      modeProvider = { (approvalCombo.selectedItem as CodexSettingsOptions.ApprovalLevelOption).mode },
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

    bus.addListener("session_configured") { _, msg ->
      val m = msg.get("model")?.asString
      val e = msg.get("effort")?.asString
      val r = msg.get("rollout_path")?.asString
      val parts = listOfNotNull(m, e, r?.let { "rollout:" + it.substringAfterLast('/') })
      sessionLabel.text = if (parts.isNotEmpty()) "Session: ${parts.joinToString(" • ")}" else ""
    }

    bus.addListener("TokenCount") { id, msg ->
      val input = msg.get("input")?.asInt ?: 0
      val output = msg.get("output")?.asInt ?: 0
      val total = msg.get("total")?.asInt ?: (input + output)
      usageLabel.text = "Tokens: in=$input out=$output total=$total"
      TurnMetricsService.onTokenCount(id, total)
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

  private fun attachReader(
    service: CodexProcessService,
    bus: EventBus,
    log: LogSink
  ): Pair<Thread, Thread> {
    val streams = service.streams() ?: return Pair(Thread(), Thread())
    val stdout = streams.first.bufferedReader(Charsets.UTF_8)
    val stderr = streams.second.bufferedReader(Charsets.UTF_8)

    val stdoutThread = Thread {
      try {
        DiagnosticsService.append("DEBUG: stdout reader thread started")
        while (true) {
          val line = stdout.readLine() ?: break
          DiagnosticsService.append("DEBUG: stdout received: ${line.take(100)}")
          ProcessHealth.onStdout()
          CodexStatusBarController.updateHealth(ProcessHealth.Status.OK)
          bus.dispatch(line)
        }
        DiagnosticsService.append("DEBUG: stdout reader thread ended (EOF)")
      } catch (t: Throwable) {
        if (t is InterruptedException) {
          log.info("stdout reader interrupted (expected on shutdown)")
        } else {
          log.warn("stdout reader ended: ${t.message}")
          DiagnosticsService.append("DEBUG: stdout reader error: ${t.message}")
        }
      }
    }.apply { isDaemon = true; name = "codex-proto-stdout" }

    val stderrThread = Thread {
      try {
        DiagnosticsService.append("DEBUG: stderr reader thread started")
        while (true) {
          val line = stderr.readLine() ?: break
          DiagnosticsService.append(line)
        }
        DiagnosticsService.append("DEBUG: stderr reader thread ended (EOF)")
      } catch (t: Throwable) {
        if (t is InterruptedException) {
          log.info("stderr reader interrupted (expected on shutdown)")
        } else {
          log.warn("stderr reader ended: ${t.message}")
          DiagnosticsService.append("DEBUG: stderr reader error: ${t.message}")
        }
      }
    }.apply { isDaemon = true; name = "codex-proto-stderr" }

    stdoutThread.start()
    stderrThread.start()

    return Pair(stdoutThread, stderrThread)
  }

  private fun buildCancelExecSubmission(execId: String): String {
    val body = JsonObject().apply {
      addProperty("type", "CancelExecCommand")
      addProperty("exec_id", execId)
    }
    return EnvelopeJson.encodeSubmission(SubmissionEnvelope(Ids.newId(), "Submit", body))
  }
}








