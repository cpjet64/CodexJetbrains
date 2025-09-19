package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap

data class Turn(val id: String, val createdAtMillis: Long = System.currentTimeMillis())

class TurnRegistry {
    private val map = ConcurrentHashMap<String, Turn>()

    fun put(turn: Turn) { map[turn.id] = turn }
    fun get(id: String): Turn? = map[id]
    fun remove(id: String) { map.remove(id) }
    fun size(): Int = map.size

    fun onEvent(id: String, msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        if (type == "TaskComplete") map.remove(id)
    }
}

