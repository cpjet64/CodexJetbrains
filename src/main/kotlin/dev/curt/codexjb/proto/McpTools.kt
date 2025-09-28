package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import dev.curt.codexjb.core.TelemetryService

data class McpTool(val name: String, val description: String)

class McpToolsModel {
    @Volatile var tools: List<McpTool> = emptyList()
        private set
    @Volatile private var errorMessageInternal: String? = null
    @Volatile var isServerAvailable: Boolean = true
        private set

    fun onEvent(id: String, msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        when (type.lowercase()) {
            "mcplisttoolsresponse", "mcp_list_tools_response", "mcptoolslist" -> handleToolsList(msg)
            "mcptoolserror", "mcp_tools_error" -> handleError(msg.get("message")?.asString)
            "mcpserverunavailable", "mcp_server_unavailable" -> handleError("MCP server is not running or not configured")
        }
    }

    fun hasError(): Boolean = !isServerAvailable || errorMessageInternal != null
    
    fun getErrorMessage(): String? = errorMessageInternal

    private fun handleToolsList(msg: JsonObject) {
        isServerAvailable = true
        errorMessageInternal = null
        val toolsJson = msg.get("tools")
        tools = when {
            toolsJson == null -> emptyList()
            toolsJson.isJsonArray -> parseToolsArray(toolsJson.asJsonArray)
            toolsJson.isJsonObject -> parseToolsMap(toolsJson.asJsonObject)
            else -> emptyList()
        }
    }

    private fun handleError(message: String?) {
        isServerAvailable = false
        errorMessageInternal = message ?: "MCP server unavailable"
        tools = emptyList()
        TelemetryService.recordMcpToolFailure()
    }

    private fun parseToolsArray(arr: JsonArray): List<McpTool> = arr.mapNotNull { element ->
        val obj = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapNotNull null
        val name = obj.get("name")?.asString ?: return@mapNotNull null
        val description = obj.get("description")?.asString ?: ""
        McpTool(name, description)
    }

    private fun parseToolsMap(obj: JsonObject): List<McpTool> = obj.entrySet().mapNotNull { (key, value) ->
        val toolObj = value.takeIf { it.isJsonObject }?.asJsonObject
        val name = toolObj?.get("name")?.asString ?: key
        val description = toolObj?.get("description")?.asString
            ?: toolObj?.get("summary")?.asString
            ?: ""
        if (name.isBlank()) null else McpTool(name, description)
    }
}
