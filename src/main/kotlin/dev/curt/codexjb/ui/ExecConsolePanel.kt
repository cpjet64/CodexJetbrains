package dev.curt.codexjb.ui

import dev.curt.codexjb.proto.EventBus
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class ExecConsolePanel : JPanel(BorderLayout()) {
    private val header = JLabel("")
    private val area = JTextArea()
    private val copy = JButton("Copy All")

    init {
        area.isEditable = false
        add(header, BorderLayout.NORTH)
        add(JScrollPane(area), BorderLayout.CENTER)
        copy.addActionListener {
            val clip = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clip.setContents(java.awt.datatransfer.StringSelection(area.text), null)
        }
        add(copy, BorderLayout.SOUTH)
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
    }

    fun onDelta(chunk: String) { area.append(chunk) }

    fun onEnd(code: Int, durationMs: Long) {
        area.append("\n[exit=$code in ${durationMs}ms]\n")
    }
}

