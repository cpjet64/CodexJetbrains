package dev.curt.codexjb.proto

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * JSON-RPC message types for App Server protocol.
 * Note: App Server does NOT include "jsonrpc": "2.0" field.
 */

/** Request ID can be number or string */
typealias RequestId = String

/** JSON-RPC Request (client → server) */
data class JsonRpcRequest(
    val id: RequestId,
    val method: String,
    val params: JsonObject? = null
)

/** JSON-RPC Response (server → client) */
data class JsonRpcResponse(
    val id: RequestId,
    val result: JsonElement
)

/** JSON-RPC Error (server → client) */
data class JsonRpcError(
    val id: RequestId,
    val error: ErrorObject
) {
    data class ErrorObject(
        val code: Int,
        val message: String,
        val data: JsonElement? = null
    )
}

/** JSON-RPC Notification (bidirectional, no response expected) */
data class JsonRpcNotification(
    val method: String,
    val params: JsonObject? = null
)

/** Union type for all JSON-RPC messages */
sealed class JsonRpcMessage {
    data class Request(val value: JsonRpcRequest) : JsonRpcMessage()
    data class Response(val value: JsonRpcResponse) : JsonRpcMessage()
    data class Error(val value: JsonRpcError) : JsonRpcMessage()
    data class Notification(val value: JsonRpcNotification) : JsonRpcMessage()
}

object JsonRpcParser {
    /**
     * Parse a line of JSON into a JSON-RPC message.
     * Returns null if the line is not valid JSON or not a valid message.
     */
    fun parse(line: String): JsonRpcMessage? {
        return try {
            val el = JsonParser.parseString(line)
            if (!el.isJsonObject) return null
            val obj = el.asJsonObject

            // Check for response (has "result" and "id")
            if (obj.has("result") && obj.has("id")) {
                val id = obj["id"].asString
                val result = obj["result"]
                return JsonRpcMessage.Response(JsonRpcResponse(id, result))
            }

            // Check for error (has "error" and "id")
            if (obj.has("error") && obj.has("id")) {
                val id = obj["id"].asString
                val errorObj = obj["error"].asJsonObject
                val code = errorObj["code"].asInt
                val message = errorObj["message"].asString
                val data = errorObj["data"]
                return JsonRpcMessage.Error(
                    JsonRpcError(
                        id,
                        JsonRpcError.ErrorObject(code, message, data)
                    )
                )
            }

            // Check for request (has "method" and "id")
            if (obj.has("method") && obj.has("id")) {
                val id = obj["id"].asString
                val method = obj["method"].asString
                val params = obj["params"]?.asJsonObject
                return JsonRpcMessage.Request(JsonRpcRequest(id, method, params))
            }

            // Check for notification (has "method" but no "id")
            if (obj.has("method")) {
                val method = obj["method"].asString
                val params = obj["params"]?.asJsonObject
                return JsonRpcMessage.Notification(JsonRpcNotification(method, params))
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encode a JSON-RPC request to a JSON string.
     */
    fun encodeRequest(req: JsonRpcRequest): String {
        val obj = JsonObject()
        obj.addProperty("id", req.id)
        obj.addProperty("method", req.method)
        if (req.params != null) {
            obj.add("params", req.params)
        }
        return obj.toString()
    }

    /**
     * Encode a JSON-RPC notification to a JSON string.
     */
    fun encodeNotification(note: JsonRpcNotification): String {
        val obj = JsonObject()
        obj.addProperty("method", note.method)
        if (note.params != null) {
            obj.add("params", note.params)
        }
        return obj.toString()
    }

    /**
     * Encode a JSON-RPC response to a JSON string.
     */
    fun encodeResponse(resp: JsonRpcResponse): String {
        val obj = JsonObject()
        obj.addProperty("id", resp.id)
        obj.add("result", resp.result)
        return obj.toString()
    }

    /**
     * Encode a JSON-RPC error to a JSON string.
     */
    fun encodeError(err: JsonRpcError): String {
        val obj = JsonObject()
        obj.addProperty("id", err.id)
        val errorObj = JsonObject()
        errorObj.addProperty("code", err.error.code)
        errorObj.addProperty("message", err.error.message)
        if (err.error.data != null) {
            errorObj.add("data", err.error.data)
        }
        obj.add("error", errorObj)
        return obj.toString()
    }
}
