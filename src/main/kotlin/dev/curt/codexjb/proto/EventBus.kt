package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import dev.curt.codexjb.core.DiagnosticsService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

fun interface EventListener { fun onEvent(id: String, msg: JsonObject) }

/**
 * EventBus for MCP protocol events.
 * Uses JSON-RPC notifications to dispatch events to listeners.
 */
class EventBus {
    private val map = ConcurrentHashMap<String, CopyOnWriteArrayList<EventListener>>()

    fun addListener(type: String, listener: EventListener) {
        map.computeIfAbsent(type) { CopyOnWriteArrayList() }.add(listener)
    }

    fun removeListener(type: String, listener: EventListener) {
        map[type]?.remove(listener)
    }

    /**
     * Dispatch an event from MCP protocol (JSON-RPC notifications).
     */
    fun dispatchEvent(id: String, msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        val listeners = map[type]
        if (listeners == null || listeners.isEmpty()) {
            val keys = try {
                msg.entrySet().joinToString(", ") { it.key }
            } catch (_: Exception) { "" }
            DiagnosticsService.append("DEBUG: Unhandled event type '$type' keys=[$keys]")
        }
        notify(type, id, msg)
        notify("*", id, msg)
    }

    /**
     * Compatibility method for old envelope format (used by tests).
     * Parses envelope JSON and delegates to dispatchEvent().
     */
    fun dispatch(line: String) {
        val env = parseEnvelope(line) ?: return
        dispatchEvent(env.id, env.msg)
    }

    private fun parseEnvelope(line: String): EventEnvelope? {
        return try {
            val el = com.google.gson.JsonParser.parseString(line)
            if (!el.isJsonObject) return null
            val obj = el.asJsonObject
            val id = obj["id"]?.asString ?: return null
            val msg = obj["msg"]?.asJsonObject ?: return null
            EventEnvelope(id, msg)
        } catch (_: Exception) {
            null
        }
    }

    private data class EventEnvelope(val id: String, val msg: JsonObject)

    private fun notify(type: String, id: String, msg: JsonObject) {
        val ls = map[type] ?: return
        for (l in ls) l.onEvent(id, msg)
    }
}
