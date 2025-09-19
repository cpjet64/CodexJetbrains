package dev.curt.codexjb.proto

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class SubmissionEnvelope(
    val id: String,
    val op: String,
    val body: JsonObject
)

data class EventEnvelope(
    val id: String,
    val msg: JsonObject
)

object EnvelopeJson {
    fun encodeSubmission(env: SubmissionEnvelope): String {
        val root = JsonObject()
        root.addProperty("id", env.id)
        root.addProperty("op", env.op)
        root.add("body", env.body)
        return root.toString()
    }

    fun parseEvent(line: String): EventEnvelope? {
        return try {
            val el = JsonParser.parseString(line)
            if (!el.isJsonObject) return null
            val obj = el.asJsonObject
            val id = obj["id"]?.asString ?: return null
            val msg = obj["msg"]?.asJsonObject ?: return null
            EventEnvelope(id, msg)
        } catch (_: Exception) {
            null
        }
    }
}
