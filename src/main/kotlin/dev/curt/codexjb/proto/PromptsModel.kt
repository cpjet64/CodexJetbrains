package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.curt.codexjb.core.TelemetryService

data class Prompt(val name: String, val description: String, val content: String)

class PromptsModel {
    @Volatile var prompts: List<Prompt> = emptyList()
        private set

    fun onEvent(id: String, msg: JsonObject) {
        if (msg.get("type")?.asString != "PromptsList") return
        val arr = msg.getAsJsonArray("prompts") ?: JsonArray()
        prompts = arr.mapNotNull {
            it.asJsonObject.let { o ->
                val name = o.get("name")?.asString
                val description = o.get("description")?.asString ?: ""
                val content = o.get("content")?.asString ?: ""
                if (name != null) {
                    TelemetryService.recordMcpToolInvocation() // Track prompt usage
                    Prompt(name, description, content)
                } else null
            }
        }
    }
}
