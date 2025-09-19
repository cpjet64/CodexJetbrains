package dev.curt.codexjb.proto

import com.google.gson.JsonObject

data class OverrideTurnContext(
    val cwd: String? = null,
    val model: String? = null,
    val effort: String? = null
) {
    fun toJson(): JsonObject = JsonObject().apply {
        cwd?.let { addProperty("cwd", it) }
        model?.let { addProperty("model", it) }
        effort?.let { addProperty("effort", it) }
    }
}

