package dev.curt.codexjb.proto

import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.LogSink
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoSenderTest {
    @Test
    fun restartsOnSendFailureAndRetries() {
        val cfg = CodexProcessConfig(executable = Paths.get("/usr/bin/codex"))
        val recorder = RecordingBackend(failFirst = true)
        val log = RecordingLog()
        var reconnected = 0
        val sender = ProtoSender(recorder, cfg, log) { reconnected += 1 }

        sender.send("{\"hello\":true}")

        assertEquals(1, recorder.starts)
        assertEquals(listOf("{\"hello\":true}"), recorder.lines)
        assertEquals(1, reconnected)
        assertEquals(listOf("WARN:send failed; attempting restart"), log.lines)
    }
}

private class RecordingBackend(private var failFirst: Boolean) : SenderBackend {
    val lines = mutableListOf<String>()
    var starts = 0
    override fun start(config: CodexProcessConfig, restart: Boolean): Boolean {
        starts += 1
        return true
    }
    override fun send(line: String) {
        if (failFirst) {
            failFirst = false
            throw IllegalStateException("down")
        }
        lines += line
    }
}

private class RecordingLog : LogSink {
    val lines = mutableListOf<String>()
    override fun info(message: String) { lines += "INFO:$message" }
    override fun warn(message: String) { lines += "WARN:$message" }
    override fun error(message: String, t: Throwable?) { lines += "ERROR:$message" }
}
