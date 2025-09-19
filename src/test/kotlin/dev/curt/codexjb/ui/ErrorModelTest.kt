package dev.curt.codexjb.ui

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorModelTest {
    @Test
    fun recordsStreamErrorMessage() {
        val m = ErrorModel()
        val msg = JsonObject().apply {
            addProperty("type", "StreamError")
            addProperty("message", "Bad request")
        }
        m.onEvent("t1", msg)
        assertEquals("Bad request", m.lastError)
    }

    @Test
    fun ignoresOtherTypes() {
        val m = ErrorModel()
        val msg = JsonObject().apply { addProperty("type", "AgentMessageDelta") }
        m.onEvent("t1", msg)
        assertNull(m.lastError)
    }
}

