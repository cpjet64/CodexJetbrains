package dev.curt.codexjb.ui

import dev.curt.codexjb.proto.EventBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities

class ExecConsolePanelTest {

    private fun header(panel: ExecConsolePanel): JLabel {
        return panel.components.filterIsInstance<JLabel>().first()
    }

    private fun killButton(panel: ExecConsolePanel): JButton {
        val buttons = panel.components
            .filterIsInstance<JPanel>()
            .flatMap { it.components.toList() }
            .filterIsInstance<JButton>()
        return buttons.first { it.text == "Kill Process" }
    }

    private fun consoleText(panel: ExecConsolePanel): String {
        val scroll = panel.components.filterIsInstance<JScrollPane>().first()
        val area = scroll.viewport.view as JTextPane
        val doc = area.document
        return doc.getText(0, doc.length)
    }

    private fun pumpEdt() {
        SwingUtilities.invokeAndWait { }
    }

    @Test
    fun setsApprovalStatusInHeader() {
        val panel = ExecConsolePanel()
        SwingUtilities.invokeAndWait {
            panel.showPending("exec-1", "/tmp", "echo hello")
            panel.setApprovalStatus("APPROVED")
        }

        val headerText = header(panel).text
        assertTrue(headerText.contains("Exec: echo hello @ /tmp"))
        assertTrue(headerText.contains("[APPROVED]"))
    }
    
    @Test
    fun updatesApprovalStatusInHeader() {
        val panel = ExecConsolePanel()
        SwingUtilities.invokeAndWait {
            panel.showPending("exec-1", "/tmp", "echo hello")
            panel.setApprovalStatus("PENDING")
        }
        pumpEdt()

        val firstUpdate = header(panel).text
        assertTrue(firstUpdate.contains("[PENDING]"))

        SwingUtilities.invokeAndWait { panel.setApprovalStatus("APPROVED") }
        val secondUpdate = header(panel).text
        assertTrue(secondUpdate.contains("[APPROVED]"))
        assertTrue(!secondUpdate.contains("[PENDING]"))
    }
    
    @Test
    fun clearsApprovalStatus() {
        val panel = ExecConsolePanel()
        SwingUtilities.invokeAndWait {
            panel.showPending("exec-1", "/tmp", "echo hello")
            panel.setApprovalStatus("APPROVED")
        }
        val withStatus = header(panel).text

        SwingUtilities.invokeAndWait { panel.setApprovalStatus(null) }
        val withoutStatus = header(panel).text
        assertTrue(!withoutStatus.contains("[APPROVED]"))
        assertTrue(withoutStatus.length < withStatus.length)
    }
    
    @Test
    fun preservesBaseHeaderWhenUpdatingApproval() {
        val panel = ExecConsolePanel()
        SwingUtilities.invokeAndWait { panel.onBegin("exec-1", "/test", "echo hello") }
        val baseText = header(panel).text

        SwingUtilities.invokeAndWait { panel.setApprovalStatus("APPROVED") }
        val withApproval = header(panel).text

        assertTrue(withApproval.contains("Exec: echo hello @ /test"))
        assertTrue(withApproval.contains("[APPROVED]"))
        assertTrue(withApproval.startsWith("Exec"))
        assertTrue(withApproval.length >= baseText.length)
    }

    @Test
    fun retainsPendingStatusAcrossBegin() {
        val panel = ExecConsolePanel()
        SwingUtilities.invokeAndWait {
            panel.showPending("exec-1", "/tmp", "echo hello")
            panel.setApprovalStatus("PENDING")
            panel.onBegin("exec-1", "/tmp", "echo hello")
        }
        val text = header(panel).text
        assertTrue(text.contains("[PENDING]"))
    }

    @Test
    fun killButtonInvokesCancelHandler() {
        val panel = ExecConsolePanel()
        var cancelled: String? = null
        SwingUtilities.invokeAndWait {
            panel.setCancelHandler { cancelled = it }
            panel.onBegin("exec-123", "/tmp", "echo hello")
        }
        SwingUtilities.invokeAndWait { killButton(panel).doClick() }
        assertEquals("exec-123", cancelled)
    }

    @Test
    fun wireProcessesExecEvents() {
        val panel = ExecConsolePanel()
        val bus = EventBus()
        panel.wire(bus)

        bus.dispatch("""{"id":"exec-1","msg":{"type":"ExecCommandBegin","command":"echo hi","cwd":"/tmp"}}""")
        pumpEdt()
        bus.dispatch("""{"id":"exec-1","msg":{"type":"ExecCommandOutputDelta","chunk":"\u001B[31mError\u001B[0m"}}""")
        pumpEdt()
        bus.dispatch("""{"id":"exec-1","msg":{"type":"ExecCommandEnd","exit_code":0,"duration_ms":42}}""")
        pumpEdt()

        val text = consoleText(panel)
        assertTrue(text.contains("Error"))
        assertTrue(text.contains("exit=0"))
        assertTrue(!text.contains("\u001B"))
        assertNotNull(header(panel))
    }
}
