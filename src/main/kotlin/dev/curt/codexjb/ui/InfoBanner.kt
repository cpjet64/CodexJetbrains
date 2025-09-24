package dev.curt.codexjb.ui

import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.border.EmptyBorder

class InfoBanner : JPanel(BorderLayout()) {
    private val label = JLabel("")
    private val close = JButton("Hide")
    private var timer: Timer? = null

    init {
        background = Color(0xEE, 0xF7, 0xEE)
        isVisible = false
        border = EmptyBorder(6, 8, 6, 8)
        add(label, BorderLayout.CENTER)
        add(close, BorderLayout.EAST)
        label.foreground = Color(0x00, 0x55, 0x22)
        close.addActionListener { hideNow() }
        label.accessibleContext.accessibleName = "Info message"
        close.accessibleContext.accessibleName = "Hide info"
    }

    fun show(message: String, autoHideMs: Int = 3000) {
        label.text = message
        isVisible = true
        revalidate()
        repaint()
        timer?.stop()
        timer = Timer(autoHideMs) { hideNow() }.also { it.isRepeats = false; it.start() }
    }

    private fun hideNow() {
        timer?.stop()
        timer = null
        isVisible = false
        revalidate()
        repaint()
    }
}

