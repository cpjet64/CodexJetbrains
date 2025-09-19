package dev.curt.codexjb.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StatusTextBuilderTest {
    @Test
    fun buildsStatusText() {
        val s = StatusTextBuilder.build("gpt-4.1-mini", "high")
        assertEquals("Codex: gpt-4.1-mini · high", s)
    }
}

