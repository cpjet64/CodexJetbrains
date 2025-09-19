package dev.curt.codexjb.proto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorrelationTest {
    @Test
    fun mapsEventsToTurnAndExpires() {
        val reg = TurnRegistry()
        val bus = EventBus()
        val corr = Correlation(reg, bus)
        val seen = mutableListOf<String>()
        corr.beginTurn("t1")
        corr.wire { turn, msg -> seen += "${turn.id}:${msg["type"].asString}" }

        val delta = "{" +
            "\"id\":\"t1\"," +
            "\"msg\":{\"type\":\"AgentMessageDelta\",\"delta\":\"hi\"}}"
        val done = "{" +
            "\"id\":\"t1\"," +
            "\"msg\":{\"type\":\"TaskComplete\"}}"
        bus.dispatch(delta)
        bus.dispatch(done)

        assertEquals(listOf("t1:AgentMessageDelta", "t1:TaskComplete"), seen)
        assertTrue(reg.size() == 0)
    }
}

