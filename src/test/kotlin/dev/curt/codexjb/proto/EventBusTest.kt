package dev.curt.codexjb.proto

import kotlin.test.Test
import kotlin.test.assertEquals

class EventBusTest {
    @Test
    fun routesByTypeAndWildcard() {
        val bus = EventBus()
        val seen = mutableListOf<String>()
        val a = EventListener { id, msg -> seen += "A:${msg["type"].asString}:$id" }
        val any = EventListener { id, msg -> seen += "*:${msg["type"].asString}:$id" }
        bus.addListener("AgentMessageDelta", a)
        bus.addListener("*", any)

        val line = "{" +
            "\"id\":\"t1\"," +
            "\"msg\":{\"type\":\"AgentMessageDelta\",\"delta\":\"hi\"}}"
        bus.dispatch(line)

        assertEquals(listOf("A:AgentMessageDelta:t1", "*:AgentMessageDelta:t1"), seen)
    }
}

