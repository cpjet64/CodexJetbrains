package dev.curt.codexjb.proto

import com.google.gson.JsonObject

data class TokenUsage(
    var input: Int = 0,
    var output: Int = 0,
    var total: Int = 0
)

class TokenUsageModel {
    @Volatile var last: TokenUsage? = null
        private set

    fun onEvent(id: String, msg: JsonObject) {
        if (msg.get("type")?.asString != "TokenCount") return
        val u = TokenUsage(
            input = msg.get("input")?.asInt ?: 0,
            output = msg.get("output")?.asInt ?: 0,
            total = msg.get("total")?.asInt ?: 0
        )
        last = u
    }
}

