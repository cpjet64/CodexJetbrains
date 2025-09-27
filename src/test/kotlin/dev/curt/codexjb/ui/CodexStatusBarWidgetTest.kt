package dev.curt.codexjb.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CodexStatusBarWidgetTest {

    @Test
    fun statusTextBuilderUsesBulletSeparator() {
        assertEquals("Codex: model \u2022 effort", StatusTextBuilder.build("model", "effort"))
    }

    @Test
    fun controllerUpdatesRegisteredWidget() {
        val widget = CodexStatusBarWidget()
        CodexStatusBarController.register(widget)
        try {
            assertEquals("Codex: auto \u2022 medium", widget.getText())
            CodexStatusBarController.update("gpt-4o-mini", "high")
            assertEquals("Codex: gpt-4o-mini \u2022 high", widget.getText())
        } finally {
            CodexStatusBarController.unregister(widget)
            CodexStatusBarController.update("auto", "medium")
        }
    }
}
