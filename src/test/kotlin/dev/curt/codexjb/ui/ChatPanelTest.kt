package dev.curt.codexjb.ui

import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.proto.*
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatPanelTest {
    private fun createMockProtocol(): AppServerProtocol {
        val proc = dev.curt.codexjb.core.CodexProcessService()
        val config = dev.curt.codexjb.core.CodexProcessConfig(Paths.get("/usr/bin/codex"))
        val bus = EventBus()
        return AppServerProtocol(proc, config, bus)
    }

    @Test
    fun disablesSendWhileActiveAndBuildsSubmission() {
        val bus = EventBus()
        val turns = TurnRegistry()
        val protocol = createMockProtocol()
        val panel = ChatPanel(
            protocol = protocol,
            bus = bus,
            turns = turns,
            modelProvider = { "gpt-4.1-mini" },
            reasoningProvider = { "medium" },
            cwdProvider = { Paths.get("/work") }
        )
        val before = panel.transcriptCount()
        SwingUtilities.invokeAndWait { panel.submit("Hello") }
        assertTrue(panel.transcriptCount() > before)
        assertEquals(1, turns.size())
        assertEquals(false, panel.isSendEnabled())
        assertEquals(true, panel.isSpinnerVisible())
    }

    @Test
    fun clearTranscriptRemovesMessages() {
        val bus = EventBus()
        val turns = TurnRegistry()
        val protocol = createMockProtocol()
        val panel = ChatPanel(
            protocol = protocol,
            bus = bus,
            turns = turns,
            modelProvider = { "gpt-4.1-mini" },
            reasoningProvider = { "medium" },
            cwdProvider = { Paths.get("/work") }
        )
        SwingUtilities.invokeAndWait { panel.submit("Hello") }
        assertTrue(panel.transcriptCount() > 0)
        SwingUtilities.invokeAndWait { panel.clearTranscript() }
        assertEquals(0, panel.transcriptCount())
    }

    @Test
    fun appendsAgentDeltaAndSealsOnFinal() {
        val bus = EventBus()
        val turns = TurnRegistry()
        val protocol = createMockProtocol()
        val panel = ChatPanel(
            protocol = protocol,
            bus = bus,
            turns = turns,
            modelProvider = { "gpt-4.1-mini" },
            reasoningProvider = { "medium" },
            cwdProvider = { Paths.get("/work") }
        )
        val id = "t-ui-1"
        SwingUtilities.invokeAndWait { panel.submitWithId("Hello", id) }
        val delta = "{" +
            "\"id\":\"$id\"," +
            "\"msg\":{\"type\":\"AgentMessageDelta\",\"delta\":\"hi\"}}"
        val fin = "{" +
            "\"id\":\"$id\"," +
            "\"msg\":{\"type\":\"AgentMessage\"}}"
        bus.dispatch(delta)
        bus.dispatch(fin)
        assertEquals(1, turns.size())
    }
}

