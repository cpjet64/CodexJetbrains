package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import dev.curt.codexjb.proto.EventBus
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class ErrorBanner : JPanel(BorderLayout()) {
    private val label = JLabel("")
    private val close = JButton("Dismiss")

    init {
        background = CodexTheme.errorBannerBg
        isVisible = false
        border = EmptyBorder(
            CodexTheme.bannerPadding.top,
            CodexTheme.bannerPadding.left,
            CodexTheme.bannerPadding.bottom,
            CodexTheme.bannerPadding.right
        )
        add(label, BorderLayout.CENTER)
        add(close, BorderLayout.EAST)
        label.foreground = CodexTheme.errorBannerFg
        label.font = CodexTheme.secondaryFont
        close.font = CodexTheme.secondaryFont
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
