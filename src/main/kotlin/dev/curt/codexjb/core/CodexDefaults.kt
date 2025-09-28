package dev.curt.codexjb.core

object CodexDefaults {
    val MODELS: List<String> = listOf("gpt-4.1-mini", "gpt-4o-mini")
    val EFFORTS: List<String> = listOf("low", "medium", "high")
    val SANDBOX_POLICIES: List<SandboxOption> = listOf(
        SandboxOption("workspace-write", "Workspace (recommended)"),
        SandboxOption("read-only", "Read Only"),
        SandboxOption("danger-full-access", "Full Access (unsafe)")
    )

    data class SandboxOption(val id: String, val label: String)
}
