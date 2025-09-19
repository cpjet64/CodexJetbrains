package dev.curt.codexjb.ui

import dev.curt.codexjb.diff.UnifiedDiffParser
import java.awt.BorderLayout
import javax.swing.*

class DiffPanel(diffText: String, private val onApply: (List<String>) -> Unit) : JPanel(BorderLayout()) {
    private val patches = UnifiedDiffParser.parse(diffText)
    private val listModel = DefaultListModel<FileItem>()
    private val fileList = JList(listModel)
    private val viewer = JTextArea()

    init {
        fileList.cellRenderer = FileItemRenderer()
        for (p in patches) listModel.addElement(FileItem(p.newPath, true))
        fileList.addListSelectionListener { showSelected() }
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(fileList), JScrollPane(viewer)),
            BorderLayout.CENTER)
        val apply = JButton("Apply")
        apply.addActionListener { onApply(selectedFiles()) }
        add(apply, BorderLayout.SOUTH)
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
        viewer.text = buildString {
            for (h in p.hunks) {
                appendLine(h.header)
                h.lines.forEach { appendLine(it) }
            }
        }
        viewer.caretPosition = 0
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
