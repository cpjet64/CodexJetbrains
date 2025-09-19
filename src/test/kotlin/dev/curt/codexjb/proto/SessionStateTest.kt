package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import dev.curt.codexjb.core.LogSink
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStateTest {
    @Test
    fun updatesOnSessionConfigured() {
        val s = SessionState()
        val msg = JsonObject().apply {
            addProperty("type", "SessionConfigured")
            addProperty("model", "gpt-4.1-mini")
            addProperty("effort", "medium")
            addProperty("rollout_path", "/v1")
        }
        s.onEvent("t1", msg)
        assertEquals("gpt-4.1-mini", s.model)
        assertEquals("medium", s.effort)
        assertEquals("/v1", s.rolloutPath)
    }

    @Test
    fun logsRolloutPath() {
        val log = RecLog()
        val s = SessionState(log)
        val msg = JsonObject().apply {
            addProperty("type", "SessionConfigured")
            addProperty("rollout_path", "/v2")
        }
        s.onEvent("t1", msg)
        assertEquals(listOf("INFO:Session rollout_path: /v2"), log.lines)
    }
}

private class RecLog : LogSink {
    val lines = mutableListOf<String>()
    override fun info(message: String) { lines += "INFO:$message" }
    override fun warn(message: String) { lines += "WARN:$message" }
    override fun error(message: String, t: Throwable?) { lines += "ERROR:$message" }
}
