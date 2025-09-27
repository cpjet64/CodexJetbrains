package dev.curt.codexjb.ui

import dev.curt.codexjb.core.TelemetryService
import dev.curt.codexjb.proto.EventBus
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextPane
import javax.swing.text.StyledDocument

class ExecConsolePanel : JPanel(BorderLayout()) {
    private val header = JLabel("")
    private val area = JTextPane()
    private val copy = JButton("Copy All")
    private val kill = JButton("Kill Process")
    private var currentProcess: Process? = null
    private val maxBufferSize = 100_000 // 100KB limit
    private var approvalStatus: String? = null

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
            currentProcess?.let { process ->
                if (process.isAlive) {
                    process.destroyForcibly()
                    val doc = area.styledDocument
                    doc.insertString(doc.length, "\n[Process killed]\n", null)
                }
            }
        }
        
        kill.isEnabled = false
    }

    fun wire(bus: EventBus) {
        bus.addListener("ExecCommandBegin") { _, msg ->
            val cwd = msg.get("cwd")?.asString ?: ""
            val cmd = msg.get("command")?.asString ?: ""
            onBegin(cwd, cmd)
        }
        bus.addListener("ExecCommandOutputDelta") { _, msg ->
            onDelta(msg.get("chunk")?.asString ?: "")
        }
        bus.addListener("ExecCommandEnd") { _, msg ->
            val code = msg.get("exit_code")?.asInt ?: 0
            val dur = msg.get("duration_ms")?.asLong ?: 0L
            onEnd(code, dur)
        }
    }

    fun onBegin(cwd: String, command: String) {
        header.text = "Exec: $command @ $cwd"
        area.text = ""
        kill.isEnabled = true
        updateHeader() // Apply approval status if any
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
        kill.isEnabled = false
        currentProcess = null
        
        // Record telemetry
        if (code == 0) {
            TelemetryService.recordExecCommandSuccess()
        } else {
            TelemetryService.recordExecCommandFailure()
        }
    }
    
    fun setCurrentProcess(process: Process?) {
        currentProcess = process
        kill.isEnabled = process?.isAlive == true
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
}

