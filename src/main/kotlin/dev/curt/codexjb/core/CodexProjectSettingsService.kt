package dev.curt.codexjb.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "CodexProjectSettingsService", storages = [Storage("codex-project-settings.xml")])
class CodexProjectSettingsService : PersistentStateComponent<CodexProjectSettingsService.State> {
    data class State(
        var cliPathOverride: String? = null,
        var useWslOverride: Boolean? = null,
        var openToolWindowOnStartup: Boolean? = null,
        var defaultModel: String? = null,
        var defaultEffort: String? = null,
        var defaultApprovalMode: String? = null,
        var defaultSandboxPolicy: String? = null
    )

    private var state = State()

    var cliPathOverride: String?
        get() = state.cliPathOverride
        set(value) { state.cliPathOverride = value }

    var useWslOverride: Boolean?
        get() = state.useWslOverride
        set(value) { state.useWslOverride = value }

    var openToolWindowOnStartup: Boolean?
        get() = state.openToolWindowOnStartup
        set(value) { state.openToolWindowOnStartup = value }

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

    fun clearOverrides() {
        state = State()
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }
}
