package dev.curt.codexjb.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import java.nio.file.Path

@Service
@State(name = "CodexConfigService", storages = [Storage("codex-config.xml")])
class CodexConfigService : PersistentStateComponent<CodexConfigService.State> {
    data class State(
        var cliPathStr: String? = null,
        var lastModel: String? = null,
        var lastEffort: String? = null,
        var lastApprovalMode: String? = null,
        var lastSandboxPolicy: String? = null,
        var defaultModel: String? = null,
        var defaultEffort: String? = null,
        var defaultApprovalMode: String? = null,
        var defaultSandboxPolicy: String? = null,
        var useWsl: Boolean = false,
        var openToolWindowOnStartup: Boolean = false,
        var autoOpenChangedFiles: Boolean = false,
        var autoStageAppliedChanges: Boolean = false,
        var autoOpenConsoleOnExec: Boolean = false,
        var consoleVisible: Boolean = false,
        var showReasoning: Boolean = true,
        var lastUsedTool: String? = null,
        var lastUsedPrompt: String? = null,
        var customModels: MutableList<String> = mutableListOf()
    )

    private var state = State()

    var cliPath: Path?
        get() = state.cliPathStr?.let { Path.of(it) }
        set(value) { state.cliPathStr = value?.toString() }

    var availableModels: List<String>
        get() = if (state.customModels.isNotEmpty()) state.customModels.toList() else CodexSettingsOptions.MODELS
        set(value) {
            val sanitized = value.map(String::trim).filter { it.isNotEmpty() }.distinct()
            state.customModels.clear()
            state.customModels.addAll(sanitized)
            val valid = availableModels
            if (state.defaultModel != null && state.defaultModel !in valid) {
                state.defaultModel = valid.firstOrNull()
            }
            if (state.lastModel != null && state.lastModel !in valid) {
                state.lastModel = valid.firstOrNull()
            }
        }

    var defaultArgs: List<String> = listOf("proto")

    var lastModel: String?
        get() = state.lastModel
        set(value) { state.lastModel = value }

    var lastEffort: String?
        get() = state.lastEffort
        set(value) { state.lastEffort = value }

    var lastApprovalMode: String?
        get() = state.lastApprovalMode
        set(value) { state.lastApprovalMode = value }

    var lastSandboxPolicy: String?
        get() = state.lastSandboxPolicy
        set(value) { state.lastSandboxPolicy = value }

    var defaultModel: String?
        get() = state.defaultModel
        set(value) { state.defaultModel = value }

    var defaultEffort: String?
        get() = state.defaultEffort
        set(value) { state.defaultEffort = value }

    var defaultApprovalMode: String?
        get() = state.defaultApprovalMode
        set(value) { state.defaultApprovalMode = value }

    var defaultSandboxPolicy: String?
        get() = state.defaultSandboxPolicy
        set(value) { state.defaultSandboxPolicy = value }

    var useWsl: Boolean
        get() = state.useWsl
        set(value) { state.useWsl = value }

    var openToolWindowOnStartup: Boolean
        get() = state.openToolWindowOnStartup
        set(value) { state.openToolWindowOnStartup = value }

    var autoOpenChangedFiles: Boolean
        get() = state.autoOpenChangedFiles
        set(value) { state.autoOpenChangedFiles = value }

    var autoStageAppliedChanges: Boolean
        get() = state.autoStageAppliedChanges
        set(value) { state.autoStageAppliedChanges = value }

    var autoOpenConsoleOnExec: Boolean
        get() = state.autoOpenConsoleOnExec
        set(value) { state.autoOpenConsoleOnExec = value }

    var consoleVisible: Boolean
        get() = state.consoleVisible
        set(value) { state.consoleVisible = value }

    var showReasoning: Boolean
        get() = state.showReasoning
        set(value) { state.showReasoning = value }

    var lastUsedTool: String?
        get() = state.lastUsedTool
        set(value) { state.lastUsedTool = value }

    var lastUsedPrompt: String?
        get() = state.lastUsedPrompt
        set(value) { state.lastUsedPrompt = value }

    @Volatile
    private var discoverer: (Path?) -> Path? = { wd ->
        CodexPathDiscovery.discover(
            os = CodexPathDiscovery.currentOs(),
            env = System.getenv(),
            workingDirectory = wd
        )
    }

    fun installDiscoverer(fn: (Path?) -> Path?) { // for tests
        discoverer = fn
    }

    fun resolveExecutable(workingDirectory: Path?): Path? {
        return cliPath ?: discoverer(workingDirectory)
    }

    fun effectiveSettings(project: Project?): EffectiveSettings {
        val projectSettings = project?.getService(CodexProjectSettingsService::class.java)
        return EffectiveSettings(
            cliPath = projectSettings?.cliPathOverride?.let(Path::of) ?: cliPath,
            useWsl = projectSettings?.useWslOverride ?: useWsl,
            openToolWindowOnStartup = projectSettings?.openToolWindowOnStartup ?: openToolWindowOnStartup,
            defaultModel = projectSettings?.defaultModel ?: defaultModel,
            defaultEffort = projectSettings?.defaultEffort ?: defaultEffort,
            defaultApprovalMode = projectSettings?.defaultApprovalMode ?: defaultApprovalMode,
            defaultSandboxPolicy = projectSettings?.defaultSandboxPolicy ?: defaultSandboxPolicy
        )
    }

    fun exportToJson(includeProjectOverrides: Boolean = true): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val root = JsonObject().apply {
            addProperty("cli_path", state.cliPathStr)
            addProperty("use_wsl", state.useWsl)
            addProperty("open_tool_window_on_startup", state.openToolWindowOnStartup)
            addProperty("default_model", state.defaultModel)
            addProperty("default_effort", state.defaultEffort)
            addProperty("default_approval_mode", state.defaultApprovalMode)
            addProperty("default_sandbox_policy", state.defaultSandboxPolicy)
            val modelsArray = JsonArray().apply { availableModels.forEach { add(it) } }
            add("available_models", modelsArray)
        }
        if (includeProjectOverrides) {
            val projectOverrides = JsonObject()
            root.add("projects", projectOverrides)
        }
        return gson.toJson(root)
    }

    fun importFromJson(json: String) {
        val obj = GsonBuilder().create().fromJson(json, JsonObject::class.java) ?: return
        state.cliPathStr = obj.get("cli_path")?.takeIf { !it.isJsonNull }?.asString
        state.useWsl = obj.get("use_wsl")?.takeIf { !it.isJsonNull }?.asBoolean ?: state.useWsl
        state.openToolWindowOnStartup = obj.get("open_tool_window_on_startup")?.takeIf { !it.isJsonNull }?.asBoolean
            ?: state.openToolWindowOnStartup
        state.defaultModel = obj.get("default_model")?.takeIf { !it.isJsonNull }?.asString
        state.defaultEffort = obj.get("default_effort")?.takeIf { !it.isJsonNull }?.asString
        state.defaultApprovalMode = obj.get("default_approval_mode")?.takeIf { !it.isJsonNull }?.asString
        state.defaultSandboxPolicy = obj.get("default_sandbox_policy")?.takeIf { !it.isJsonNull }?.asString
        obj.getAsJsonArray("available_models")?.let { array ->
            availableModels = array.mapNotNull { element ->
                if (element.isJsonNull) null else element.asString
            }
        }
    }

    fun resetToDefaults() {
        state = State()
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }
}

data class EffectiveSettings(
    val cliPath: Path?,
    val useWsl: Boolean,
    val openToolWindowOnStartup: Boolean,
    val defaultModel: String?,
    val defaultEffort: String?,
    val defaultApprovalMode: String?,
    val defaultSandboxPolicy: String?
)

