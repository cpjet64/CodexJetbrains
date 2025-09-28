package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class Prompt(val name: String, val description: String, val content: String)

class PromptsModel {
    @Volatile var prompts: List<Prompt> = emptyList()
        private set

    fun onEvent(id: String, msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        if (type.lowercase() !in setOf("listcustompromptsresponse", "list_custom_prompts_response", "promptslist")) return
        val arr = when {
            msg.get("custom_prompts")?.isJsonArray == true -> msg.getAsJsonArray("custom_prompts")
            msg.get("prompts")?.isJsonArray == true -> msg.getAsJsonArray("prompts")
            else -> JsonArray()
        }
        prompts = arr.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = obj.get("name")?.asString ?: return@mapNotNull null
            val description = obj.get("description")?.asString ?: obj.get("summary")?.asString ?: ""
            val content = obj.get("content")?.asString ?: obj.get("prompt")?.asString ?: ""
            Prompt(name, description, content)
        }
    }
}
