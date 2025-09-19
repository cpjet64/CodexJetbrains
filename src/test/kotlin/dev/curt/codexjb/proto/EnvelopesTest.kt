package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EnvelopesTest {
    @Test
    fun encodesSubmission() {
        val body = JsonObject().apply { addProperty("text", "hi") }
        val env = SubmissionEnvelope("abc", "Submit", body)
        val s = EnvelopeJson.encodeSubmission(env)
        // basic shape
        assertEquals(true, s.contains("\"id\":\"abc\""))
        assertEquals(true, s.contains("\"op\":\"Submit\""))
        assertEquals(true, s.contains("\"text\":\"hi\""))
    }

    @Test
    fun parsesEvent() {
        val line = "{" +
            "\"id\":\"abc\"," +
            "\"msg\":{\"type\":\"AgentMessageDelta\",\"delta\":\"hi\"}}"
        val ev = EnvelopeJson.parseEvent(line)
        assertNotNull(ev)
        assertEquals("abc", ev.id)
        assertEquals("AgentMessageDelta", ev.msg["type"].asString)
    }

    @Test
    fun tolerantParsingReturnsNull() {
        assertNull(EnvelopeJson.parseEvent("not json"))
        assertNull(EnvelopeJson.parseEvent("{}"))
    }
}

