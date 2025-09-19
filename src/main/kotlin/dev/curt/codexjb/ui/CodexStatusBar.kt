package dev.curt.codexjb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Component
import java.awt.event.MouseEvent
import com.intellij.util.Consumer
import javax.swing.JLabel

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
    override fun install(statusBar: StatusBar) { this.statusBar = statusBar }
    override fun dispose() { this.statusBar = null }
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getText(): String = StatusTextBuilder.build(model, effort)
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT
    override fun getTooltipText(): String = "Codex session settings"

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { }

    fun update(model: String, effort: String) {
        this.model = model
        this.effort = effort
        statusBar?.updateWidget(ID())
    }
}

object StatusTextBuilder {
    fun build(model: String, effort: String): String = "Codex: $model · $effort"
}
