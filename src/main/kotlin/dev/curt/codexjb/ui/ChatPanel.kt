package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.proto.*
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
    private val promptsPanel = JPanel()
    private val promptsList = JList<String>()
    private var activeTurnId: String? = null
    private var currentAgentArea: JTextArea? = null

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
        
        val toolsLabel = JLabel("Available Tools:")
        toolsPanel.add(toolsLabel, BorderLayout.NORTH)
        
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
                    toolTipText = mcpTools.tools.find { it.name == value }?.description ?: ""
                }
                return renderer
            }
        }
        
        toolsPanel.add(JScrollPane(toolsList), BorderLayout.CENTER)
        
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
        
        val promptsLabel = JLabel("Available Prompts:")
        promptsPanel.add(promptsLabel, BorderLayout.NORTH)
        
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
                    toolTipText = prompts.prompts.find { it.name == value }?.description ?: ""
                }
                return renderer
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
    }
    
    private fun updateToolsList() {
        val toolNames = mcpTools.tools.map { it.name }
        toolsList.model = DefaultListModel<String>().apply {
            toolNames.forEach { addElement(it) }
        }
    }
    
    private fun updatePromptsList() {
        val promptNames = prompts.prompts.map { it.name }
        promptsList.model = DefaultListModel<String>().apply {
            promptNames.forEach { addElement(it) }
        }
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

    private fun renderUserBubble(text: String) {
        val label = JLabel(text)
        label.isOpaque = true
        label.background = Color(0xE8F0FE)
        label.border = EmptyBorder(8, 8, 8, 8)
        label.alignmentX = Component.RIGHT_ALIGNMENT
        transcript.add(Box.createVerticalStrut(4))
        transcript.add(label)
        transcript.revalidate()
        scrollToBottom()
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

    fun clearTranscript() {
        transcript.removeAll()
        transcript.revalidate()
        transcript.repaint()
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
