package dev.curt.codexjb.proto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
        
        when (type) {
            "McpToolsList" -> {
                isServerAvailable = true
                errorMessageInternal = null
                val arr = msg.getAsJsonArray("tools") ?: JsonArray()
                tools = arr.mapNotNull {
                    it.asJsonObject.let { o ->
                        val n = o.get("name")?.asString
                        val d = o.get("description")?.asString ?: ""
                        if (n != null) {
                            TelemetryService.recordMcpToolInvocation()
                            McpTool(n, d)
                        } else null
                    }
                }
            }
            "McpToolsError" -> {
                isServerAvailable = false
                errorMessageInternal = msg.get("message")?.asString ?: "MCP server unavailable"
                tools = emptyList()
                TelemetryService.recordMcpToolFailure()
            }
            "McpServerUnavailable" -> {
                isServerAvailable = false
                errorMessageInternal = "MCP server is not running or not configured"
                tools = emptyList()
                TelemetryService.recordMcpToolFailure()
            }
        }
    }
    
    fun hasError(): Boolean = !isServerAvailable || errorMessageInternal != null
    
    fun getErrorMessage(): String? = errorMessageInternal
}

