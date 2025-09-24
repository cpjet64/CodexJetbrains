package dev.curt.codexjb.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
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
        var autoOpenChangedFiles: Boolean = false,
        var autoStageAppliedChanges: Boolean = false,
        var autoOpenConsoleOnExec: Boolean = false,
        var consoleVisible: Boolean = false
    )

    private var state = State()

    var cliPath: Path?
        get() = state.cliPathStr?.let { Path.of(it) }
        set(value) { state.cliPathStr = value?.toString() }

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

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }
}
