package dev.curt.codexjb.ui

import dev.curt.codexjb.core.ProcessHealth
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTextBuilderTest {
    @Test
    fun buildsBasicStatusText() {
        val state = CodexStatusBarController.State(
            model = "gpt-4.1-mini",
            effort = "high",
            health = ProcessHealth.Status.OK,
            tokensPerSecond = null
        )
        val text = StatusTextBuilder.build(state)
        assertEquals("Codex: gpt-4.1-mini \u2022 high \u2022 Healthy", text)
    }

    @Test
    fun includesTokensWhenAvailable() {
        val state = CodexStatusBarController.State(
            model = "o1",
            effort = "medium",
            health = ProcessHealth.Status.RESTARTING,
            tokensPerSecond = 12.34
        )
        val text = StatusTextBuilder.build(state)
        assertEquals("Codex: o1 \u2022 medium \u2022 Restarting \u2022 12.3 tok/s", text)
    }
}