package dev.curt.codexjb.ui

import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.proto.*
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatPanelTest {
    @Test
    fun disablesSendWhileActiveAndBuildsSubmission() {
        val bus = EventBus()
        val turns = TurnRegistry()
        val sent = mutableListOf<String>()
        val sender = ProtoSender(
            backend = object : SenderBackend {
                override fun start(config: dev.curt.codexjb.core.CodexProcessConfig, restart: Boolean) = true
                override fun send(line: String) { sent += line }
            },
            config = dev.curt.codexjb.core.CodexProcessConfig(Paths.get("/usr/bin/codex")),
            log = object : LogSink { override fun info(message: String) {}; override fun warn(message: String) {}; override fun error(message: String, t: Throwable?) {} }
        )
        val panel = ChatPanel(
            sender = sender,
            bus = bus,
            turns = turns,
            modelProvider = { "gpt-4.1-mini" },
            reasoningProvider = { "medium" },
            cwdProvider = { Paths.get("/work") }
        )
        assertTrue(sent.any { it.contains("ListMcpTools", ignoreCase = true) })
        assertTrue(sent.any { it.contains("ListCustomPrompts", ignoreCase = true) })
        val before = panel.transcriptCount()
        SwingUtilities.invokeAndWait { panel.submit("Hello") }
        assertTrue(panel.transcriptCount() > before)
        assertTrue(sent.any { it.contains("\"UserMessage\"", ignoreCase = true) })
        assertEquals(1, turns.size())
        assertEquals(false, panel.isSendEnabled())
        assertEquals(true, panel.isSpinnerVisible())
    }

    @Test
    fun clearTranscriptRemovesMessages() {
        val bus = EventBus()
        val turns = TurnRegistry()
        val sender = ProtoSender(
            backend = object : SenderBackend {
                override fun start(config: dev.curt.codexjb.core.CodexProcessConfig, restart: Boolean) = true
                override fun send(line: String) {}
            },
            config = dev.curt.codexjb.core.CodexProcessConfig(Paths.get("/usr/bin/codex")),
            log = object : LogSink { override fun info(message: String) {}; override fun warn(message: String) {}; override fun error(message: String, t: Throwable?) {} }
        )
        val panel = ChatPanel(
            sender = sender,
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
        val sent = mutableListOf<String>()
        val sender = ProtoSender(
            backend = object : SenderBackend {
                override fun start(config: dev.curt.codexjb.core.CodexProcessConfig, restart: Boolean) = true
                override fun send(line: String) { sent += line }
            },
            config = dev.curt.codexjb.core.CodexProcessConfig(Paths.get("/usr/bin/codex")),
            log = object : LogSink { override fun info(message: String) {}; override fun warn(message: String) {}; override fun error(message: String, t: Throwable?) {} }
        )
        val panel = ChatPanel(
            sender = sender,
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

