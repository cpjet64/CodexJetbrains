package dev.curt.codexjb.ui

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.core.TelemetryService
import dev.curt.codexjb.core.TurnMetricsService
import dev.curt.codexjb.proto.*
import dev.curt.codexjb.tooling.PatchApplier
import dev.curt.codexjb.ui.settings.CodexSettingsConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.options.ShowSettingsUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.*
import javax.swing.border.EmptyBorder

class ChatPanel(
    private val sender: ProtoSender,
    private val bus: EventBus,
    private val turns: TurnRegistry,
    private val project: Project? = null,
    private val modelProvider: () -> String,
    private val effortProvider: () -> String,
    private val cwdProvider: () -> Path?,
    private val mcpTools: McpToolsModel = McpToolsModel(),
    private val prompts: PromptsModel = PromptsModel(),
    private val sandboxProvider: () -> String = { "workspace-write" }
) : JPanel(BorderLayout()) {

    private val log: LogSink = CodexLogger.forClass(ChatPanel::class.java)
    private val transcript = JPanel()
    private val scroll = JScrollPane(transcript)
    private val input = JTextArea(3, 40)
    private val send = JButton("Send")
    private val clear = JButton("Clear")
    private val spinner = JProgressBar().apply { isIndeterminate = true; isVisible = false }
    private val toolsPanel = JPanel()
    private val toolsList = JList<String>()
    private val refreshToolsButton = JButton("Refresh")
    private val openMcpSettingsButton = JButton("MCP Settings…")
    private val promptsPanel = JPanel()
    private val promptsList = JList<String>()
    private val refreshPromptsButton = JButton("Refresh")
    private val diffViews = mutableMapOf<String, JComponent>()
    private var activeTurnId: String? = null
    private var currentAgentArea: JTextArea? = null
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 1000L // 1 second debounce
    private var initialRequestsSent = false
    private var lastToolsSessionId: String? = null
    private var lastPromptsSessionId: String? = null

    init {
        transcript.layout = BoxLayout(transcript, BoxLayout.Y_AXIS)
        transcript.border = EmptyBorder(8, 8, 8, 8)
        scroll.verticalScrollBar.unitIncrement = 16
        installContextMenu()
        setupToolsPanel()
        add(buildFooter(), BorderLayout.SOUTH)
        registerListeners()
        requestInitialData()
    }

    private fun setupToolsPanel() {
        toolsPanel.layout = BorderLayout()
        toolsPanel.border = EmptyBorder(4, 4, 4, 4)
        
        val toolsHeader = JPanel(BorderLayout())
        val toolsLabel = JLabel("Available Tools:")
        toolsHeader.add(toolsLabel, BorderLayout.WEST)
        refreshToolsButton.toolTipText = "Refresh the list of available MCP tools"
        openMcpSettingsButton.toolTipText = "Open Codex settings to configure MCP servers"
        val toolsActions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            add(openMcpSettingsButton)
            add(refreshToolsButton)
        }
        toolsHeader.add(toolsActions, BorderLayout.EAST)
        toolsPanel.add(toolsHeader, BorderLayout.NORTH)
        
        toolsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        toolsList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is String) {
                    text = value
                    val tool = mcpTools.tools.find { it.name == value }
                    toolTipText = if (tool != null) {
                        "Tool: ${tool.name}\nDescription: ${tool.description}\n\nDouble-click to use this tool"
                    } else {
                        "Tool: $value\n\nDouble-click to use this tool"
                    }
                }
                return renderer
            }
        }
        
        // Add selection listener to save last used tool
        toolsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedTool = toolsList.selectedValue
                if (selectedTool != null) {
                    val config = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
                    config.lastUsedTool = selectedTool
                }
            }
        }
        
        // Add double-click listener to run tool
        toolsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedTool = toolsList.selectedValue
                    if (selectedTool != null && mcpTools.tools.any { it.name == selectedTool }) {
                        runTool(selectedTool)
                    }
                }
            }
        })
        
        toolsPanel.add(JScrollPane(toolsList), BorderLayout.CENTER)
        
        // Add refresh button action listener with debouncing
        refreshToolsButton.addActionListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRefreshTime > refreshDebounceMs) {
                lastRefreshTime = currentTime
                refreshTools()
            }
        }

        openMcpSettingsButton.addActionListener {
            val targetProject = project
            if (targetProject != null) {
                CodexSettingsConfigurable.withProject(targetProject) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(targetProject, SETTINGS_ID)
                }
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "MCP server settings are available when a project is open.",
                    "Codex Settings",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
        
        // Setup prompts panel
        setupPromptsPanel()
        
        // Create a tabbed pane for tools and prompts
        val tabbedPane = JTabbedPane()
        tabbedPane.addTab("Tools", toolsPanel)
        tabbedPane.addTab("Prompts", promptsPanel)
        
        // Add tabbed pane to the right side
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, tabbedPane)
        splitPane.dividerLocation = 400
        add(splitPane, BorderLayout.CENTER)
    }

    private fun setupPromptsPanel() {
        promptsPanel.layout = BorderLayout()
        promptsPanel.border = EmptyBorder(4, 4, 4, 4)
        
        val promptsHeader = JPanel(BorderLayout())
        val promptsLabel = JLabel("Available Prompts:")
        promptsHeader.add(promptsLabel, BorderLayout.WEST)
        refreshPromptsButton.toolTipText = "Refresh the list of available prompts"
        promptsHeader.add(refreshPromptsButton, BorderLayout.EAST)
        promptsPanel.add(promptsHeader, BorderLayout.NORTH)
        
        promptsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        promptsList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is String) {
                    text = value
                    val prompt = prompts.prompts.find { it.name == value }
                    toolTipText = if (prompt != null) {
                        "Prompt: ${prompt.name}\nDescription: ${prompt.description}\n\nDouble-click to insert this prompt into the input box"
                    } else {
                        "Prompt: $value\n\nDouble-click to insert this prompt into the input box"
                    }
                }
                return renderer
            }
        }
        
        // Add selection listener to save last used prompt
        promptsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedPrompt = promptsList.selectedValue
                if (selectedPrompt != null) {
                    val config = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
                    config.lastUsedPrompt = selectedPrompt
                }
            }
        }
        
        // Add double-click listener to insert prompt content
        promptsList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedPrompt = promptsList.selectedValue
                    if (selectedPrompt != null) {
                        val prompt = prompts.prompts.find { it.name == selectedPrompt }
                        if (prompt != null) {
                            input.text = prompt.content
                        }
                    }
                }
            }
        })
        
        promptsPanel.add(JScrollPane(promptsList), BorderLayout.CENTER)
        
        // Add refresh button action listener with debouncing
        refreshPromptsButton.addActionListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRefreshTime > refreshDebounceMs) {
                lastRefreshTime = currentTime
                refreshPrompts()
            }
        }
    }

    private fun buildFooter(): JComponent {
        val panel = JPanel(BorderLayout())
        val controls = JPanel()
        input.lineWrap = true
        input.wrapStyleWord = true
        input.toolTipText = "Ask Codex"
        input.accessibleContext.accessibleName = "Codex input"
        send.addActionListener(this::onSend)
        send.toolTipText = "Send to Codex"
        send.accessibleContext.accessibleName = "Send"
        clear.addActionListener {
            val res = JOptionPane.showConfirmDialog(
                this,
                "Clear chat?",
                "Confirm",
                JOptionPane.YES_NO_OPTION
            )
            if (res == JOptionPane.YES_OPTION) clearTranscript()
        }
        clear.toolTipText = "Clear chat"
        controls.add(send)
        controls.add(spinner)
        controls.add(clear)
        spinner.accessibleContext.accessibleName = "Streaming"
        clear.accessibleContext.accessibleName = "Clear chat"
        panel.add(JScrollPane(input), BorderLayout.CENTER)
        panel.add(controls, BorderLayout.EAST)
        return panel
    }

    private fun registerListeners() {
        registerBusListener("TurnDiff") { id, msg ->
            TurnMetricsService.onDiffStart(id)
            val diff = msg.get("diff")?.asString ?: msg.get("text")?.asString ?: return@registerBusListener
            SwingUtilities.invokeLater { showDiffPanel(id, diff) }
        }
        registerBusListener("AgentMessageDelta") { id, msg ->
            TurnMetricsService.onStreamDelta(id)
            if (id != activeTurnId) return@registerBusListener
            val delta = msg.get("delta")?.asString ?: return@registerBusListener
            SwingUtilities.invokeLater { appendAgentDelta(delta) }
        }
        registerBusListener("AgentMessage") { id, _ ->
            TurnMetricsService.onStreamComplete(id)
            if (id != activeTurnId) return@registerBusListener
            SwingUtilities.invokeLater { sealAgentMessage() }
        }

        registerBusListener(
            "McpListToolsResponse",
            "mcp_list_tools_response",
            "McpToolsList",
            "McpToolsError",
            "mcp_tools_error",
            "McpServerUnavailable",
            "mcp_server_unavailable"
        ) { eventId, msg ->
            mcpTools.onEvent(eventId, msg)
            SwingUtilities.invokeLater { updateToolsList() }
        }

        registerBusListener(
            "ListCustomPromptsResponse",
            "list_custom_prompts_response",
            "PromptsList"
        ) { eventId, msg ->
            prompts.onEvent(eventId, msg)
            SwingUtilities.invokeLater { updatePromptsList() }
        }

        registerBusListener("McpToolCallBegin", "mcp_tool_call_begin", "ToolCallBegin") { _, msg ->
            handleMcpToolCallBegin(msg)
        }
        registerBusListener("McpToolCallEnd", "mcp_tool_call_end", "ToolCallEnd") { _, msg ->
            handleMcpToolCallEnd(msg)
        }

        registerBusListener("PatchApplyBegin") { id, _ -> TurnMetricsService.onApplyBegin(id) }
        registerBusListener("PatchApplyEnd") { id, _ -> TurnMetricsService.onApplyEnd(id) }
        registerBusListener("ExecCommandBegin") { id, _ -> TurnMetricsService.onExecStart(id) }
        registerBusListener("ExecCommandEnd") { id, _ -> TurnMetricsService.onExecEnd(id) }

        registerBusListener("SessionConfigured", "session_configured") { id, _ ->
            onSessionConfigured(id)
        }
    }

    private fun registerBusListener(vararg types: String, handler: (String, JsonObject) -> Unit) {
        types.forEach { type ->
            bus.addListener(type) { id, msg -> handler(id, msg) }
        }
    }

    private fun updateToolsList() {
        toolsList.clearSelection()
        when {
            mcpTools.hasError() -> {
                val errorMsg = mcpTools.getErrorMessage() ?: "MCP server unavailable"
                toolsList.model = DefaultListModel<String>().apply {
                    addElement("⚠ Error: $errorMsg")
                    addElement("")
                    addElement("Click 'Refresh' to retry")
                }
                toolsList.isEnabled = false
            }
            mcpTools.tools.isEmpty() -> {
                toolsList.model = DefaultListModel<String>().apply {
                    addElement("(No tools available)")
                }
                toolsList.isEnabled = false
            }
            else -> {
                val toolNames = mcpTools.tools.map { it.name }
                toolsList.model = DefaultListModel<String>().apply {
                    toolNames.forEach { addElement(it) }
                }
                toolsList.isEnabled = true

                val config = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
                val lastUsedTool = config.lastUsedTool
                if (lastUsedTool != null && toolNames.contains(lastUsedTool)) {
                    toolsList.setSelectedValue(lastUsedTool, true)
                }
            }
        }
    }

    private fun updatePromptsList() {
        promptsList.clearSelection()
        if (prompts.prompts.isEmpty()) {
            promptsList.model = DefaultListModel<String>().apply {
                addElement("(No prompts available)")
            }
            promptsList.isEnabled = false
            return
        }

        val promptNames = prompts.prompts.map { it.name }
        promptsList.model = DefaultListModel<String>().apply {
            promptNames.forEach { addElement(it) }
        }
        promptsList.isEnabled = true

        val config = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
        val lastUsedPrompt = config.lastUsedPrompt
        if (lastUsedPrompt != null && promptNames.contains(lastUsedPrompt)) {
            promptsList.setSelectedValue(lastUsedPrompt, true)
        }
    }

    private fun sendSubmission(type: String, configure: JsonObject.() -> Unit = {}) {
        val body = JsonObject().apply {
            addProperty("type", type)
            configure()
        }
        val envelope = SubmissionEnvelope(Ids.newId(), "Submit", body)
        sender.send(EnvelopeJson.encodeSubmission(envelope))
    }

    private fun refreshTools() {
        SwingUtilities.invokeLater {
            toolsList.model = DefaultListModel<String>().apply { addElement("Loading tools…") }
            toolsList.isEnabled = false
        }
        sendSubmission("ListMcpTools")
        log.info("Refreshing MCP tools...")
    }

    private fun refreshPrompts() {
        SwingUtilities.invokeLater {
            promptsList.model = DefaultListModel<String>().apply { addElement("Loading prompts…") }
            promptsList.isEnabled = false
        }
        sendSubmission("ListCustomPrompts")
        log.info("Refreshing prompts...")
    }

    private fun runTool(toolName: String) {
        if (mcpTools.tools.none { it.name == toolName }) {
            log.warn("Attempted to run unknown MCP tool: $toolName")
            return
        }
        sendSubmission("RunTool") {
            addProperty("tool", toolName)
        }
        log.info("Running tool: $toolName")

        addUserMessage("Running tool: $toolName")
    }

    private fun requestInitialData() {
        if (initialRequestsSent) return
        initialRequestsSent = true
        refreshTools()
        refreshPrompts()
    }

    private fun onSessionConfigured(sessionId: String) {
        if (sessionId.isBlank()) return
        if (lastToolsSessionId != sessionId) {
            lastToolsSessionId = sessionId
            refreshTools()
        }
        if (lastPromptsSessionId != sessionId) {
            lastPromptsSessionId = sessionId
            refreshPrompts()
        }
    }

    private fun handleMcpToolCallBegin(msg: JsonObject) {
        val invocation = msg.getAsJsonObject("invocation")
        val tool = invocation?.get("tool")?.asString
            ?: invocation?.get("name")?.asString
            ?: msg.get("tool")?.asString
            ?: "Unknown"
        val server = invocation?.get("server")?.asString ?: msg.get("server")?.asString
        val label = formatToolLabel(tool, server)
        TelemetryService.recordMcpToolInvocation()
        SwingUtilities.invokeLater {
            addToolCallMessage("🔧 Starting tool: $label", System.currentTimeMillis())
        }
    }

    private fun handleMcpToolCallEnd(msg: JsonObject) {
        val invocation = msg.getAsJsonObject("invocation")
        val tool = invocation?.get("tool")?.asString
            ?: invocation?.get("name")?.asString
            ?: msg.get("tool")?.asString
            ?: "Unknown"
        val server = invocation?.get("server")?.asString ?: msg.get("server")?.asString
        val label = formatToolLabel(tool, server)
        val durationMs = parseDurationMillis(msg.getAsJsonObject("duration"))
            ?: msg.get("duration_ms")?.let { runCatching { it.asLong }.getOrNull() }
        val success = when {
            msg.get("success") != null -> msg.get("success")?.asBoolean ?: false
            else -> parseToolCallSuccess(msg.get("result"))
        }
        if (!success) {
            TelemetryService.recordMcpToolFailure()
        }
        val durationText = durationMs?.let { "$it ms" } ?: "unknown time"
        val status = if (success) "✅" else "❌"
        SwingUtilities.invokeLater {
            addToolCallMessage("$status Tool '$label' completed in $durationText", System.currentTimeMillis())
        }
    }

    private fun parseDurationMillis(durationObj: JsonObject?): Long? {
        if (durationObj == null) return null
        val secs = durationObj.longOrNull("secs") ?: return null
        val nanos = durationObj.longOrNull("nanos") ?: 0L
        return secs * 1000 + nanos / 1_000_000
    }

    private fun parseToolCallSuccess(resultEl: JsonElement?): Boolean {
        val obj = resultEl?.takeIf { it.isJsonObject }?.asJsonObject ?: return true
        if (obj.has("Err") || obj.has("error")) return false
        if (!obj.has("Ok")) return true
        val ok = obj.get("Ok")
        if (!ok.isJsonObject) return true
        val isError = ok.asJsonObject.get("is_error")?.asBoolean ?: false
        return !isError
    }

    private fun JsonObject.longOrNull(name: String): Long? = runCatching { this.get(name)?.asLong }.getOrNull()

    private fun formatToolLabel(tool: String, server: String?): String =
        if (server.isNullOrBlank()) tool else "$server:$tool"

    private fun addToolCallMessage(text: String, timestamp: Long) {
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(timestamp))
        val message = JTextArea("[$timeStr] $text")
        message.isEditable = false
        message.background = Color(240, 248, 255) // Light blue background for tool calls
        message.border = EmptyBorder(4, 8, 4, 8)
        message.lineWrap = true
        message.wrapStyleWord = true
        message.font = message.font.deriveFont(java.awt.Font.BOLD)
        
        transcript.add(message)
        transcript.revalidate()
        scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
    }

    private fun onSend(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        val text = input.text.trim()
        if (text.isEmpty()) return
        submit(text)
    }

    internal fun submit(text: String) {
        submitWithId(text, Ids.newId())
    }

    internal fun submitWithId(text: String, id: String) {
        if (send.isEnabled.not()) return
        val model = modelProvider()
        val effort = effortProvider()
        renderUserBubble(text)
        prepareAgentBubble()
        setSending(true)
        turns.put(Turn(id))
        activeTurnId = id
        TurnMetricsService.onSubmission(id)
        val sandbox = sandboxProvider()
        sender.send(buildSubmission(id, text, model, effort, sandbox))
    }

    private fun buildSubmission(id: String, text: String, model: String, effort: String, sandbox: String): String {
        val body = JsonObject().apply {
            addProperty("type", "UserMessage")
            addProperty("text", text)
            val ctx = OverrideTurnContext(
                cwd = cwdProvider()?.toString(),
                model = model,
                effort = effort,
                sandboxPolicy = sandbox
            )
            add("override", ctx.toJson())
        }
        return EnvelopeJson.encodeSubmission(SubmissionEnvelope(id, "Submit", body))
    }

    private fun addUserMessage(text: String) {
        val label = JLabel(text)
        label.isOpaque = true
        label.background = Color(0xE8F0FE)
        label.border = EmptyBorder(8, 8, 8, 8)
        label.alignmentX = Component.RIGHT_ALIGNMENT
        transcript.add(Box.createVerticalStrut(4))
        transcript.add(label)
        transcript.revalidate()
        scrollToBottom()
    }

    private fun renderUserBubble(text: String) {
        addUserMessage(text)
        input.text = ""
    }

    private fun prepareAgentBubble() {
        val area = JTextArea(1, 40)
        area.lineWrap = true
        area.wrapStyleWord = true
        area.isEditable = false
        area.background = Color(0xF1F3F4)
        area.border = EmptyBorder(8, 8, 8, 8)
        currentAgentArea = area
        transcript.add(Box.createVerticalStrut(4))
        transcript.add(area)
        transcript.revalidate()
        scrollToBottom()
    }

    private fun appendAgentDelta(delta: String) {
        val area = currentAgentArea ?: return
        area.append(delta)
        scrollToBottom()
    }

    private fun sealAgentMessage() {
        setSending(false)
        activeTurnId = null
        currentAgentArea = null
    }

    private fun setSending(active: Boolean) {
        send.isEnabled = !active
        spinner.isVisible = active
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val bar = scroll.verticalScrollBar
            bar.value = bar.maximum
        }
    }

    private fun showDiffPanel(turnId: String, diffText: String) {
        val project = this.project ?: return
        val wrapper = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                EmptyBorder(12, 0, 0, 0),
                EmptyBorder(8, 8, 8, 8)
            )
        }
        diffViews[turnId]?.let { existing ->
            transcript.remove(existing)
        }
        val diffPanel = DiffPanel(diffText, { files ->
            val summary = PatchApplier.apply(project, diffText, files.toSet())
            val result = DiffPanel.ApplyResult(summary.success, summary.failed)
            if (result.failed == 0) {
                SwingUtilities.invokeLater { removeDiffPanel(turnId, wrapper) }
            }
            result
        }) {
            removeDiffPanel(turnId, wrapper)
        }
        wrapper.add(diffPanel, BorderLayout.CENTER)
        diffViews[turnId] = wrapper
        transcript.add(wrapper)
        transcript.revalidate()
        transcript.repaint()
        scrollToBottom()
    }

    private fun removeDiffPanel(turnId: String, component: JComponent) {
        diffViews.remove(turnId)
        transcript.remove(component)
        transcript.revalidate()
        transcript.repaint()
        turns.remove(turnId)
        TurnMetricsService.onDiffEnd(turnId)
    }


    fun clearTranscript() {
        transcript.removeAll()
        transcript.revalidate()
        transcript.repaint()
        diffViews.clear()
    }

    internal fun transcriptCount(): Int = transcript.componentCount
    internal fun isSendEnabled(): Boolean = send.isEnabled
    internal fun isSpinnerVisible(): Boolean = spinner.isVisible

    internal fun collectTranscriptText(): String {
        val sb = StringBuilder()
        for (c in transcript.components) {
            when (c) {
                is JLabel -> sb.appendLine(c.text)
                is JTextArea -> sb.appendLine(c.text)
            }
        }
        return sb.toString().trimEnd()
    }

    private fun installContextMenu() {
        val menu = JPopupMenu()
        val copy = JMenuItem("Copy")
        copy.addActionListener {
            val sel = transcript.getComponentAt(transcript.mousePosition ?: return@addActionListener)
            val text = when (sel) {
                is JLabel -> sel.text
                is JTextArea -> sel.text
                else -> collectTranscriptText()
            }
            val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clip.setContents(java.awt.datatransfer.StringSelection(text), null)
        }
        menu.add(copy)
        transcript.componentPopupMenu = menu
    }

    companion object {
        private const val SETTINGS_ID = "dev.curt.codexjb.settings"
    }
}
