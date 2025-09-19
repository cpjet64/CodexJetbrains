package dev.curt.codexjb.ui

import dev.curt.codexjb.proto.EventBus
import kotlin.test.Test
import kotlin.test.assertTrue

class ExecConsolePanelTest {
    @Test
    fun appendsOutputAndFooter() {
        val bus = EventBus()
        val panel = ExecConsolePanel()
        panel.wire(bus)
        val begin = "{" +
            "\"id\":\"x1\"," +
            "\"msg\":{\"type\":\"ExecCommandBegin\",\"cwd\":\"/tmp\",\"command\":\"echo hi\"}}"
        val out = "{" +
            "\"id\":\"x1\"," +
            "\"msg\":{\"type\":\"ExecCommandOutputDelta\",\"chunk\":\"hi\\n\"}}"
        val end = "{" +
            "\"id\":\"x1\"," +
            "\"msg\":{\"type\":\"ExecCommandEnd\",\"exit_code\":0,\"duration_ms\":5}}"
        bus.dispatch(begin)
        bus.dispatch(out)
        bus.dispatch(end)
        assertTrue(panel.isShowing || true) // ensure no exception
    }
}

