package dev.curt.codexjb.proto

import com.google.gson.JsonObject
import dev.curt.codexjb.core.LogSink

enum class ApprovalMode { CHAT, AGENT, FULL_ACCESS }

class ApprovalStore {
    private val execDecisions = mutableMapOf<String, Boolean>()
    private val patchDecisions = mutableMapOf<String, Boolean>()

    fun rememberExec(key: String, allow: Boolean) { execDecisions[key] = allow }
    fun rememberPatch(key: String, allow: Boolean) { patchDecisions[key] = allow }
    fun reset() { execDecisions.clear(); patchDecisions.clear() }
    fun lookupExec(key: String): Boolean? = execDecisions[key]
    fun lookupPatch(key: String): Boolean? = patchDecisions[key]
}

class ApprovalsController(
    private val modeProvider: () -> ApprovalMode,
    private val store: ApprovalStore,
    private val onDecision: (JsonObject) -> Unit,
    private val log: LogSink,
    private val prompt: (String, String) -> Boolean
) {
    fun wire(bus: EventBus) {
        bus.addListener("ExecApprovalRequest") { id, msg -> handleExec(id, msg) }
        bus.addListener("ApplyPatchApprovalRequest") { id, msg -> handlePatch(id, msg) }
    }

    private fun handleExec(id: String, msg: JsonObject) {
        val command = msg.get("command")?.asString ?: ""
        val cwd = msg.get("cwd")?.asString ?: ""
        val mode = modeProvider()
        val key = "$cwd::$command"
        val allow = when (mode) {
            ApprovalMode.FULL_ACCESS -> true
            else -> store.lookupExec(key) ?: prompt("Approve exec?", "$command\n@$cwd")
        }
        if (!allow) log.warn("Exec denied: $command")
        store.rememberExec(key, allow)
        onDecision(buildExecDecision(id, allow))
    }

    private fun handlePatch(id: String, msg: JsonObject) {
        val files = msg.get("files")?.asString ?: ""
        val mode = modeProvider()
        val key = files
        val allow = when (mode) {
            ApprovalMode.FULL_ACCESS -> true
            else -> store.lookupPatch(key) ?: prompt("Approve patch?", files)
        }
        if (!allow) log.warn("Patch denied: ${files.take(80)}")
        store.rememberPatch(key, allow)
        onDecision(buildPatchDecision(id, allow))
    }

    private fun buildExecDecision(id: String, allow: Boolean): JsonObject {
        val body = JsonObject().apply {
            addProperty("type", "ExecApprovalDecision")
            addProperty("allow", allow)
        }
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("op", "ApprovalDecision")
            add("body", body)
        }
    }

    private fun buildPatchDecision(id: String, allow: Boolean): JsonObject {
        val body = JsonObject().apply {
            addProperty("type", "PatchApprovalDecision")
            addProperty("allow", allow)
        }
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("op", "ApprovalDecision")
            add("body", body)
        }
    }
}

