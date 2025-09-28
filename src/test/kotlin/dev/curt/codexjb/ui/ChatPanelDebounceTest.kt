package dev.curt.codexjb.ui

import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.proto.EventBus
import dev.curt.codexjb.proto.ProtoSender
import dev.curt.codexjb.proto.SenderBackend
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
        val backend = RecordingBackend()
        val sender = ProtoSender(backend, TEST_CONFIG, RecordingLog())
        val panel = createPanel(sender)
        val button = button(panel, "refreshToolsButton")
        val initial = backend.lines.size

        SwingUtilities.invokeAndWait { button.doClick() }
        repeat(4) {
            Thread.sleep(100) // within debounce window
            SwingUtilities.invokeAndWait { button.doClick() }
        }

        assertEquals(
            initial + 1,
            backend.lines.size,
            "Only first refresh should be sent within debounce window"
        )
    }

    @Test
    fun allowsRefreshAfterDebounceWindow() {
        val backend = RecordingBackend()
        val sender = ProtoSender(backend, TEST_CONFIG, RecordingLog())
        val panel = createPanel(sender)
        val button = button(panel, "refreshToolsButton")

        val initial = backend.lines.size
        SwingUtilities.invokeAndWait { button.doClick() }
        val firstCount = backend.lines.size

        Thread.sleep(1100)
        SwingUtilities.invokeAndWait { button.doClick() }
        val secondCount = backend.lines.size

        assertTrue(secondCount > firstCount)
        assertEquals(initial + 2, secondCount, "Expected exactly two refresh submissions after debounce window")
    }

    private fun createPanel(sender: ProtoSender): ChatPanel {
        val panelRef = AtomicReference<ChatPanel>()
        SwingUtilities.invokeAndWait {
            panelRef.set(
                ChatPanel(
                    sender = sender,
                    bus = EventBus(),
                    turns = TurnRegistry(),
                    modelProvider = { "test-model" },
                    effortProvider = { "test-effort" },
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

    private class RecordingBackend : SenderBackend {
        val lines = mutableListOf<String>()
        override fun start(config: CodexProcessConfig, restart: Boolean): Boolean = true
        override fun send(line: String) { lines += line }
    }

    private class RecordingLog : LogSink {
        override fun info(message: String) {}
        override fun warn(message: String) {}
        override fun error(message: String, t: Throwable?) {}
    }

    companion object {
        private val TEST_CONFIG = CodexProcessConfig(executable = Paths.get("/usr/bin/codex"))
    }
}
