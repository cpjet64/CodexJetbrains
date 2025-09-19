package dev.curt.codexjb.proto

import com.google.gson.JsonObject

class Correlation(private val registry: TurnRegistry, private val bus: EventBus) {
    fun beginTurn(id: String) {
        registry.put(Turn(id))
    }

    fun wire(callback: (Turn, JsonObject) -> Unit) {
        bus.addListener("*") { id, msg ->
            val t = registry.get(id) ?: return@addListener
            callback(t, msg)
            registry.onEvent(id, msg)
        }
    }
}
