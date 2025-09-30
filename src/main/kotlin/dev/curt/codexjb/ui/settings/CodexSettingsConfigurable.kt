
package dev.curt.codexjb.ui.settings

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import dev.curt.codexjb.proto.ApprovalMode
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.CodexDefaults
import dev.curt.codexjb.core.CodexProjectSettingsService
import dev.curt.codexjb.core.DefaultCodexCliExecutor
import dev.curt.codexjb.core.run
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class CodexSettingsConfigurable : SearchableConfigurable {
    private val appConfig = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
    private val project: Project? = CURRENT_PROJECT.getAndSet(null)
        ?: ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed && !it.isDefault }
    private val projectSettings = project?.getService(CodexProjectSettingsService::class.java)

    private val cliPathField = TextFieldWithBrowseButton()
    private val useWslCheckbox = JCheckBox("Prefer WSL when discovering Codex CLI")
    private val openStartupCheckbox = JCheckBox("Open Codex tool window on startup")

    private val modelCombo = JComboBox(CodexDefaults.MODELS.toTypedArray())
    private val effortCombo = JComboBox(CodexDefaults.EFFORTS.toTypedArray())
    private val approvalCombo = JComboBox(ApprovalMode.values())
    private val sandboxCombo = JComboBox(CodexDefaults.SANDBOX_POLICIES.toTypedArray())

    private val projectCliField = TextFieldWithBrowseButton()
    private val projectUseWslBox = OverrideTriState()
    private val projectOpenStartupBox = OverrideTriState()
    private val projectModelCombo = OverrideCombo(CodexDefaults.MODELS)
    private val projectEffortCombo = OverrideCombo(CodexDefaults.EFFORTS)
    private val projectApprovalCombo = OverrideCombo(ApprovalMode.values().map { it.name })
    private val projectSandboxCombo = OverrideCombo(CodexDefaults.SANDBOX_POLICIES.map { it.id })

    private val testButton = JButton("Test connection")
    private val exportButton = JButton("Exportâ€¦")
    private val importButton = JButton("Importâ€¦")
    private val resetButton = JButton("Reset to defaults")
    private val statusLabel = JBLabel("")

    private var panel: JPanel? = null

    override fun getId(): String = SETTINGS_ID

    override fun getDisplayName(): String = "Codex (Unofficial)"

    override fun createComponent(): JComponent {
      if (panel == null) {
        panel = buildUI()
        reset()
      }
      return panel as JComponent
    }

    override fun isModified(): Boolean {
      val currentCli = cliPathField.text.trim().takeIf { it.isNotEmpty() }
      if (currentCli != appConfig.cliPath?.toString()) return true
      if (useWslCheckbox.isSelected != appConfig.useWsl) return true
      if (openStartupCheckbox.isSelected != appConfig.openToolWindowOnStartup) return true
      if ((modelCombo.selectedItem as String?) != appConfig.defaultModel) return true
      if ((effortCombo.selectedItem as String?) != appConfig.defaultEffort) return true
      if (((approvalCombo.selectedItem as ApprovalMode).name) != appConfig.defaultApprovalMode) return true
      if ((sandboxCombo.selectedItem as CodexDefaults.SandboxOption).id != appConfig.defaultSandboxPolicy) return true

      if (projectSettings != null) {
        val cliOverride = projectCliField.text.trim().takeIf { it.isNotEmpty() }
        if (cliOverride != projectSettings.cliPathOverride) return true
        if (projectUseWslBox.value != projectSettings.useWslOverride) return true
        if (projectOpenStartupBox.value != projectSettings.openToolWindowOnStartup) return true
        if (projectModelCombo.value != projectSettings.defaultModel) return true
        if (projectEffortCombo.value != projectSettings.defaultEffort) return true
        if (projectApprovalCombo.value != projectSettings.defaultApprovalMode) return true
        if (projectSandboxCombo.value != projectSettings.defaultSandboxPolicy) return true
      }
      return false
    }

    override fun apply() {
      val cliPath = cliPathField.text.trim().takeIf { it.isNotEmpty() }
      cliPath?.let { validateCliPath(Path.of(it)) }

      appConfig.cliPath = cliPath?.let(Path::of)
      appConfig.useWsl = useWslCheckbox.isSelected
      appConfig.openToolWindowOnStartup = openStartupCheckbox.isSelected
      appConfig.defaultModel = modelCombo.selectedItem as String
      appConfig.defaultEffort = effortCombo.selectedItem as String
      appConfig.defaultApprovalMode = (approvalCombo.selectedItem as ApprovalMode).name
      appConfig.defaultSandboxPolicy = (sandboxCombo.selectedItem as CodexDefaults.SandboxOption).id

      projectSettings?.let { settings ->
        val overrideCli = projectCliField.text.trim().takeIf { it.isNotEmpty() }
        overrideCli?.let { validateCliPath(Path.of(it)) }
        settings.cliPathOverride = overrideCli
        settings.useWslOverride = projectUseWslBox.value
        settings.openToolWindowOnStartup = projectOpenStartupBox.value
        settings.defaultModel = projectModelCombo.value
        settings.defaultEffort = projectEffortCombo.value
        settings.defaultApprovalMode = projectApprovalCombo.value
        settings.defaultSandboxPolicy = projectSandboxCombo.value
      }
    }

    override fun reset() {
      cliPathField.text = appConfig.cliPath?.toString() ?: ""
      useWslCheckbox.isSelected = appConfig.useWsl
      openStartupCheckbox.isSelected = appConfig.openToolWindowOnStartup
      modelCombo.selectedItem = appConfig.defaultModel ?: CodexDefaults.MODELS.first()
      effortCombo.selectedItem = appConfig.defaultEffort ?: CodexDefaults.EFFORTS.first()
      approvalCombo.selectedItem = ApprovalMode.values().firstOrNull { it.name == appConfig.defaultApprovalMode }
        ?: ApprovalMode.CHAT
      sandboxCombo.selectedItem = CodexDefaults.SANDBOX_POLICIES.firstOrNull { it.id == appConfig.defaultSandboxPolicy }
        ?: CodexDefaults.SANDBOX_POLICIES.first()

      projectSettings?.let { settings ->
        projectCliField.text = settings.cliPathOverride ?: ""
        projectUseWslBox.value = settings.useWslOverride
        projectOpenStartupBox.value = settings.openToolWindowOnStartup
        projectModelCombo.value = settings.defaultModel
        projectEffortCombo.value = settings.defaultEffort
        projectApprovalCombo.value = settings.defaultApprovalMode
        projectSandboxCombo.value = settings.defaultSandboxPolicy
      }
    }

    private fun buildUI(): JPanel {
      cliPathField.addBrowseFolderListener(
        TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleLocalFileDescriptor())
      )
      projectCliField.addBrowseFolderListener(
        TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleLocalFileDescriptor())
      )

      sandboxCombo.renderer = sandboxRenderer()
      projectSandboxCombo.component.renderer = sandboxRenderer()

      val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(testButton)
        add(exportButton)
        add(importButton)
        add(resetButton)
      }

      testButton.addActionListener { runTestConnection() }
      exportButton.addActionListener { exportSettings() }
      importButton.addActionListener { importSettings() }
      resetButton.addActionListener { resetToDefaults() }

      val globalPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("CLI path:"), cliPathField)
        .addComponent(useWslCheckbox)
        .addComponent(openStartupCheckbox)
        .addSeparator()
        .addLabeledComponent(JBLabel("Default model:"), modelCombo)
        .addLabeledComponent(JBLabel("Default effort:"), effortCombo)
        .addLabeledComponent(JBLabel("Default approval mode:"), approvalCombo)
        .addLabeledComponent(JBLabel("Default sandbox policy:"), sandboxCombo)
        .addComponent(buttonRow)
        .addComponent(statusLabel)
        .panel

      val root = JPanel(BorderLayout())
      root.add(globalPanel, BorderLayout.NORTH)
      project?.let { root.add(buildProjectPanel(), BorderLayout.CENTER) }
      return root
    }

    private fun buildProjectPanel(): JComponent {
      val name = project?.name ?: "current project"
      val builder = FormBuilder.createFormBuilder()
        .addSeparator()
        .addComponent(JBLabel("Project overrides (" + name + ")"))
        .addLabeledComponent(JBLabel("CLI path override:"), projectCliField)
        .addLabeledComponent(JBLabel("WSL preference:"), projectUseWslBox.component)
        .addLabeledComponent(JBLabel("Tool window on startup:"), projectOpenStartupBox.component)
        .addLabeledComponent(JBLabel("Model override:"), projectModelCombo.component)
        .addLabeledComponent(JBLabel("Effort override:"), projectEffortCombo.component)
        .addLabeledComponent(JBLabel("Approval override:"), projectApprovalCombo.component)
        .addLabeledComponent(JBLabel("Sandbox override:"), projectSandboxCombo.component)
      return builder.panel
    }

    private fun runTestConnection() {
      val pathText = cliPathField.text.trim().takeIf { it.isNotEmpty() }
      if (pathText == null) {
        Messages.showWarningDialog("Set the CLI path before testing the connection.", "Codex")
        return
      }
      val path = Path.of(pathText)
      try {
        validateCliPath(path)
      } catch (ex: ConfigurationException) {
        val errorDetail = ex.localizedMessage
          ?.takeIf { it.isNotBlank() }
          ?: ex.javaClass.simpleName
        Messages.showErrorDialog(errorDetail, "Codex")
        return
      }

      testButton.isEnabled = false
      statusLabel.text = "Testing connectionâ€¦"
      ApplicationManager.getApplication().executeOnPooledThread {
        val executor = DefaultCodexCliExecutor()
        val result = executor.run(path, "whoami", workingDirectory = null)
        ApplicationManager.getApplication().invokeLater {
          testButton.isEnabled = true
          statusLabel.text = if (result.isSuccess) {
            "CLI responded: " + result.stdout.trim()
          } else {
            "CLI error (" + result.exitCode + "): " + result.stderr.trim()
          }
        }
      }
    }

    private fun exportSettings() {
      val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
      val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
      val file = Path.of(virtualFile.path)
      val gson = GsonBuilder().setPrettyPrinting().create()
      val root = JsonObject().apply {
        addProperty("cli_path", cliPathField.text.trim().takeIf { it.isNotEmpty() })
        addProperty("use_wsl", useWslCheckbox.isSelected)
        addProperty("open_tool_window_on_startup", openStartupCheckbox.isSelected)
        addProperty("default_model", modelCombo.selectedItem as String)
        addProperty("default_effort", effortCombo.selectedItem as String)
        addProperty("default_approval_mode", (approvalCombo.selectedItem as ApprovalMode).name)
        addProperty("default_sandbox_policy", (sandboxCombo.selectedItem as CodexDefaults.SandboxOption).id)
        projectSettings?.let { _ ->
          val projectJson = JsonObject().apply {
            addProperty("cli_path_override", projectCliField.text.trim().takeIf { it.isNotEmpty() })
            addProperty("use_wsl_override", projectUseWslBox.value)
            addProperty("open_tool_window_on_startup", projectOpenStartupBox.value)
            addProperty("default_model", projectModelCombo.value)
            addProperty("default_effort", projectEffortCombo.value)
            addProperty("default_approval_mode", projectApprovalCombo.value)
            addProperty("default_sandbox_policy", projectSandboxCombo.value)
          }
          add("project_override", projectJson)
        }
      }
      Files.createDirectories(file.parent)
      Files.writeString(file, gson.toJson(root))
      statusLabel.text = "Settings exported to " + file.fileName
    }

    private fun importSettings() {
      val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
      val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
      val file = Path.of(virtualFile.path)
      try {
        val gson = GsonBuilder().create()
        val json = gson.fromJson(Files.readString(file), JsonObject::class.java)
        cliPathField.text = json.get("cli_path")?.asString ?: ""
        useWslCheckbox.isSelected = json.get("use_wsl")?.asBoolean ?: useWslCheckbox.isSelected
        openStartupCheckbox.isSelected = json.get("open_tool_window_on_startup")?.asBoolean
          ?: openStartupCheckbox.isSelected
        json.get("default_model")?.asString?.let { modelCombo.selectedItem = it }
        json.get("default_effort")?.asString?.let { effortCombo.selectedItem = it }
        json.get("default_approval_mode")?.asString?.let { value ->
          approvalCombo.selectedItem = ApprovalMode.values().firstOrNull { it.name == value } ?: approvalCombo.selectedItem
        }
        json.get("default_sandbox_policy")?.asString?.let { value ->
          sandboxCombo.selectedItem = CodexDefaults.SANDBOX_POLICIES.firstOrNull { it.id == value }
            ?: sandboxCombo.selectedItem
        }
        json.getAsJsonObject("project_override")?.let { proj ->
          projectCliField.text = proj.get("cli_path_override")?.asString ?: ""
          projectUseWslBox.value = proj.get("use_wsl_override")?.asBoolean
          projectOpenStartupBox.value = proj.get("open_tool_window_on_startup")?.asBoolean
          projectModelCombo.value = proj.get("default_model")?.asString
          projectEffortCombo.value = proj.get("default_effort")?.asString
          projectApprovalCombo.value = proj.get("default_approval_mode")?.asString
          projectSandboxCombo.value = proj.get("default_sandbox_policy")?.asString
        }
        statusLabel.text = "Settings imported from " + file.fileName
      } catch (ex: Exception) {
      val detail = ex.localizedMessage
        ?.takeIf { it.isNotBlank() }
        ?: ex.javaClass.simpleName
      Messages.showErrorDialog(project, "Failed to import settings: " + detail, "Codex")
      }
    }

    private fun resetToDefaults() {
      cliPathField.text = ""
      useWslCheckbox.isSelected = false
      openStartupCheckbox.isSelected = false
      modelCombo.selectedIndex = 0
      effortCombo.selectedIndex = 0
      approvalCombo.selectedItem = ApprovalMode.CHAT
      sandboxCombo.selectedIndex = 0
      projectCliField.text = ""
      projectUseWslBox.value = null
      projectOpenStartupBox.value = null
      projectModelCombo.value = null
      projectEffortCombo.value = null
      projectApprovalCombo.value = null
      projectSandboxCombo.value = null
      statusLabel.text = ""
    }

    private fun validateCliPath(path: Path) {
      if (!Files.exists(path)) {
        throw ConfigurationException("CLI path does not exist: " + path)
      }
      if (!Files.isRegularFile(path)) {
        throw ConfigurationException("CLI path is not a file: " + path)
      }
      if (!Files.isExecutable(path)) {
        throw ConfigurationException("CLI path is not executable: " + path)
      }
    }

    private fun sandboxRenderer(): javax.swing.DefaultListCellRenderer = object : javax.swing.DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: javax.swing.JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
      ): java.awt.Component {
        val renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is CodexDefaults.SandboxOption) {
          text = value.label
        }
        return renderer
      }
    }

    override fun disposeUIResources() {
      panel = null
    }

    companion object {
      private const val SETTINGS_ID = "dev.curt.codexjb.settings"
      private val CURRENT_PROJECT = AtomicReference<Project?>()

      fun withProject(project: Project?, action: () -> Unit) {
        try {
          CURRENT_PROJECT.set(project)
          action()
        } finally {
          CURRENT_PROJECT.set(null)
        }
      }
    }

    private class OverrideCombo(options: List<String>) {
      private val model = javax.swing.DefaultComboBoxModel<String>()
      val component: JComboBox<String> = JComboBox(model)

      init {
        model.addElement(USE_GLOBAL)
        options.forEach(model::addElement)
      }

      var value: String?
        get() = component.selectedItem?.toString()?.takeIf { it != USE_GLOBAL }
        set(value) {
          component.selectedItem = value ?: USE_GLOBAL
        }

      companion object {
        private const val USE_GLOBAL = "Use global"
      }
    }

    private class OverrideTriState {
      private val model = javax.swing.DefaultComboBoxModel(arrayOf(USE_GLOBAL, ENABLED, DISABLED))
      val component = JComboBox(model)

      var value: Boolean?
        get() = when (component.selectedItem) {
          ENABLED -> true
          DISABLED -> false
          else -> null
        }
        set(value) {
          component.selectedItem = when (value) {
            true -> ENABLED
            false -> DISABLED
            else -> USE_GLOBAL
          }
        }

      companion object {
        private const val USE_GLOBAL = "Use global"
        private const val ENABLED = "Enabled"
        private const val DISABLED = "Disabled"
      }
    }
}
