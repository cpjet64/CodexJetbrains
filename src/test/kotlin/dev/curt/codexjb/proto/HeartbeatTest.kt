package dev.curt.codexjb.proto

import kotlin.test.Test
import kotlin.test.assertTrue

class HeartbeatTest {
    @Test
    fun buildsPingSubmission() {
        val s = Heartbeat.buildPingSubmission("h1")
        assertTrue(s.contains("\"id\":\"h1\""))
        assertTrue(s.contains("\"op\":\"Submit\""))
        assertTrue(s.contains("\"type\":\"Ping\""))
    }
}

