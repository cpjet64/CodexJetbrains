package dev.curt.codexjb.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import javax.swing.JLabel

class ExecConsolePanelTest {

    @Test
    fun setsApprovalStatusInHeader() {
        val panel = ExecConsolePanel()
        
        // Initial state should have no approval status
        val header = panel.getComponents().find { it is JLabel } as JLabel
        val initialText = header.text
        
        // Set approval status
        panel.setApprovalStatus("APPROVED")
        
        // Verify approval status is appended to header
        assertTrue(header.text.contains("[APPROVED]"))
        assertTrue(header.text.contains(initialText))
    }
    
    @Test
    fun updatesApprovalStatusInHeader() {
        val panel = ExecConsolePanel()
        val header = panel.getComponents().find { it is JLabel } as JLabel
        
        // Set initial approval status
        panel.setApprovalStatus("PENDING")
        val firstUpdate = header.text
        
        // Update approval status
        panel.setApprovalStatus("APPROVED")
        val secondUpdate = header.text
        
        // Verify old status is replaced
        assertTrue(secondUpdate.contains("[APPROVED]"))
        assertTrue(!secondUpdate.contains("[PENDING]"))
    }
    
    @Test
    fun clearsApprovalStatus() {
        val panel = ExecConsolePanel()
        val header = panel.getComponents().find { it is JLabel } as JLabel
        
        // Set approval status
        panel.setApprovalStatus("APPROVED")
        val withStatus = header.text
        
        // Clear approval status
        panel.setApprovalStatus(null)
        val withoutStatus = header.text
        
        // Verify status is removed
        assertTrue(!withoutStatus.contains("[APPROVED]"))
        assertTrue(withoutStatus.length < withStatus.length)
    }
    
    @Test
    fun preservesBaseHeaderWhenUpdatingApproval() {
        val panel = ExecConsolePanel()
        val header = panel.getComponents().find { it is JLabel } as JLabel
        
        // Set base header
        panel.onBegin("/test", "echo hello")
        val baseText = header.text
        
        // Add approval status
        panel.setApprovalStatus("APPROVED")
        val withApproval = header.text
        
        // Verify base text is preserved
        assertTrue(withApproval.contains("Exec: echo hello @ /test"))
        assertTrue(withApproval.contains("[APPROVED]"))
    }
}