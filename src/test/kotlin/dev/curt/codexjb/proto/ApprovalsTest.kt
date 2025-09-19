package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import dev.curt.codexjb.core.LogSink
import kotlin.test.Test
import kotlin.test.assertEquals

class ApprovalsTest {
    @Test
    fun handlesExecRequestWithPrompt() {
        val bus = EventBus()
        val store = ApprovalStore()
        val decisions = mutableListOf<JsonObject>()
        val ctrl = ApprovalsController(
            modeProvider = { ApprovalMode.AGENT },
            store = store,
            onDecision = { decisions += it },
            log = object : LogSink { override fun info(message: String) {}; override fun warn(message: String) {}; override fun error(message: String, t: Throwable?) {} },
            prompt = { _, _ -> true }
        )
        ctrl.wire(bus)
        val line = "{" +
            "\"id\":\"a1\"," +
            "\"msg\":{\"type\":\"ExecApprovalRequest\",\"command\":\"echo hi\",\"cwd\":\"/tmp\"}}"
        bus.dispatch(line)
        assertEquals(1, decisions.size)
        assertEquals("ApprovalDecision", decisions.first()["op"].asString)
    }

    @Test
    fun autoApprovesInFullAccess() {
        val bus = EventBus()
        val store = ApprovalStore()
        val decisions = mutableListOf<JsonObject>()
        val ctrl = ApprovalsController(
            modeProvider = { ApprovalMode.FULL_ACCESS },
            store = store,
            onDecision = { decisions += it },
            log = object : LogSink { override fun info(message: String) {}; override fun warn(message: String) {}; override fun error(message: String, t: Throwable?) {} },
            prompt = { _, _ -> false }
        )
        ctrl.wire(bus)
        val line = "{" +
            "\"id\":\"p1\"," +
            "\"msg\":{\"type\":\"ApplyPatchApprovalRequest\",\"files\":\"a.kt,b.kt\"}}"
        bus.dispatch(line)
        assertEquals(1, decisions.size)
    }
}

