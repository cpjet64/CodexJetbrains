package dev.curt.codexjb.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.Insets

/**
 * Centralized theme constants for the Codex UI.
 * All colors use JBColor for automatic light/dark theme adaptation.
 */
object CodexTheme {
    // === Spacing ===
    const val panelPadding = 8
    const val sectionGap = 6
    const val bubbleGap = 10
    const val bubbleRadius = 10
    val bubblePadding = Insets(10, 14, 10, 14)
    val bannerPadding = Insets(6, 10, 6, 10)
    const val bannerRadius = 6

    // === Banner Colors ===
    val errorBannerBg: Color = JBColor(Color(0xFFF0F0), Color(0x3C2020))
    val errorBannerFg: Color = JBColor(Color(0x9B1C1C), Color(0xF5A5A5))
    val infoBannerBg: Color = JBColor(Color(0xECF7EC), Color(0x1E3A1E))
    val infoBannerFg: Color = JBColor(Color(0x0A6B2E), Color(0xA5DBA5))
    val warningFg: Color = JBColor(Color(0xB00000), Color(0xFF6B6B))

    // === Label Colors ===
    val primaryLabelFg: Color get() = UIUtil.getLabelForeground()
    val secondaryLabelFg: Color = JBColor(Color(0x666666), Color(0x999999))
    val mutedLabelFg: Color = JBColor(Color(0x888888), Color(0x777777))

    // === Fonts ===
    val headingFont: Font = UIUtil.getLabelFont().deriveFont(Font.BOLD, 14f)
    val bodyFont: Font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 12f)
    val secondaryFont: Font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
    val monoFont: Font = Font(Font.MONOSPACED, Font.PLAIN, 12)
}
