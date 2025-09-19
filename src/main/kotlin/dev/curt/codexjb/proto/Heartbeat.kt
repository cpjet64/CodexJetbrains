package dev.curt.codexjb.proto

import com.google.gson.JsonObject

object Heartbeat {
    fun buildPingSubmission(id: String = Ids.newId()): String {
        val body = JsonObject()
        body.addProperty("type", "Ping")
        val env = SubmissionEnvelope(id = id, op = "Submit", body = body)
        return EnvelopeJson.encodeSubmission(env)
    }
}

