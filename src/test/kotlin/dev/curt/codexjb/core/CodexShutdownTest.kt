package dev.curt.codexjb.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexShutdownTest {
    @Test
    fun stopsServiceOnProjectClosed() {
        val recorder = RecordingStopper()
        val shutdown = CodexShutdown(recorder)

        shutdown.onProjectClosed()

        assertEquals(1, recorder.stopCalls)
    }
}

private class RecordingStopper : ProcessStopper {
    var stopCalls = 0
        private set

    override fun stop() {
        stopCalls += 1
    }
}
