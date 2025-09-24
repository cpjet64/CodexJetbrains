package dev.curt.codexjb.ui

import dev.curt.codexjb.diff.UnifiedDiffParser
import dev.curt.codexjb.tooling.PatchApplier
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.*

class DiffPanel(
    diffText: String,
    private val onApply: (List<String>) -> ApplyResult,
    private val onDiscard: () -> Unit = {}
) : JPanel(BorderLayout()) {
    private val patches = UnifiedDiffParser.parse(diffText)
    private val listModel = DefaultListModel<FileItem>()
    private val fileList = JList(listModel)
    private val leftViewer = JTextArea()
    private val rightViewer = JTextArea()
    private val leftLabel = JLabel("")
    private val rightLabel = JLabel("")
    private val progress = JProgressBar().apply { isIndeterminate = true; isVisible = false }

    init {
        fileList.cellRenderer = FileItemRenderer()
        for (p in patches) listModel.addElement(FileItem(p.newPath, true))
        fileList.addListSelectionListener { showSelected() }
        leftViewer.isEditable = false
        rightViewer.isEditable = false
        val leftPane = JPanel(BorderLayout()).apply {
            add(leftLabel, BorderLayout.NORTH)
            add(JScrollPane(leftViewer), BorderLayout.CENTER)
        }
        val rightPane = JPanel(BorderLayout()).apply {
            add(rightLabel, BorderLayout.NORTH)
            add(JScrollPane(rightViewer), BorderLayout.CENTER)
        }
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(fileList), JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane))
        add(split, BorderLayout.CENTER)
        val apply = JButton("Apply")
        apply.addActionListener { doApply(apply) }
        val discard = JButton("Discard")
        discard.addActionListener { onDiscard() }
        val bottom = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(apply)
            add(Box.createHorizontalStrut(8))
            add(discard)
            add(Box.createHorizontalStrut(8))
            add(progress)
        }
        add(bottom, BorderLayout.SOUTH)
        if (!listModel.isEmpty) fileList.selectedIndex = 0
    }

    private fun selectedFiles(): List<String> = (0 until listModel.size())
        .map { listModel.get(it) }
        .filter { it.included }
        .map { it.path }

    private fun showSelected() {
        val idx = fileList.selectedIndex
        if (idx < 0) return
        val name = listModel.get(idx).path
        val p = patches.firstOrNull { it.newPath == name || it.oldPath == name } ?: return
        leftLabel.text = p.oldPath ?: p.newPath
        rightLabel.text = p.newPath
        val (leftText, rightText) = buildSideBySideTexts(p)
        leftViewer.text = leftText
        rightViewer.text = rightText
        leftViewer.caretPosition = 0
        rightViewer.caretPosition = 0
    }

    private fun buildSideBySideTexts(patch: dev.curt.codexjb.diff.FilePatch): Pair<String, String> {
        val left = StringBuilder()
        val right = StringBuilder()
        for (h in patch.hunks) {
            // We omit headers in panes; focus on content
            for (line in h.lines) {
                when {
                    line.startsWith(" ") -> { left.appendLine(line.substring(1)); right.appendLine(line.substring(1)) }
                    line.startsWith("-") -> { left.appendLine(line.substring(1)) }
                    line.startsWith("+") -> { right.appendLine(line.substring(1)) }
                    else -> { left.appendLine(line); right.appendLine(line) }
                }
            }
        }
        return left.toString() to right.toString()
    }

    private fun doApply(button: JButton) {
        val files = selectedFiles()
        button.isEnabled = false
        progress.isVisible = true
        Thread {
            val res = try { onApply(files) } catch (_: Throwable) { ApplyResult(0, files.size) }
            SwingUtilities.invokeLater {
                progress.isVisible = false
                button.isEnabled = true
                JOptionPane.showMessageDialog(
                    this,
                    "Patch apply: success=${res.success} failed=${res.failed}",
                    "Patch Apply",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        }.apply { isDaemon = true; name = "codex-apply-patch" }.start()
    }

    data class ApplyResult(val success: Int, val failed: Int)

    companion object {
        fun forProject(project: Project, diffText: String): DiffPanel {
            return DiffPanel(diffText, { files ->
                val sum = PatchApplier.apply(project, diffText, files.toSet())
                ApplyResult(sum.success, sum.failed)
            })
        }
    }
}

private data class FileItem(val path: String, var included: Boolean)

private class FileItemRenderer : JCheckBox(), ListCellRenderer<FileItem> {
    override fun getListCellRendererComponent(
        list: JList<out FileItem>, value: FileItem, index: Int, selected: Boolean, cellHasFocus: Boolean
    ): java.awt.Component {
        text = value.path
        this.isSelected = value.included
        addActionListener { value.included = isSelected }
        return this
    }
}
