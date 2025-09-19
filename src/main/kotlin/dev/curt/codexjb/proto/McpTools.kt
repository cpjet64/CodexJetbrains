package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class McpTool(val name: String, val description: String)

class McpToolsModel {
    @Volatile var tools: List<McpTool> = emptyList()
        private set

    fun onEvent(id: String, msg: JsonObject) {
        if (msg.get("type")?.asString != "McpToolsList") return
        val arr = msg.getAsJsonArray("tools") ?: JsonArray()
        tools = arr.mapNotNull {
            it.asJsonObject.let { o ->
                val n = o.get("name")?.asString
                val d = o.get("description")?.asString ?: ""
                if (n != null) McpTool(n, d) else null
            }
        }
    }
}

