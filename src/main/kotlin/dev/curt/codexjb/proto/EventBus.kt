package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

fun interface EventListener { fun onEvent(id: String, msg: JsonObject) }

class EventBus {
    private val map = ConcurrentHashMap<String, CopyOnWriteArrayList<EventListener>>()

    fun addListener(type: String, listener: EventListener) {
        map.computeIfAbsent(type) { CopyOnWriteArrayList() }.add(listener)
    }

    fun removeListener(type: String, listener: EventListener) {
        map[type]?.remove(listener)
    }

    fun dispatch(line: String) {
        val env = EnvelopeJson.parseEvent(line) ?: return
        val type = env.msg.get("type")?.asString ?: return
        notify(type, env)
        notify("*", env)
    }

    private fun notify(type: String, env: EventEnvelope) {
        val ls = map[type] ?: return
        for (l in ls) l.onEvent(env.id, env.msg)
    }
}

