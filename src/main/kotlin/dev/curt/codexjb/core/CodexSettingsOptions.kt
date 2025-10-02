package dev.curt.codexjb.core

import dev.curt.codexjb.proto.ApprovalMode

/** Centralized catalog of selectable options for Codex settings and UI controls. */
object CodexSettingsOptions {
    /** Default model identifiers shown in dropdowns when no user customization exists. */
    val MODELS: List<String> = listOf(
        "gpt-5-codex",
        "gpt-5",
    )

    /** Master reasoning level list used when a model does not provide a custom restriction. */
    val REASONING_LEVELS: List<String> = listOf("minimal", "low", "medium", "high")

    private val MODEL_REASONING: Map<String, List<String>> = mapOf(
        "gpt-5-codex" to listOf("low", "medium", "high"),
        "gpt-5" to listOf("minimal", "low", "medium", "high")
    )

    fun reasoningLevelsForModel(model: String?): List<String> =
        MODEL_REASONING[model] ?: REASONING_LEVELS

    val SANDBOX_POLICIES: List<SandboxOption> = listOf(
        SandboxOption("workspace-write", "Workspace Write(recommended)"),
        SandboxOption("read-only", "Read Only"),
        SandboxOption("danger-full-access", "Full Access (unsafe)")
    )

    val APPROVAL_LEVELS: List<ApprovalLevelOption> = listOf(
        ApprovalLevelOption(ApprovalMode.CHAT, "Read Only"),
        ApprovalLevelOption(ApprovalMode.AGENT, "Auto"),
        ApprovalLevelOption(ApprovalMode.FULL_ACCESS, "Full Access")
    )

    fun approvalOptionFor(modeName: String?): ApprovalLevelOption? =
        APPROVAL_LEVELS.firstOrNull { it.mode.name == modeName }

    data class SandboxOption(val id: String, val label: String)

    data class ApprovalLevelOption(val mode: ApprovalMode, val label: String)
}

