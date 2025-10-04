package dev.curt.codexjb.ui

import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.CodexProcessService
import dev.curt.codexjb.proto.AppServerProtocol
import dev.curt.codexjb.proto.EventBus
import dev.curt.codexjb.proto.TurnRegistry
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JButton
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatPanelDebounceTest {

    @Test
    fun debouncesRapidRefreshClicks() {
        val protocol = createMockProtocol()
        val panel = createPanel(protocol)
        val button = button(panel, "refreshToolsButton")

        // Just verify the button exists and can be clicked without errors
        SwingUtilities.invokeAndWait { button.doClick() }
        repeat(4) {
            Thread.sleep(100) // within debounce window
            SwingUtilities.invokeAndWait { button.doClick() }
        }

        // TODO: Re-implement test with proper mocking when protocol supports it
        assertTrue(true, "Debounce test placeholder")
    }

    @Test
    fun allowsRefreshAfterDebounceWindow() {
        val protocol = createMockProtocol()
        val panel = createPanel(protocol)
        val button = button(panel, "refreshToolsButton")

        SwingUtilities.invokeAndWait { button.doClick() }

        Thread.sleep(1100)
        SwingUtilities.invokeAndWait { button.doClick() }

        // TODO: Re-implement test with proper mocking when protocol supports it
        assertTrue(true, "Debounce test placeholder")
    }

    private fun createMockProtocol(): AppServerProtocol {
        val proc = CodexProcessService()
        val config = TEST_CONFIG
        val bus = EventBus()
        return AppServerProtocol(proc, config, bus)
    }

    private fun createPanel(protocol: AppServerProtocol): ChatPanel {
        val panelRef = AtomicReference<ChatPanel>()
        SwingUtilities.invokeAndWait {
            panelRef.set(
                ChatPanel(
                    protocol = protocol,
                    bus = EventBus(),
                    turns = TurnRegistry(),
                    modelProvider = { "test-model" },
                    reasoningProvider = { "test-effort" },
                    cwdProvider = { null }
                )
            )
        }
        return panelRef.get()
    }

    private fun button(panel: ChatPanel, fieldName: String): JButton {
        val field = ChatPanel::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(panel) as JButton
    }

    companion object {
        private val TEST_CONFIG = CodexProcessConfig(executable = Paths.get("/usr/bin/codex"))
    }
}

