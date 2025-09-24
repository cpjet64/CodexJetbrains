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
    private val prompt: (String, String) -> Boolean,
    private val promptRationale: (String, String) -> String? = { _, _ -> null }
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
        val lookedUp = store.lookupExec(key)
        val (allow, source) = when (mode) {
            ApprovalMode.FULL_ACCESS -> true to "FULL_ACCESS"
            else -> when (lookedUp) {
                null -> (prompt("Approve exec?", "$command\n@$cwd") to "PROMPT")
                else -> (lookedUp to "REMEMBERED")
            }
        }
        if (!allow) {
            val code = if (source == "REMEMBERED") "REMEMBERED_DENY" else "USER_DENY"
            log.warn("Exec denied [$code]: $command")
        }
        store.rememberExec(key, allow)
        onDecision(buildExecDecision(id, allow))
    }

    private fun handlePatch(id: String, msg: JsonObject) {
        val files = msg.get("files")?.asString ?: ""
        val mode = modeProvider()
        val key = files
        val lookedUp = store.lookupPatch(key)
        val (allow, source) = when (mode) {
            ApprovalMode.FULL_ACCESS -> true to "FULL_ACCESS"
            else -> when (lookedUp) {
                null -> (prompt("Approve patch?", files) to "PROMPT")
                else -> (lookedUp to "REMEMBERED")
            }
        }
        var rationale = when (source) {
            "FULL_ACCESS" -> "Auto-approved by FULL_ACCESS mode"
            "REMEMBERED" -> if (allow) "Approved by remembered decision" else "Denied by remembered decision"
            else -> if (allow) "Approved by user" else "Denied by user"
        }
        if (!allow) {
            val extra = promptRationale("Add rationale (optional)", files)?.trim()
            if (!extra.isNullOrEmpty()) rationale = extra
            val code = if (source == "REMEMBERED") "REMEMBERED_DENY" else "USER_DENY"
            log.warn("Patch denied [$code]: ${files.take(80)}")
        }
        store.rememberPatch(key, allow)
        onDecision(buildPatchDecision(id, allow, rationale))
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

    private fun buildPatchDecision(id: String, allow: Boolean, rationale: String?): JsonObject {
        val body = JsonObject().apply {
            addProperty("type", "PatchApprovalDecision")
            addProperty("allow", allow)
            if (rationale != null) addProperty("rationale", rationale)
        }
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("op", "ApprovalDecision")
            add("body", body)
        }
    }
}
