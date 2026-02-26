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
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


private object ChatPalette {
    private fun bubbleColor(name: String, light: Int, dark: Int): Color =
        JBColor.namedColor(name, JBColor(Color(light), Color(dark)))

    val panelBackground: Color
        get() = UIUtil.getPanelBackground()

    val bubbleForeground: Color
        get() = UIUtil.getLabelForeground()

    val toolCallBackground: Color = bubbleColor("Codex.Chat.ToolCall.background", 0xF0F6FF, 0x1F2A3C)
    val userBubbleBackground: Color = bubbleColor("Codex.Chat.UserBubble.background", 0xDCE8FC, 0x263A52)
    val agentBubbleBackground: Color = bubbleColor("Codex.Chat.AgentBubble.background", 0xEEF0F2, 0x34393F)
}

class ChatPanel(
    private val protocol: AppServerProtocol,
    private val bus: EventBus,
    private val turns: TurnRegistry,
    private val project: Project? = null,
    private val modelProvider: () -> String,
    private val reasoningProvider: () -> String,
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
    private var currentReasoningArea: JTextArea? = null
    private var currentReasoningPanel: JPanel? = null
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 1000L // 1 second debounce
    private var initialRequestsSent = false
    private var lastToolsSessionId: String? = null
    private var lastPromptsSessionId: String? = null

    init {
        transcript.layout = BoxLayout(transcript, BoxLayout.Y_AXIS)
        transcript.border = EmptyBorder(12, 12, 12, 12)
        transcript.background = ChatPalette.panelBackground
        scroll.viewport.background = ChatPalette.panelBackground
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
        val footer = JPanel()
        footer.layout = BoxLayout(footer, BoxLayout.Y_AXIS)

        // Row 1: Thin full-width streaming indicator
        spinner.preferredSize = Dimension(Int.MAX_VALUE, 2)
        spinner.maximumSize = Dimension(Int.MAX_VALUE, 2)
        spinner.alignmentX = Component.LEFT_ALIGNMENT
        footer.add(spinner)

        // Row 2: Input area with watermark + buttons
        val inputRow = JPanel(BorderLayout())
        inputRow.alignmentX = Component.LEFT_ALIGNMENT

        input.lineWrap = true
        input.wrapStyleWord = true
        input.toolTipText = "Ask Codex"
        input.accessibleContext.accessibleName = "Codex input"
        input.font = CodexTheme.bodyFont

        // Watermark overlay
        val watermark = JLabel("Type a message...")
        watermark.foreground = CodexTheme.mutedLabelFg
        watermark.font = CodexTheme.bodyFont
        watermark.border = EmptyBorder(4, 6, 0, 0)
        watermark.isVisible = input.text.isEmpty()

        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { watermark.isVisible = input.text.isEmpty() }
            override fun removeUpdate(e: DocumentEvent) { watermark.isVisible = input.text.isEmpty() }
            override fun changedUpdate(e: DocumentEvent) { watermark.isVisible = input.text.isEmpty() }
        })

        // Layer watermark over input using OverlayLayout
        val inputContainer = JPanel()
        inputContainer.layout = OverlayLayout(inputContainer)
        val watermarkWrapper = JPanel(BorderLayout())
        watermarkWrapper.isOpaque = false
        watermarkWrapper.add(watermark, BorderLayout.NORTH)
        inputContainer.add(watermarkWrapper)
        val inputScroll = JScrollPane(input)
        inputScroll.border = BorderFactory.createCompoundBorder(
            LineBorder(CodexTheme.secondaryLabelFg, 1, true),
            EmptyBorder(2, 4, 2, 4)
        )
        inputContainer.add(inputScroll)

        // Key bindings: Enter to send, Shift+Enter for newline
        run {
            val im = input.getInputMap(JComponent.WHEN_FOCUSED)
            val am = input.actionMap
            im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "codex-send")
            im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.SHIFT_DOWN_MASK), "insert-break")
            am.put("codex-send", object : javax.swing.AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    onSend(e)
                }
            })
        }

        send.addActionListener(this::onSend)
        send.toolTipText = "Send to Codex"
        send.accessibleContext.accessibleName = "Send"
        send.font = CodexTheme.bodyFont

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
        clear.font = CodexTheme.secondaryFont

        spinner.accessibleContext.accessibleName = "Streaming"
        clear.accessibleContext.accessibleName = "Clear chat"

        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.Y_AXIS)
        buttonPanel.border = EmptyBorder(0, 4, 0, 0)
        send.alignmentX = Component.CENTER_ALIGNMENT
        clear.alignmentX = Component.CENTER_ALIGNMENT
        buttonPanel.add(send)
        buttonPanel.add(Box.createVerticalStrut(4))
        buttonPanel.add(clear)

        inputRow.add(inputContainer, BorderLayout.CENTER)
        inputRow.add(buttonPanel, BorderLayout.EAST)

        footer.add(inputRow)
        return footer
    }

    private fun registerListeners() {
        // Listen for task_started to sync turn ID from server
        registerBusListener("task_started") { id, _ ->
            log.info("Task started with server turn ID: $id")
            activeTurnId = id
            SwingUtilities.invokeLater {
                // Only prepare agent bubble if not already created
                if (currentAgentArea == null) {
                    prepareAgentBubble()
                }
            }
        }

        registerBusListener("TurnDiff", "turn_diff") { id, msg ->
            TurnMetricsService.onDiffStart(id)
            val diff = msg.get("diff")?.asString ?: msg.get("text")?.asString ?: return@registerBusListener
            SwingUtilities.invokeLater { showDiffPanel(id, diff) }
        }
        registerBusListener("AgentMessageDelta", "agent_message_delta") { id, msg ->
            TurnMetricsService.onStreamDelta(id)
            if (id != activeTurnId) return@registerBusListener
            val delta = msg.get("delta")?.asString ?: return@registerBusListener
            SwingUtilities.invokeLater {
                // Create agent bubble after reasoning (if we haven't created it yet)
                if (currentAgentArea == null) {
                    prepareAgentBubble()
                }
                appendAgentDelta(delta)
            }
        }
        registerBusListener("AgentMessage", "agent_message") { id, _ ->
            TurnMetricsService.onStreamComplete(id)
            if (id != activeTurnId) return@registerBusListener
            SwingUtilities.invokeLater { sealAgentMessage() }
        }

        // Listen for task_complete to finalize turn
        registerBusListener("task_complete") { id, _ ->
            log.info("Task completed for turn ID: $id")
            SwingUtilities.invokeLater { setSending(false) }
        }

        // Listen for reasoning events
        registerBusListener("agent_reasoning_section_break") { id, _ ->
            if (id != activeTurnId) return@registerBusListener
            val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
            if (!cfg.showReasoning) return@registerBusListener
            SwingUtilities.invokeLater { appendReasoningDelta("\n\n") }
        }

        registerBusListener("agent_reasoning_delta") { id, msg ->
            if (id != activeTurnId) return@registerBusListener
            val delta = msg.get("delta")?.asString ?: return@registerBusListener
            SwingUtilities.invokeLater {
                // Always create reasoning bubble first (it will be collapsible)
                if (currentReasoningArea == null) {
                    prepareReasoningBubble()
                }

                val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
                if (cfg.showReasoning) {
                    appendReasoningDelta(delta)
                }
            }
        }

        registerBusListener("agent_reasoning") { id, _ ->
            if (id != activeTurnId) return@registerBusListener
            val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
            if (!cfg.showReasoning) return@registerBusListener
            // Final reasoning text - already assembled via deltas
            log.info("Reasoning complete for turn $id")
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
        registerBusListener("ExecCommandBegin", "exec_command_begin") { id, _ -> TurnMetricsService.onExecStart(id) }
        registerBusListener("ExecCommandEnd", "exec_command_end") { id, _ -> TurnMetricsService.onExecEnd(id) }

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

    private fun sendOp(op: String) {
        protocol.sendLegacyOp(op)
            .exceptionally { error ->
                log.warn("Operation '$op' failed: ${error.message}")
                Unit
            }
    }

    private fun refreshTools() {
        SwingUtilities.invokeLater {
            toolsList.model = DefaultListModel<String>().apply { addElement("Loading tools…") }
            toolsList.isEnabled = false
        }
        sendOp("list_mcp_tools")
        log.info("Refreshing MCP tools...")
    }

    private fun refreshPrompts() {
        SwingUtilities.invokeLater {
            promptsList.model = DefaultListModel<String>().apply { addElement("Loading prompts…") }
            promptsList.isEnabled = false
        }
        sendOp("list_custom_prompts")
        log.info("Refreshing prompts...")
    }

    private fun runTool(toolName: String) {
        if (mcpTools.tools.none { it.name == toolName }) {
            log.warn("Attempted to run unknown MCP tool: $toolName")
            return
        }
        protocol.runMcpTool(toolName)
            .exceptionally { error ->
                log.warn("Failed to run MCP tool '$toolName': ${error.message}")
                Unit
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
        message.background = ChatPalette.toolCallBackground
        message.foreground = ChatPalette.bubbleForeground
        message.border = EmptyBorder(6, 10, 6, 10)
        message.lineWrap = true
        message.wrapStyleWord = true
        message.font = CodexTheme.bodyFont.deriveFont(Font.BOLD)

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
        renderUserBubble(text)
        // Don't prepare agent bubble yet - wait for reasoning to come first
        setSending(true)
        turns.put(Turn(id))
        activeTurnId = id
        TurnMetricsService.onSubmission(id)

        // Send message via JSON-RPC protocol
        protocol.sendMessage(text)
            .thenAccept {
                log.info("Message sent successfully")
            }
            .exceptionally { error ->
                log.error("Failed to send message: ${error.message}")
                SwingUtilities.invokeLater {
                    setSending(false)
                    JOptionPane.showMessageDialog(
                        this,
                        "Failed to send message: ${error.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
                null
            }
    }

    private fun sendUserInput(text: String) {
        protocol.sendMessage(text)
            .thenAccept {
                log.info("Message sent successfully")
            }
            .exceptionally { error ->
                log.error("Failed to send message: ${error.message}")
                null
            }
    }

    private fun addUserMessage(text: String) {
        // Create bubble panel with rounded corners
        val bubble = RoundedPanel(ChatPalette.userBubbleBackground, CodexTheme.bubbleRadius)
        bubble.layout = BorderLayout()
        bubble.border = EmptyBorder(
            CodexTheme.bubblePadding.top, CodexTheme.bubblePadding.left,
            CodexTheme.bubblePadding.bottom, CodexTheme.bubblePadding.right
        )

        val area = JTextArea(text)
        area.lineWrap = true
        area.wrapStyleWord = true
        area.isEditable = false
        area.isOpaque = false
        area.foreground = ChatPalette.bubbleForeground
        area.border = null
        area.maximumSize = Dimension(450, Int.MAX_VALUE)
        bubble.add(area, BorderLayout.CENTER)

        // Right-align user messages
        val wrapper = JPanel(BorderLayout())
        wrapper.isOpaque = false
        wrapper.add(bubble, BorderLayout.EAST)

        transcript.add(Box.createVerticalStrut(CodexTheme.bubbleGap))
        transcript.add(wrapper)
        transcript.revalidate()
        scrollToBottom()
    }

    private fun renderUserBubble(text: String) {
        addUserMessage(text)
        input.text = ""
    }

    private fun prepareAgentBubble() {
        // Create bubble panel with rounded corners
        val bubble = RoundedPanel(ChatPalette.agentBubbleBackground, CodexTheme.bubbleRadius)
        bubble.layout = BorderLayout()
        bubble.border = EmptyBorder(
            CodexTheme.bubblePadding.top, CodexTheme.bubblePadding.left,
            CodexTheme.bubblePadding.bottom, CodexTheme.bubblePadding.right
        )

        val area = JTextArea(1, 50)
        area.lineWrap = true
        area.wrapStyleWord = true
        area.isEditable = false
        area.isOpaque = false
        area.foreground = ChatPalette.bubbleForeground
        area.border = null
        bubble.add(area, BorderLayout.CENTER)

        // Left-align agent messages
        val wrapper = JPanel(BorderLayout())
        wrapper.isOpaque = false
        wrapper.add(bubble, BorderLayout.WEST)

        currentAgentArea = area
        transcript.add(Box.createVerticalStrut(CodexTheme.bubbleGap))
        transcript.add(wrapper)
        transcript.revalidate()
        scrollToBottom()
    }

    private fun appendAgentDelta(delta: String) {
        val area = currentAgentArea ?: return
        area.append(delta)
        scrollToBottom()
    }

    private fun prepareReasoningBubble() {
        val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)

        // Create collapsible panel with reasoning header using rounded corners
        val panel = RoundedPanel(ChatPalette.toolCallBackground, CodexTheme.bubbleRadius)
        panel.layout = BorderLayout()
        panel.border = EmptyBorder(
            CodexTheme.bubblePadding.top, CodexTheme.bubblePadding.left,
            CodexTheme.bubblePadding.bottom, CodexTheme.bubblePadding.right
        )

        // Header with toggle button
        val header = JPanel(BorderLayout())
        header.isOpaque = false

        // Start collapsed or expanded based on user preference
        val startExpanded = cfg.showReasoning
        val toggleButton = JButton(if (startExpanded) "▼ 🧠 Reasoning" else "▶ 🧠 Reasoning")
        toggleButton.isBorderPainted = false
        toggleButton.isFocusPainted = false
        toggleButton.isContentAreaFilled = false
        toggleButton.foreground = ChatPalette.bubbleForeground
        toggleButton.font = CodexTheme.secondaryFont
        header.add(toggleButton, BorderLayout.WEST)

        // Reasoning text area
        val area = JTextArea(1, 40)
        area.lineWrap = true
        area.wrapStyleWord = true
        area.isEditable = false
        area.isOpaque = false
        area.foreground = ChatPalette.bubbleForeground
        area.border = EmptyBorder(8, 8, 8, 8)

        val scrollPane = JScrollPane(area)
        scrollPane.border = null
        scrollPane.isOpaque = false
        scrollPane.viewport.isOpaque = false
        scrollPane.isVisible = startExpanded // Start collapsed if showReasoning is false

        panel.add(header, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Toggle collapse/expand
        toggleButton.addActionListener {
            val isVisible = scrollPane.isVisible
            scrollPane.isVisible = !isVisible
            toggleButton.text = if (isVisible) "▶ 🧠 Reasoning" else "▼ 🧠 Reasoning"
            panel.revalidate()
            scrollToBottom()
        }

        currentReasoningArea = area
        currentReasoningPanel = panel
        transcript.add(Box.createVerticalStrut(4))
        transcript.add(panel)
        transcript.revalidate()
        scrollToBottom()
    }

    private fun appendReasoningDelta(delta: String) {
        val area = currentReasoningArea ?: return
        area.append(delta)
        scrollToBottom()
    }

    private fun sealAgentMessage() {
        setSending(false)
        activeTurnId = null
        currentAgentArea = null
        currentReasoningArea = null
        currentReasoningPanel = null
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

/**
 * Custom JPanel that draws rounded corners.
 */
class RoundedPanel(private val bgColor: Color, private val radius: Int) : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bgColor
        g2.fillRoundRect(0, 0, width - 1, height - 1, radius, radius)
        g2.dispose()
    }
}
