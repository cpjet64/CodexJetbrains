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
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.nio.file.Path
import kotlin.io.path.exists
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JButton
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

    // Listen for error events from app-server
    bus.addListener("error") { _, msg ->
      val errorMessage = msg.get("message")?.asString ?: "Unknown error"
      DiagnosticsService.append("App Server Error: $errorMessage")
      log.warn("App server error: $errorMessage")
    }

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
      // Only show the broken-down PATH entries to reduce noise
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
        // Only show the broken-down PATHEXT entries to reduce noise
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

    // Show loading panel while version check runs
    val loadingPanel = JLabel("<html><center>Checking Codex CLI version...<br>Please wait...</center></html>")
    loadingPanel.horizontalAlignment = JLabel.CENTER
    val loadingContent = contentFactory.createContent(loadingPanel, "Chat", false)
    toolWindow.contentManager.addContent(loadingContent)

    // Check Codex CLI version BEFORE transforming the config (must use original exe, not node.exe)
    DiagnosticsService.append("Checking Codex CLI version (async)...")
    CodexVersionCheck.checkAppServerSupport(exe).thenAccept { (isSupported, installedVersion) ->
      // Remove loading panel
      SwingUtilities.invokeLater {
        toolWindow.contentManager.removeContent(loadingContent, true)
      }

      if (!isSupported) {
        val versionMsg = installedVersion?.let { "version $it" } ?: "unknown version"
        val errorMsg = """
          Codex CLI $versionMsg does not support app-server protocol.

          Please update to version 0.44.0 or later:
          npm install -g @openai/codex@latest
        """.trimIndent()

        DiagnosticsService.append("ERROR: $errorMsg")
        log.error(errorMsg)

        SwingUtilities.invokeLater {
          val content = contentFactory.createContent(
            JLabel("<html>${errorMsg.replace("\n", "<br>")}</html>"),
            "Chat",
            false
          )
          toolWindow.contentManager.addContent(content)
        }
        return@thenAccept
      }

      // Continue with initialization on EDT
      SwingUtilities.invokeLater {
        initializeToolWindow(
          project, toolWindow, exe, effectiveSettings,
          contentFactory, bus, turns, cfg, proc, log
        )
      }
    }.exceptionally { e ->
      val actualException = if (e is java.util.concurrent.CompletionException) e.cause ?: e else e

      when (actualException) {
        is java.util.concurrent.TimeoutException -> {
          DiagnosticsService.append("ERROR: Version check timed out after 15 seconds")
          log.error("Version check timed out", actualException)

          SwingUtilities.invokeLater {
            toolWindow.contentManager.removeContent(loadingContent, true)
            val content = contentFactory.createContent(
              JLabel("<html>Version check timed out.<br>The 'codex --version' command did not respond.<br>Check Diagnostics panel for details.</html>"),
              "Chat",
              false
            )
            toolWindow.contentManager.addContent(content)
          }
        }
        else -> {
          DiagnosticsService.append("ERROR: Version check failed: ${actualException.message}")
          log.error("Version check failed", actualException)

          SwingUtilities.invokeLater {
            toolWindow.contentManager.removeContent(loadingContent, true)
            val content = contentFactory.createContent(
              JLabel("<html>Version check failed: ${actualException.message ?: "unknown error"}<br>Check Diagnostics panel for details.</html>"),
              "Chat",
              false
            )
            toolWindow.contentManager.addContent(content)
          }
        }
      }
      null
    }
  }

  private fun initializeToolWindow(
    project: Project,
    toolWindow: ToolWindow,
    exe: Path,
    effectiveSettings: EffectiveSettings,
    contentFactory: ContentFactory,
    bus: EventBus,
    turns: TurnRegistry,
    cfg: CodexConfigService,
    proc: CodexProcessService,
    log: LogSink
  ) {
    val projectDir = project.basePath?.let { Path.of(it) }
    val baseConfig = CodexProcessConfig(
        executable = exe,
        workingDirectory = projectDir
    )
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
        // Try to bypass PowerShell wrapper for better performance
        // This works with all node installation methods (NVM, fnm, Volta, Scoop, Chocolatey, standard installer)
        val nodeExe = ExecutableDiscovery.findNode()
        val jsScript = ExecutableDiscovery.findCodexScript(exe)

        if (nodeExe != null && jsScript != null) {
          DiagnosticsService.append("Bypassing .ps1 wrapper: node=$nodeExe, script=$jsScript")
          CodexProcessConfig(
            executable = nodeExe,
            arguments = listOf(jsScript.toString()) + baseConfig.arguments,
            workingDirectory = baseConfig.workingDirectory,
            environment = baseConfig.environment,
            inheritParentEnvironment = baseConfig.inheritParentEnvironment
          )
        } else {
          DiagnosticsService.append("Using PowerShell wrapper for .ps1 script (node=${nodeExe != null}, script=${jsScript != null})")
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

    // Note: Process is started by AppServerProtocol.start()
    DiagnosticsService.append("Starting Codex CLI with shell: ${processConfig.executable}")
    DiagnosticsService.append("Full command: ${processConfig.executable} ${processConfig.arguments.joinToString(" ")}")

    DiagnosticsService.append("Building UI components...")
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
    // === Structured header with vertical rows ===
    val header = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = EmptyBorder(CodexTheme.panelPadding, CodexTheme.panelPadding, CodexTheme.panelPadding, CodexTheme.panelPadding)
    }

    // Row 2: Selectors (Model, Reasoning, Approval, Sandbox)
    val selectorsRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
      alignmentX = Component.LEFT_ALIGNMENT
    }
    fun addSelectorLabel(text: String) = JLabel(text).apply {
      font = CodexTheme.secondaryFont
      foreground = CodexTheme.secondaryLabelFg
    }
    selectorsRow.add(addSelectorLabel("Model:"))
    modelCombo.preferredSize = Dimension(130, 24)
    selectorsRow.add(modelCombo)
    selectorsRow.add(addSelectorLabel("Reasoning:"))
    reasoningCombo.preferredSize = Dimension(130, 24)
    selectorsRow.add(reasoningCombo)
    selectorsRow.add(addSelectorLabel("Approval:"))
    approvalCombo.preferredSize = Dimension(130, 24)
    selectorsRow.add(approvalCombo)
    selectorsRow.add(addSelectorLabel("Sandbox:"))
    sandboxCombo.preferredSize = Dimension(130, 24)
    selectorsRow.add(sandboxCombo)
    header.add(selectorsRow)
    header.add(Box.createVerticalStrut(CodexTheme.sectionGap))

    // Row 3: Warning labels (conditional)
    val warningsRow = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
      alignmentX = Component.LEFT_ALIGNMENT
    }
    val approvalWarn = JLabel("Full Access mode").apply {
      foreground = CodexTheme.warningFg
      font = CodexTheme.secondaryFont.deriveFont(Font.BOLD)
    }
    val initialApproval = approvalCombo.selectedItem as CodexSettingsOptions.ApprovalLevelOption
    approvalWarn.isVisible = initialApproval.mode == ApprovalMode.FULL_ACCESS
    warningsRow.add(approvalWarn)
    val sandboxWarn = JLabel("Sandbox: Full Access").apply {
      foreground = CodexTheme.warningFg
      font = CodexTheme.secondaryFont.deriveFont(Font.BOLD)
    }
    sandboxWarn.isVisible = initialSandbox.id == "danger-full-access"
    warningsRow.add(sandboxWarn)
    header.add(warningsRow)
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

    DiagnosticsService.append("Creating AppServerProtocol...")
    // Initialize AppServerProtocol
    val protocol = AppServerProtocol(
      processService = proc,
      processConfig = processConfig,
      eventBus = bus
    )

    // Show initializing panel
    val initializingPanel = JLabel("<html><center>Initializing Codex protocol...<br>Please wait...</center></html>")
    initializingPanel.horizontalAlignment = JLabel.CENTER
    val initializingContent = contentFactory.createContent(initializingPanel, "Chat", false)
    toolWindow.contentManager.addContent(initializingContent)

    DiagnosticsService.append("Starting protocol initialization (async)...")
    // Start protocol asynchronously
    val protocolFuture = try {
      protocol.start()
    } catch (e: Exception) {
      DiagnosticsService.append("ERROR: Failed to start protocol: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
      DiagnosticsService.append("Stack trace: ${e.stackTraceToString()}")
      log.error("Protocol start failed", e)
      SwingUtilities.invokeLater {
        toolWindow.contentManager.removeContent(initializingContent, true)
        val content = contentFactory.createContent(
          JLabel("<html>Failed to start protocol: ${e.message ?: "unknown error"}<br>Check Diagnostics panel for details.</html>"),
          "Chat",
          false
        )
        toolWindow.contentManager.addContent(content)
      }
      return
    }

    // Handle protocol initialization asynchronously (30 second timeout)
    protocolFuture
      .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
      .thenAccept { conversationId ->
        DiagnosticsService.append("Protocol initialized with conversation ID: $conversationId")

        // Build the UI on EDT
        SwingUtilities.invokeLater {
          toolWindow.contentManager.removeContent(initializingContent, true)
          buildChatUI(
            project, toolWindow, protocol, bus, turns, cfg,
            effectiveSettings, contentFactory, infoBanner, processMonitor,
            header, modelCombo, reasoningCombo, sandboxCombo
          )
        }
      }
      .exceptionally { e ->
        val actualException = if (e is java.util.concurrent.CompletionException) e.cause ?: e else e

        when (actualException) {
          is java.util.concurrent.TimeoutException -> {
            DiagnosticsService.append("ERROR: Protocol initialization timed out after 30 seconds")
            log.error("Protocol initialization timed out", actualException)
            SwingUtilities.invokeLater {
              toolWindow.contentManager.removeContent(initializingContent, true)
              val content = contentFactory.createContent(
                JLabel("<html>Protocol initialization timed out.<br>Check Diagnostics panel for details.</html>"),
                "Chat",
                false
              )
              toolWindow.contentManager.addContent(content)
            }
          }
          else -> {
            DiagnosticsService.append("ERROR: Failed to initialize protocol: ${actualException.javaClass.simpleName}: ${actualException.message ?: "unknown error"}")
            log.error("Protocol initialization failed", actualException)
            SwingUtilities.invokeLater {
              toolWindow.contentManager.removeContent(initializingContent, true)
              val content = contentFactory.createContent(
                JLabel("<html>Failed to initialize protocol: ${actualException.message ?: "unknown error"}<br>Check Diagnostics panel for details.</html>"),
                "Chat",
                false
              )
              toolWindow.contentManager.addContent(content)
            }
          }
        }
        null
      }
  }

  private fun buildChatUI(
    project: Project,
    toolWindow: ToolWindow,
    protocol: AppServerProtocol,
    bus: EventBus,
    turns: TurnRegistry,
    cfg: CodexConfigService,
    effectiveSettings: EffectiveSettings,
    contentFactory: ContentFactory,
    infoBanner: InfoBanner,
    processMonitor: ProcessHealthMonitor,
    header: JPanel,
    modelCombo: JComboBox<String>,
    reasoningCombo: JComboBox<String>,
    sandboxCombo: JComboBox<*>
  ) {
    val panel = JPanel(BorderLayout())
    val errorBanner = ErrorBanner().apply { wire(bus) }
    val top = JPanel()
    top.layout = javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS)
    top.add(errorBanner)
    top.add(infoBanner)
    top.add(header)
    panel.add(top, BorderLayout.NORTH)
    val chat = ChatPanel(
      protocol = protocol,
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
        processMonitor.close()
        protocol.stop()
      }
    })
    toolWindow.contentManager.addContent(content)

    if (effectiveSettings.openToolWindowOnStartup) {
      SwingUtilities.invokeLater { toolWindow.show(null) }
    }

    // Row 1: Title + action buttons
    val titleRow = JPanel(BorderLayout()).apply {
      alignmentX = Component.LEFT_ALIGNMENT
    }
    val titleLabel = JLabel("Codex").apply {
      font = CodexTheme.headingFont
    }
    titleRow.add(titleLabel, BorderLayout.WEST)

    val consoleToggle = JToggleButton("Console").apply {
      font = CodexTheme.secondaryFont
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

    val resetApprovalsButton = JButton("Reset approvals").apply {
      font = CodexTheme.secondaryFont
      toolTipText = "Clear remembered approval decisions for this session"
      addActionListener {
        protocol.resetApprovalDecisions()
        infoBanner.show("Approval decisions reset")
      }
      accessibleContext.accessibleName = "Reset remembered approval decisions"
    }

    val titleButtons = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
    titleButtons.add(consoleToggle)
    titleButtons.add(resetApprovalsButton)
    titleRow.add(titleButtons, BorderLayout.EAST)

    // Insert title row at position 0 (before selectors row)
    header.add(titleRow, 0)
    header.add(Box.createVerticalStrut(CodexTheme.sectionGap), 1)

    // Row 4: Info labels (session, tokens, path) — muted
    header.add(Box.createVerticalStrut(CodexTheme.sectionGap))
    val infoRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
      alignmentX = Component.LEFT_ALIGNMENT
    }
    val sessionLabel = JLabel("").apply {
      foreground = CodexTheme.mutedLabelFg
      font = CodexTheme.secondaryFont
    }
    infoRow.add(sessionLabel)
    val usageLabel = JLabel("").apply {
      foreground = CodexTheme.mutedLabelFg
      font = CodexTheme.secondaryFont
    }
    infoRow.add(usageLabel)
    val pathLabel = JLabel("").apply {
      foreground = CodexTheme.mutedLabelFg
      font = CodexTheme.secondaryFont
    }
    infoRow.add(pathLabel)
    header.add(infoRow)

    // Row 5: Checkboxes
    header.add(Box.createVerticalStrut(CodexTheme.sectionGap))
    val checkboxRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
      alignmentX = Component.LEFT_ALIGNMENT
    }
    val showReasoningCheck = JCheckBox("Show reasoning").apply {
      font = CodexTheme.secondaryFont
      isSelected = cfg.showReasoning
      addActionListener { cfg.showReasoning = isSelected }
      accessibleContext.accessibleName = "Show reasoning traces"
      toolTipText = "Show model reasoning traces inline with chat responses"
    }
    checkboxRow.add(showReasoningCheck)
    val autoOpenConsoleCheck = JCheckBox("Auto-open exec console").apply {
      font = CodexTheme.secondaryFont
      isSelected = cfg.autoOpenConsoleOnExec
      addActionListener { cfg.autoOpenConsoleOnExec = isSelected }
      accessibleContext.accessibleName = "Auto-open exec console"
    }
    checkboxRow.add(autoOpenConsoleCheck)
    header.add(checkboxRow)

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
    bus.addListener("token_count") { id, msg ->
      val input = msg.get("input")?.asInt ?: 0
      val output = msg.get("output")?.asInt ?: 0
      val total = msg.get("total")?.asInt ?: (input + output)
      usageLabel.text = "Tokens: in=$input out=$output total=$total"
      TurnMetricsService.onTokenCount(id, total)
    }

    // Conversation path updates (heartbeat response)
    bus.addListener("conversation_path") { _, msg ->
      val path = msg.get("path")?.asString ?: return@addListener
      val convId = msg.get("conversation_id")?.asString
      SwingUtilities.invokeLater {
        pathLabel.text = if (convId.isNullOrBlank()) "Path: $path" else "Path: $path  |  Conv: $convId"
      }
    }

    consolePanel.setCancelHandler { execId ->
      protocol.interrupt()
      infoBanner.show("Cancel request sent")
    }

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


}








