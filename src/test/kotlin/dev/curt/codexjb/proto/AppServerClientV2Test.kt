package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedWriter
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppServerClientV2Test {
    @Test
    fun mapsTurnStartedNotificationToTaskStarted() {
        val (client, _, bus) = createClient()
        var seenId: String? = null
        var seenType: String? = null

        bus.addListener("task_started") { id, msg ->
            seenId = id
            seenType = msg["type"]?.asString
        }

        val params = JsonObject().apply {
            add("turn", JsonObject().apply { addProperty("id", "turn-1") })
        }
        client.processMessage(JsonRpcMessage.Notification(JsonRpcNotification("turn/started", params)))

        assertEquals("turn-1", seenId)
        assertEquals("task_started", seenType)
    }

    @Test
    fun mapsAgentMessageDeltaNotificationToLegacyDeltaEvent() {
        val (client, _, bus) = createClient()
        var seenDelta: String? = null
        var seenType: String? = null

        bus.addListener("AgentMessageDelta") { _, msg ->
            seenType = msg["type"]?.asString
            seenDelta = msg["delta"]?.asString
        }

        val params = JsonObject().apply {
            addProperty("turnId", "turn-2")
            addProperty("delta", "hello")
        }
        client.processMessage(JsonRpcMessage.Notification(JsonRpcNotification("item/agentMessage/delta", params)))

        assertEquals("AgentMessageDelta", seenType)
        assertEquals("hello", seenDelta)
    }

    @Test
    fun mapsCompletedAgentMessageItemToLegacyAgentMessageEvent() {
        val (client, _, bus) = createClient()
        var fired = false

        bus.addListener("AgentMessage") { _, _ -> fired = true }

        val params = JsonObject().apply {
            addProperty("turnId", "turn-3")
            add("item", JsonObject().apply {
                addProperty("type", "agentMessage")
                addProperty("text", "done")
            })
        }
        client.processMessage(JsonRpcMessage.Notification(JsonRpcNotification("item/completed", params)))

        assertTrue(fired)
    }

    @Test
    fun respondsToV2CommandApprovalWithV2DecisionShape() {
        val (client, sink, _) = createClient()
        client.execApprovalHandler = { ApprovalDecision.APPROVED_FOR_SESSION }

        val params = JsonObject().apply {
            addProperty("threadId", "thr-1")
            addProperty("itemId", "item-1")
            add("command", JsonArray().apply { add("git"); add("status") })
            addProperty("cwd", "/work")
            addProperty("reason", "Need status")
        }
        client.processMessage(
            JsonRpcMessage.Request(
                JsonRpcRequest(
                    id = "99",
                    method = "item/commandExecution/requestApproval",
                    params = params
                )
            )
        )

        val line = waitForLine(sink)
        assertNotNull(line)
        val json = JsonParser.parseString(line).asJsonObject
        assertEquals("99", json["id"].asString)
        assertEquals("acceptForSession", json["result"].asJsonObject["decision"].asString)
    }

    private fun createClient(): Triple<AppServerClient, StringWriter, EventBus> {
        val sink = StringWriter()
        val bus = EventBus()
        val client = AppServerClient(BufferedWriter(sink), bus)
        return Triple(client, sink, bus)
    }

    private fun waitForLine(sink: StringWriter, timeoutMs: Long = 2_000): String? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val line = sink.toString().lineSequence().firstOrNull { it.isNotBlank() }
            if (line != null) return line
            Thread.sleep(10)
        }
        return null
    }
}
