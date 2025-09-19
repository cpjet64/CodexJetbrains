package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenUsageTest {
    @Test
    fun updatesUsageFromEvent() {
        val m = TokenUsageModel()
        val msg = JsonObject().apply {
            addProperty("type", "TokenCount")
            addProperty("input", 10)
            addProperty("output", 20)
            addProperty("total", 30)
        }
        m.onEvent("t1", msg)
        val u = m.last!!
        assertEquals(10, u.input)
        assertEquals(20, u.output)
        assertEquals(30, u.total)
    }
}

