package dev.curt.codexjb.ui

import java.awt.Color
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

object AnsiColorHandler {
    
    private val colorMap = mapOf(
        30 to Color.BLACK,
        31 to Color.RED,
        32 to Color.GREEN,
        33 to Color.YELLOW,
        34 to Color.BLUE,
        35 to Color.MAGENTA,
        36 to Color.CYAN,
        37 to Color.WHITE,
        90 to Color.GRAY,
        91 to Color.PINK,
        92 to Color.GREEN,
        93 to Color.YELLOW,
        94 to Color.BLUE,
        95 to Color.MAGENTA,
        96 to Color.CYAN,
        97 to Color.WHITE
    )
    
    private val brightColorMap = mapOf(
        30 to Color.DARK_GRAY,
        31 to Color.RED,
        32 to Color.GREEN,
        33 to Color.YELLOW,
        34 to Color.BLUE,
        35 to Color.MAGENTA,
        36 to Color.CYAN,
        37 to Color.WHITE
    )
    
    fun processAnsiCodes(text: String, doc: StyledDocument, startOffset: Int) {
        val regex = Regex("\u001B\\[([0-9;]+)m")
        var currentOffset = startOffset
        var currentColor = Color.BLACK
        var currentStyle = 0 // 0 = normal, 1 = bold, 2 = dim, 3 = italic, 4 = underline
        
        val matches = regex.findAll(text)
        var lastEnd = 0
        
        for (match in matches) {
            val beforeText = text.substring(lastEnd, match.range.first)
            if (beforeText.isNotEmpty()) {
                val attrs = createAttributes(currentColor, currentStyle)
                doc.insertString(currentOffset, beforeText, attrs)
                currentOffset += beforeText.length
            }
            
            val codes = match.groupValues[1].split(';').mapNotNull { it.toIntOrNull() }
            for (code in codes) {
                when (code) {
                    0 -> { // Reset
                        currentColor = Color.BLACK
                        currentStyle = 0
                    }
                    1 -> currentStyle = currentStyle or 1 // Bold
                    2 -> currentStyle = currentStyle or 2 // Dim
                    3 -> currentStyle = currentStyle or 4 // Italic
                    4 -> currentStyle = currentStyle or 8 // Underline
                    22 -> currentStyle = currentStyle and 1.inv() // Reset bold/dim
                    23 -> currentStyle = currentStyle and 4.inv() // Reset italic
                    24 -> currentStyle = currentStyle and 8.inv() // Reset underline
                    in 30..37 -> currentColor = colorMap[code] ?: Color.BLACK
                    in 90..97 -> currentColor = colorMap[code] ?: Color.BLACK
                    in 40..47 -> { /* Background colors - not implemented */ }
                    in 100..107 -> { /* Bright background colors - not implemented */ }
                }
            }
            
            lastEnd = match.range.last + 1
        }
        
        // Add remaining text
        val remainingText = text.substring(lastEnd)
        if (remainingText.isNotEmpty()) {
            val attrs = createAttributes(currentColor, currentStyle)
            doc.insertString(currentOffset, remainingText, attrs)
        }
    }
    
    private fun createAttributes(color: Color, style: Int): AttributeSet {
        val attrs = SimpleAttributeSet()
        StyleConstants.setForeground(attrs, color)
        
        if (style and 1 != 0) { // Bold
            StyleConstants.setBold(attrs, true)
        }
        if (style and 2 != 0) { // Dim
            // Dim is not directly supported, use a lighter color
            val dimColor = Color(
                (color.red * 0.7).toInt(),
                (color.green * 0.7).toInt(),
                (color.blue * 0.7).toInt()
            )
            StyleConstants.setForeground(attrs, dimColor)
        }
        if (style and 4 != 0) { // Italic
            StyleConstants.setItalic(attrs, true)
        }
        if (style and 8 != 0) { // Underline
            StyleConstants.setUnderline(attrs, true)
        }
        
        return attrs
    }
    
    fun stripAnsiCodes(text: String): String {
        return text.replace(Regex("\u001B\\[[0-9;]*m"), "")
    }
}
