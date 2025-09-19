package dev.curt.codexjb.ui

import com.google.gson.JsonObject

class ErrorModel {
    @Volatile var lastError: String? = null
        private set

    fun onEvent(id: String, msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        if (type == "StreamError") {
            val m = msg.get("message")?.asString
            if (!m.isNullOrBlank()) lastError = m
        }
    }
}

