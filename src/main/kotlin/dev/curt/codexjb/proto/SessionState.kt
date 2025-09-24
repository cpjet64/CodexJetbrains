package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.core.TelemetryService

class SessionState(private val log: LogSink? = null) {
    @Volatile var model: String? = null
        private set
    @Volatile var effort: String? = null
        private set
    @Volatile var rolloutPath: String? = null
        private set

    fun onEvent(id: String, msg: JsonObject) {
        val type = msg.get("type")?.asString ?: return
        if (type != "SessionConfigured") return
        
        // Record session start with session ID
        TelemetryService.recordSessionStart(id)
        
        model = msg.get("model")?.asString ?: model
        effort = msg.get("effort")?.asString ?: effort
        val newRollout = msg.get("rollout_path")?.asString
        if (!newRollout.isNullOrBlank() && newRollout != rolloutPath) {
            rolloutPath = newRollout
            log?.info("Session rollout_path: $newRollout")
        }
    }
}
