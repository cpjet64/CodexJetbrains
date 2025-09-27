package dev.curt.codexjb.proto

import dev.curt.codexjb.core.LogSink
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeartbeatSchedulerTest {

    @Test
    fun doesNotSendBeforeInterval() {
        val clock = MutableClock()
        val recorder = HeartbeatRecordingLog()
        val sent = mutableListOf<String>()
        val scheduler = HeartbeatScheduler(
            sendHeartbeat = { sent += "ping" },
            log = recorder,
            interval = Duration.ofSeconds(10),
            clock = clock
        )

        scheduler.markActivity()
        clock.advance(Duration.ofSeconds(5))
        scheduler.runHeartbeatCheck()

        assertTrue(sent.isEmpty())
        scheduler.dispose()
    }

    @Test
    fun sendsPingAfterIdleWindow() {
        val clock = MutableClock()
        val recorder = HeartbeatRecordingLog()
        val sent = mutableListOf<String>()
        val scheduler = HeartbeatScheduler(
            sendHeartbeat = { sent += "ping" },
            log = recorder,
            interval = Duration.ofSeconds(10),
            clock = clock
        )

        scheduler.markActivity()
        clock.advance(Duration.ofSeconds(11))
        scheduler.runHeartbeatCheck()

        assertEquals(listOf("ping"), sent)
        scheduler.dispose()
    }

    @Test
    fun logsFailureWhenHeartbeatThrows() {
        val clock = MutableClock()
        val recorder = HeartbeatRecordingLog()
        val scheduler = HeartbeatScheduler(
            sendHeartbeat = { throw IllegalStateException("down") },
            log = recorder,
            interval = Duration.ofSeconds(1),
            clock = clock
        )

        scheduler.markActivity()
        clock.advance(Duration.ofSeconds(2))
        scheduler.runHeartbeatCheck()

        assertTrue(recorder.lines.any { it.startsWith("WARN:heartbeat send failed") })
        scheduler.dispose()
    }
}

private class HeartbeatRecordingLog : LogSink {
    val lines = mutableListOf<String>()
    override fun info(message: String) { lines += "INFO:$message" }
    override fun warn(message: String) { lines += "WARN:$message" }
    override fun error(message: String, t: Throwable?) { lines += "ERROR:$message" }
}

private class MutableClock : Clock() {
    private var current: Instant = Instant.EPOCH

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
