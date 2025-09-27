package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.proto.*
import dev.curt.codexjb.tooling.PatchApplier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
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
    private val prompts: PromptsModel = PromptsModel()
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
    private val promptsPanel = JPanel()
    private val promptsList = JList<String>()
    private val refreshPromptsButton = JButton("Refresh")
    private val diffViews = mutableMapOf<String, JComponent>()
    private var activeTurnId: String? = null
    private var currentAgentArea: JTextArea? = null
    private var lastRefreshTime = 0L
    private val refreshDebounceMs = 1000L // 1 second debounce

    init {
        transcript.layout = BoxLayout(transcript, BoxLayout.Y_AXIS)
        transcript.border = EmptyBorder(8, 8, 8, 8)
        scroll.verticalScrollBar.unitIncrement = 16
        installContextMenu()
        setupToolsPanel()
        add(buildFooter(), BorderLayout.SOUTH)
        registerListeners()
    }

    private fun setupToolsPanel() {
        toolsPanel.layout = BorderLayout()
        toolsPanel.border = EmptyBorder(4, 4, 4, 4)
        
        val toolsHeader = JPanel(BorderLayout())
        val toolsLabel = JLabel("Available Tools:")
        toolsHeader.add(toolsLabel, BorderLayout.WEST)
        refreshToolsButton.toolTipText = "Refresh the list of available MCP tools"
        toolsHeader.add(refreshToolsButton, BorderLayout.EAST)
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
                    if (selectedTool != null && !selectedTool.startsWith("âš ï¸")) {
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
        bus.addListener("TurnDiff") { id, msg ->
            val diff = msg.get("diff")?.asString ?: msg.get("text")?.asString ?: return@addListener
            SwingUtilities.invokeLater { showDiffPanel(id, diff) }
        }
        bus.addListener("AgentMessageDelta") { id, msg ->
            if (id != activeTurnId) return@addListener
            val delta = msg.get("delta")?.asString ?: return@addListener
            SwingUtilities.invokeLater { appendAgentDelta(delta) }
        }
        bus.addListener("AgentMessage") { id, _ ->
            if (id != activeTurnId) return@addListener
            SwingUtilities.invokeLater { sealAgentMessage() }
        }
        bus.addListener("McpToolsList") { id, msg ->
            mcpTools.onEvent(id, msg)
            SwingUtilities.invokeLater { updateToolsList() }
        }
        bus.addListener("PromptsList") { id, msg ->
            prompts.onEvent(id, msg)
            SwingUtilities.invokeLater { updatePromptsList() }
        }
        bus.addListener("McpToolsError") { id, msg ->
            mcpTools.onEvent(id, msg)
            SwingUtilities.invokeLater { updateToolsList() }
        }
        bus.addListener("McpServerUnavailable") { id, msg ->
            mcpTools.onEvent(id, msg)
            SwingUtilities.invokeLater { updateToolsList() }
        }
        bus.addListener("ToolCallBegin") { id, msg ->
            val toolName = msg.get("tool")?.asString ?: "Unknown"
            val timestamp = System.currentTimeMillis()
            SwingUtilities.invokeLater { 
                addToolCallMessage("ðŸ”§ Starting tool: $toolName", timestamp)
            }
        }
        bus.addListener("ToolCallEnd") { id, msg ->
            val toolName = msg.get("tool")?.asString ?: "Unknown"
            val duration = msg.get("duration_ms")?.asLong ?: 0L
            val success = msg.get("success")?.asBoolean ?: false
            val status = if (success) "âœ…" else "âŒ"
            SwingUtilities.invokeLater { 
                addToolCallMessage("$status Tool '$toolName' completed in ${duration}ms", System.currentTimeMillis())
            }
        }
    }

    private fun updateToolsList() {
        if (mcpTools.hasError()) {
            // Show error message in the tools list
            val errorMsg = mcpTools.getErrorMessage() ?: "MCP server unavailable"
            toolsList.model = DefaultListModel<String>().apply {
                addElement("âš ï¸ Error: $errorMsg")
                addElement("")
                addElement("Click 'Refresh' to retry")
            }
        } else {
            val toolNames = mcpTools.tools.map { it.name }
            toolsList.model = DefaultListModel<String>().apply {
                toolNames.forEach { addElement(it) }
            }
            
            // Restore last used tool selection
            val config = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
            val lastUsedTool = config.lastUsedTool
            if (lastUsedTool != null && toolNames.contains(lastUsedTool)) {
                toolsList.setSelectedValue(lastUsedTool, true)
            }
        }
    }

    private fun updatePromptsList() {
        val promptNames = prompts.prompts.map { it.name }
        promptsList.model = DefaultListModel<String>().apply {
            promptNames.forEach { addElement(it) }
        }
        
        // Restore last used prompt selection
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
        sendSubmission("ListMcpTools")
        log.info("Refreshing MCP tools...")
    }

    private fun refreshPrompts() {
        sendSubmission("ListCustomPrompts")
        log.info("Refreshing prompts...")
    }

    private fun runTool(toolName: String) {
        sendSubmission("RunTool") {
            addProperty("tool", toolName)
        }
        log.info("Running tool: $toolName")

        addUserMessage("Running tool: $toolName")
    }

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
        sender.send(buildSubmission(id, text, model, effort))
    }

    private fun buildSubmission(id: String, text: String, model: String, effort: String): String {
        val body = JsonObject().apply {
            addProperty("type", "UserMessage")
            addProperty("text", text)
            val ctx = OverrideTurnContext(
                cwd = cwdProvider()?.toString(),
                model = model,
                effort = effort
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
}
