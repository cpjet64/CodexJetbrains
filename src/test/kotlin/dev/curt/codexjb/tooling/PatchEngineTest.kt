package dev.curt.codexjb.tooling

import dev.curt.codexjb.diff.Hunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PatchEngineTest {
    @Test
    fun appliesSimpleAdditionAndDeletion() {
        val original = """
            line1
            line2
            line3
        """.trimIndent().replace("\r\n", "\n").trim()
        val hunk = Hunk(
            header = "@@ -2,2 +2,3 @@",
            lines = listOf(
                " line2",
                "-line3",
                "+lineX",
                "+line3"
            )
        )
        val updated = PatchEngine.apply(original, listOf(hunk))
        assertEquals("line1\nline2\nlineX\nline3", updated)
    }

    @Test
    fun detectsContextMismatch() {
        val original = "a\nb\nc"
        val hunk = Hunk(
            header = "@@ -1,2 +1,2 @@",
            lines = listOf(
                " x",
                "-b",
                "+B"
            )
        )
        assertFailsWith<IllegalStateException> {
            PatchEngine.apply(original, listOf(hunk))
        }
    }
}

