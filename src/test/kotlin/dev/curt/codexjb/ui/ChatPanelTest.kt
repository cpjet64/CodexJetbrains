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
            effortProvider = { "medium" },
            cwdProvider = { Paths.get("/work") }
        )
        SwingUtilities.invokeAndWait { panel.submit("Hello") }
        assertTrue(sent.first().contains("\"UserMessage\""))
        assertEquals(1, turns.size())
    }
}

