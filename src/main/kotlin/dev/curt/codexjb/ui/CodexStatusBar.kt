package dev.curt.codexjb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import dev.curt.codexjb.core.ProcessHealth
import java.awt.Component
import java.awt.event.MouseEvent

class CodexStatusBarFactory : StatusBarWidgetFactory {
    override fun getId(): String = "codex.session.status"
    override fun getDisplayName(): String = "Codex Session"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = CodexStatusBarWidget()
    override fun disposeWidget(widget: StatusBarWidget) {}
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class CodexStatusBarWidget : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private var state: CodexStatusBarController.State = CodexStatusBarController.State()

    override fun ID(): String = "codex.session.status.widget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        CodexStatusBarController.register(this)
    }

    override fun dispose() {
        CodexStatusBarController.unregister(this)
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getText(): String = StatusTextBuilder.build(state)
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
    override fun getTooltipText(): String = "Codex session settings"

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { }

    internal fun applyState(state: CodexStatusBarController.State) {
        this.state = state
        statusBar?.updateWidget(ID())
    }
}

object CodexStatusBarController {
    private val lock = Any()
    private val widgets = mutableSetOf<CodexStatusBarWidget>()
    private var state = State()

    data class State(
        val model: String = "auto",
        val effort: String = "medium",
        val health: ProcessHealth.Status = ProcessHealth.Status.OK,
        val tokensPerSecond: Double? = null
    )

    fun updateSession(model: String, effort: String) = updateState { copy(model = model, effort = effort) }

    fun updateHealth(status: ProcessHealth.Status) = updateState { copy(health = status) }

    fun updateTokens(tokensPerSecond: Double?) = updateState { copy(tokensPerSecond = tokensPerSecond) }

    private fun updateState(transform: State.() -> State) {
        val snapshotWidgets: List<CodexStatusBarWidget>
        val newState: State
        synchronized(lock) {
            state = transform(state)
            newState = state
            snapshotWidgets = widgets.toList()
        }
        snapshotWidgets.forEach { it.applyState(newState) }
    }

    fun register(widget: CodexStatusBarWidget) {
        synchronized(lock) {
            widgets += widget
            widget.applyState(state)
        }
    }

    fun unregister(widget: CodexStatusBarWidget) {
        synchronized(lock) {
            widgets -= widget
        }
    }
}

object StatusTextBuilder {
    private const val SEPARATOR = " \u2022 "

    private fun ProcessHealth.Status.label(): String = when (this) {
        ProcessHealth.Status.OK -> "Healthy"
        ProcessHealth.Status.RESTARTING -> "Restarting"
        ProcessHealth.Status.STALE -> "Stale"
        ProcessHealth.Status.ERROR -> "Error"
    }

    fun build(state: CodexStatusBarController.State): String {
        val parts = mutableListOf("Codex: ${state.model}", state.effort)
        parts += state.health.label()
        state.tokensPerSecond?.let {
            parts += java.lang.String.format(java.util.Locale.US, "%.1f tok/s", it)
        }
        return parts.joinToString(SEPARATOR)
    }
}
