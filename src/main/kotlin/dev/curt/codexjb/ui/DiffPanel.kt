package dev.curt.codexjb.ui

import com.intellij.openapi.application.ApplicationManager
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.diff.UnifiedDiffParser
import dev.curt.codexjb.tooling.PatchApplier
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.ArrayList
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class DiffPanel(
    diffText: String,
    private val onApply: (List<String>) -> ApplyResult,
    private val onDiscard: () -> Unit = {}
) : JPanel(BorderLayout()) {
    private val config = ApplicationManager.getApplication()?.getService(CodexConfigService::class.java)
    private val patches = UnifiedDiffParser.parse(diffText)
    private val listModel = DefaultListModel<FileItem>()
    private val fileList = JList(listModel)
    private val leftViewer = JTextArea()
    private val rightViewer = JTextArea()
    private val leftLabel = JLabel("")
    private val rightLabel = JLabel("")
    private val applyButton = JButton("Apply")
    private val discardButton = JButton("Discard")
    private val progress = JProgressBar().apply { isIndeterminate = true; isVisible = false }
    private val autoOpenCheck = JCheckBox("Open files after apply").apply {
        isSelected = config?.autoOpenChangedFiles ?: false
        addActionListener { config?.autoOpenChangedFiles = isSelected }
    }
    private val autoStageCheck = JCheckBox("Stage changes after apply").apply {
        isSelected = config?.autoStageAppliedChanges ?: false
        addActionListener { config?.autoStageAppliedChanges = isSelected }
    }

    init {
        fileList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fileList.cellRenderer = FileItemRenderer()
        fileList.addListSelectionListener { showSelected() }
        fileList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = fileList.locationToIndex(e.point)
                if (index >= 0) toggleIncluded(index)
            }
        })
        fileList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle")
        fileList.actionMap.put("toggle", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val index = fileList.selectedIndex
                if (index >= 0) toggleIncluded(index)
            }
        })

        for (p in patches) {
            val name = p.newPath ?: p.oldPath ?: continue
            listModel.addElement(FileItem(name, true))
        }

        leftViewer.isEditable = false
        rightViewer.isEditable = false
        leftViewer.lineWrap = false
        rightViewer.lineWrap = false
        val leftPane = JPanel(BorderLayout()).apply {
            add(leftLabel, BorderLayout.NORTH)
            add(JScrollPane(leftViewer), BorderLayout.CENTER)
        }
        val rightPane = JPanel(BorderLayout()).apply {
            add(rightLabel, BorderLayout.NORTH)
            add(JScrollPane(rightViewer), BorderLayout.CENTER)
        }
        val diffSplit = JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            JScrollPane(fileList),
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightPane)
        ).apply {
            resizeWeight = 0.2
        }
        add(diffSplit, BorderLayout.CENTER)

        applyButton.addActionListener { doApply() }
        discardButton.addActionListener { onDiscard() }
        val buttons = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(applyButton)
            add(Box.createHorizontalStrut(8))
            add(discardButton)
            add(Box.createHorizontalStrut(8))
            add(progress)
        }
        val options = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(autoOpenCheck)
            add(autoStageCheck)
        }
        val bottom = JPanel(BorderLayout()).apply {
            add(buttons, BorderLayout.WEST)
            add(options, BorderLayout.CENTER)
        }
        add(bottom, BorderLayout.SOUTH)

        if (!listModel.isEmpty) fileList.selectedIndex = 0
        updateApplyEnabled()
    }

    private fun toggleIncluded(index: Int) {
        val item = listModel.get(index)
        item.included = !item.included
        fileList.repaint(fileList.getCellBounds(index, index))
        updateApplyEnabled()
    }

    private fun selectedFiles(): List<String> {
        val result = ArrayList<String>(listModel.size())
        for (i in 0 until listModel.size()) {
            val item = listModel.get(i)
            if (item.included) result += item.path
        }
        return result
    }

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
            for (line in h.lines) {
                when {
                    line.startsWith(" ") -> {
                        val content = line.substring(1)
                        left.appendLine(content)
                        right.appendLine(content)
                    }
                    line.startsWith("-") -> left.appendLine(line.substring(1))
                    line.startsWith("+") -> right.appendLine(line.substring(1))
                    else -> {
                        left.appendLine(line)
                        right.appendLine(line)
                    }
                }
            }
        }
        return left.toString() to right.toString()
    }

    private fun doApply() {
        val files = selectedFiles()
        if (files.isEmpty()) {
            Toolkit.getDefaultToolkit().beep()
            return
        }
        applyButton.isEnabled = false
        progress.isVisible = true
        Thread {
            val res = try {
                onApply(files)
            } catch (_: Throwable) {
                ApplyResult(0, files.size)
            }
            SwingUtilities.invokeLater {
                progress.isVisible = false
                val message = "Patch apply: success=${res.success} failed=${res.failed}"
                val type = if (res.failed > 0) JOptionPane.ERROR_MESSAGE else JOptionPane.INFORMATION_MESSAGE
                val note = if (res.failed > 0) "\nSome hunks could not be applied. Resolve conflicts manually and retry." else ""
                JOptionPane.showMessageDialog(
                    this,
                    message + note,
                    "Patch Apply",
                    type
                )
                updateApplyEnabled()
            }
        }.apply { isDaemon = true; name = "codex-apply-patch" }.start()
    }

    private fun updateApplyEnabled() {
        applyButton.isEnabled = selectedFiles().isNotEmpty()
    }

    data class ApplyResult(val success: Int, val failed: Int)

    companion object {
        fun forProject(project: Project, diffText: String, onDiscard: () -> Unit = {}): DiffPanel {
            return DiffPanel(diffText, { files ->
                val summary = PatchApplier.apply(project, diffText, files.toSet())
                ApplyResult(summary.success, summary.failed)
            }, onDiscard)
        }
    }

    @TestOnly
    internal fun toggleForTest(index: Int) = toggleIncluded(index)

    @TestOnly
    internal fun includedFilesForTest(): List<String> = selectedFiles()

    @TestOnly
    internal fun selectForTest(index: Int) {
        fileList.selectedIndex = index
    }

    @TestOnly
    internal fun leftTextForTest(): String = leftViewer.text

    @TestOnly
    internal fun isApplyEnabledForTest(): Boolean = applyButton.isEnabled
}

private data class FileItem(val path: String, var included: Boolean)

private class FileItemRenderer : JCheckBox(), ListCellRenderer<FileItem> {
    init {
        isOpaque = true
    }

    override fun getListCellRendererComponent(
        list: JList<out FileItem>, value: FileItem, index: Int, selected: Boolean, cellHasFocus: Boolean
    ): java.awt.Component {
        text = value.path
        isSelected = value.included
        background = if (selected) list.selectionBackground else list.background
        foreground = if (selected) list.selectionForeground else list.foreground
        return this
    }
}