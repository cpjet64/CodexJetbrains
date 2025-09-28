package dev.curt.codexjb.ui

import dev.curt.codexjb.core.DiagnosticsService
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class DiagnosticsPanel : JPanel(BorderLayout()) {
    private val textArea = JTextArea()
    private val copyButton = JButton("Copy")
    private val clearButton = JButton("Clear")
    private val listener: (List<String>) -> Unit = { lines -> updateText(lines) }

    init {
        textArea.isEditable = false
        add(JScrollPane(textArea), BorderLayout.CENTER)

        val buttons = JPanel().apply {
            add(copyButton)
            add(clearButton)
        }
        add(buttons, BorderLayout.SOUTH)

        copyButton.addActionListener { DiagnosticsService.copyToClipboard() }
        clearButton.addActionListener {
            DiagnosticsService.append("Diagnostics cleared by user")
            DiagnosticsService.clear()
        }

        DiagnosticsService.addListener(listener)
    }

    private fun updateText(lines: List<String>) {
        SwingUtilities.invokeLater {
            textArea.text = lines.joinToString(System.lineSeparator())
            textArea.caretPosition = textArea.document.length
        }
    }

    fun dispose() {
        DiagnosticsService.removeListener(listener)
    }
}
