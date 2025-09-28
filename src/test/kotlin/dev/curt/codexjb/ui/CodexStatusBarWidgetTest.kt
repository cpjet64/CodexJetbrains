package dev.curt.codexjb.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexStatusBarWidgetTest {

    @Test
    fun statusTextBuilderUsesBulletSeparator() {
        val state = CodexStatusBarController.State(model = "model", effort = "effort")
        assertEquals("Codex: model \u2022 effort \u2022 Healthy", StatusTextBuilder.build(state))
    }

    @Test
    fun controllerUpdatesRegisteredWidget() {
        val widget = CodexStatusBarWidget()
        CodexStatusBarController.register(widget)
        try {
            assertEquals("Codex: auto \u2022 medium \u2022 Healthy", widget.getText())
            CodexStatusBarController.updateSession("gpt-4o-mini", "high")
            assertEquals("Codex: gpt-4o-mini \u2022 high \u2022 Healthy", widget.getText())
        } finally {
            CodexStatusBarController.unregister(widget)
            CodexStatusBarController.updateSession("auto", "medium")
        }
    }
}
