package dev.curt.codexjb.ui

import java.awt.BorderLayout
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
        background = CodexTheme.infoBannerBg
        isVisible = false
        border = EmptyBorder(
            CodexTheme.bannerPadding.top,
            CodexTheme.bannerPadding.left,
            CodexTheme.bannerPadding.bottom,
            CodexTheme.bannerPadding.right
        )
        add(label, BorderLayout.CENTER)
        add(close, BorderLayout.EAST)
        label.foreground = CodexTheme.infoBannerFg
        label.font = CodexTheme.secondaryFont
        close.font = CodexTheme.secondaryFont
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
