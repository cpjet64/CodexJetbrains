package dev.curt.codexjb.proto

import com.google.gson.JsonObject

data class OverrideTurnContext(
    val cwd: String? = null,
    val model: String? = null,
    val effort: String? = null,
    val sandboxPolicy: String? = null
) {
    fun toJson(): JsonObject = JsonObject().apply {
        cwd?.let { addProperty("cwd", it) }
        model?.let { addProperty("model", it) }
        effort?.let { addProperty("effort", it) }
        sandboxPolicy?.let { add("sandbox_policy", serializeSandbox(it)) }
    }

    private fun serializeSandbox(policy: String): com.google.gson.JsonElement {
        return when (policy) {
            "danger-full-access" -> com.google.gson.JsonPrimitive("danger-full-access")
            "read-only" -> com.google.gson.JsonPrimitive("read-only")
            "workspace-write" -> JsonObject().apply { add("workspace-write", JsonObject()) }
            else -> com.google.gson.JsonPrimitive(policy)
        }
    }
}
