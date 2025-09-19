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
    private val cwdProvider: () -> Path?
) : JPanel(BorderLayout()) {

    private val log: LogSink = CodexLogger.forClass(ChatPanel::class.java)
    private val transcript = JPanel()
    private val scroll = JScrollPane(transcript)
    private val input = JTextArea(3, 40)
    private val send = JButton("Send")
    private val clear = JButton("Clear")
    private var activeTurnId: String? = null
    private var currentAgentArea: JTextArea? = null

    init {
        transcript.layout = BoxLayout(transcript, BoxLayout.Y_AXIS)
        transcript.border = EmptyBorder(8, 8, 8, 8)
        scroll.verticalScrollBar.unitIncrement = 16
        add(scroll, BorderLayout.CENTER)
        add(buildFooter(), BorderLayout.SOUTH)
        registerListeners()
    }

    private fun buildFooter(): JComponent {
        val panel = JPanel(BorderLayout())
        val controls = JPanel()
        input.lineWrap = true
        input.wrapStyleWord = true
        input.toolTipText = "Ask Codex"
        send.addActionListener(this::onSend)
        send.toolTipText = "Send to Codex"
        clear.addActionListener { clearTranscript() }
        clear.toolTipText = "Clear chat"
        controls.add(send)
        controls.add(clear)
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
    }

    private fun onSend(@Suppress("UNUSED_PARAMETER") e: ActionEvent) {
        val text = input.text.trim()
        if (text.isEmpty()) return
        submit(text)
    }

    internal fun submit(text: String) {
        if (send.isEnabled.not()) return
        val id = Ids.newId()
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
            add("override", JsonObject().apply {
                addProperty("model", model)
                addProperty("effort", effort)
                cwdProvider()?.let { addProperty("cwd", it.toString()) }
            })
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
}
