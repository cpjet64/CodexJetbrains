package dev.curt.codexjb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
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
    private var model: String = "auto"
    private var effort: String = "medium"

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
    override fun getText(): String = StatusTextBuilder.build(model, effort)
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
    override fun getTooltipText(): String = "Codex session settings"

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { }

    internal fun applyState(model: String, effort: String) {
        this.model = model
        this.effort = effort
        statusBar?.updateWidget(ID())
    }
}

object CodexStatusBarController {
    private val lock = Any()
    private val widgets = mutableSetOf<CodexStatusBarWidget>()
    private var currentModel: String = "auto"
    private var currentEffort: String = "medium"

    fun update(model: String, effort: String) {
        val snapshot: List<CodexStatusBarWidget>
        synchronized(lock) {
            currentModel = model
            currentEffort = effort
            snapshot = widgets.toList()
        }
        snapshot.forEach { it.applyState(model, effort) }
    }

    fun register(widget: CodexStatusBarWidget) {
        synchronized(lock) {
            widgets += widget
            widget.applyState(currentModel, currentEffort)
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

    fun build(model: String, effort: String): String = "Codex: $model$SEPARATOR$effort"
}
