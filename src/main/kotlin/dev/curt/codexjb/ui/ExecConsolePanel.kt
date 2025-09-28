package dev.curt.codexjb.ui

import dev.curt.codexjb.core.TelemetryService
import dev.curt.codexjb.proto.EventBus
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.StyledDocument

class ExecConsolePanel : JPanel(BorderLayout()) {
    private val header = JLabel("")
    private val area = JTextPane()
    private val copy = JButton("Copy All")
    private val kill = JButton("Kill Process")
    private val maxBufferSize = 100_000 // 100KB limit
    private var approvalStatus: String? = null
    private var cancelHandler: ((String) -> Unit)? = null
    private var currentExecId: String? = null
    private var execActive = false

    init {
        area.isEditable = false
        add(header, BorderLayout.NORTH)
        add(JScrollPane(area), BorderLayout.CENTER)
        
        val buttonPanel = JPanel()
        buttonPanel.add(copy)
        buttonPanel.add(kill)
        add(buttonPanel, BorderLayout.SOUTH)
        
        copy.addActionListener {
            val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clip.setContents(java.awt.datatransfer.StringSelection(area.text), null)
        }
        
        kill.addActionListener {
            val execId = currentExecId
            val handler = cancelHandler
            if (execId != null && handler != null) {
                handler(execId)
                appendLine("[cancel requested]")
                kill.isEnabled = false
            }
        }

        kill.isEnabled = false
    }

    fun wire(bus: EventBus) {
        bus.addListener("ExecCommandBegin") { id, msg ->
            val cwd = msg.get("cwd")?.asString ?: ""
            val cmd = msg.get("command")?.asString ?: ""
            SwingUtilities.invokeLater { onBegin(id, cwd, cmd) }
        }
        bus.addListener("ExecCommandOutputDelta") { _, msg ->
            val chunk = msg.get("chunk")?.asString ?: ""
            if (chunk.isEmpty()) return@addListener
            SwingUtilities.invokeLater { onDelta(chunk) }
        }
        bus.addListener("ExecCommandEnd") { _, msg ->
            val code = msg.get("exit_code")?.asInt ?: 0
            val dur = msg.get("duration_ms")?.asLong ?: 0L
            SwingUtilities.invokeLater { onEnd(code, dur) }
        }
    }

    fun showPending(execId: String, cwd: String, command: String) {
        updateContext(execId, cwd, command, resetStatus = true, clearOutput = true)
        execActive = false
        updateKillEnabled()
    }

    fun onBegin(execId: String, cwd: String, command: String) {
        val isNewExec = execId != currentExecId
        updateContext(execId, cwd, command, resetStatus = isNewExec, clearOutput = true)
        execActive = true
        updateKillEnabled()
    }

    fun onDelta(chunk: String) { 
        val doc = area.styledDocument
        val startOffset = doc.length
        
        // Check buffer size and trim if necessary
        if (startOffset > maxBufferSize) {
            val trimSize = maxBufferSize / 2
            doc.remove(0, trimSize)
        }
        
        AnsiColorHandler.processAnsiCodes(chunk, doc, doc.length)
    }

    fun onEnd(code: Int, durationMs: Long) {
        val doc = area.styledDocument
        val endText = "\n[exit=$code in ${durationMs}ms]\n"
        doc.insertString(doc.length, endText, null)
        currentExecId = null
        execActive = false
        updateKillEnabled()

        // Record telemetry
        if (code == 0) {
            TelemetryService.recordExecCommandSuccess()
        } else {
            TelemetryService.recordExecCommandFailure()
        }
    }

    fun setCancelHandler(handler: ((String) -> Unit)?) {
        cancelHandler = handler
        updateKillEnabled()
    }

    fun setApprovalStatus(status: String?) {
        approvalStatus = status
        updateHeader()
    }

    private fun updateHeader() {
        val baseText = header.text.replace(Regex(" \\[.*\\]$"), "") // Remove existing approval status
        val approvalText = if (approvalStatus != null) " [$approvalStatus]" else ""
        header.text = baseText + approvalText
    }

    private fun updateKillEnabled() {
        kill.isEnabled = execActive && cancelHandler != null
    }

    private fun appendLine(line: String) {
        val doc = area.styledDocument
        doc.insertString(doc.length, "\n$line\n", null)
    }

    private fun updateContext(
        execId: String,
        cwd: String,
        command: String,
        resetStatus: Boolean,
        clearOutput: Boolean
    ) {
        val previousId = currentExecId
        currentExecId = execId
        if (resetStatus && previousId != execId) {
            approvalStatus = null
        }
        header.text = "Exec: $command @ $cwd"
        if (clearOutput) area.text = ""
        updateHeader()
    }
}
