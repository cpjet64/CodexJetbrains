package dev.curt.codexjb.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexLoggerTest {
    @Test
    fun forwardsMessagesToInstalledProvider() {
        val rec = RecordingLogSink()
        CodexLogger.installProvider { rec }

        val log = CodexLogger.forClass(CodexLoggerTest::class.java)
        log.info("hello")
        log.warn("careful")
        log.error("boom", null)

        assertEquals(listOf(
            "INFO:hello",
            "WARN:careful",
            "ERROR:boom"
        ), rec.lines)
    }
}

private class RecordingLogSink : LogSink {
    val lines = mutableListOf<String>()
    override fun info(message: String) { lines += "INFO:$message" }
    override fun warn(message: String) { lines += "WARN:$message" }
    override fun error(message: String, t: Throwable?) { lines += "ERROR:$message" }
}

