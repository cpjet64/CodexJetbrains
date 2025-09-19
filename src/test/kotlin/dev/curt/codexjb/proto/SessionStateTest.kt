package dev.curt.codexjb.proto

import com.google.gson.JsonObject
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
}

