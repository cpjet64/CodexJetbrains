package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import dev.curt.codexjb.proto.EventBus
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class ErrorBanner : JPanel(BorderLayout()) {
    private val label = JLabel("")
    private val close = JButton("Dismiss")

    init {
        background = Color(0xFF, 0xEE, 0xEE)
        isVisible = false
        border = EmptyBorder(6, 8, 6, 8)
        add(label, BorderLayout.CENTER)
        add(close, BorderLayout.EAST)
        label.foreground = Color(0x80, 0, 0)
        close.addActionListener { isVisible = false }
        label.accessibleContext.accessibleName = "Error message"
        close.accessibleContext.accessibleName = "Dismiss error"
    }

    fun wire(bus: EventBus) {
        bus.addListener("*") { _, msg -> onEvent(msg) }
    }

    private fun onEvent(msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        if (type != "StreamError" && type != "Error") return
        val text = msg.get("message")?.asString
            ?: msg.get("error")?.asString
            ?: msg.toString()
        SwingUtilities.invokeLater { show(text) }
    }

    fun show(message: String) {
        label.text = message
        isVisible = true
        revalidate()
        repaint()
    }
}

