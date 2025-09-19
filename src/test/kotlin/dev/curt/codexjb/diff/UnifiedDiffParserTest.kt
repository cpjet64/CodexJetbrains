package dev.curt.codexjb.diff

import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedDiffParserTest {
    @Test
    fun parsesSimpleDiff() {
        val diff = """
            --- a/hello.txt
            +++ b/hello.txt
            @@ -1,1 +1,2 @@
            -Hello
            +Hello world
            +!\n
        """.trimIndent()
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        val p = patches.first()
        assertEquals("a/hello.txt", p.oldPath)
        assertEquals("b/hello.txt", p.newPath)
        assertEquals(1, p.hunks.size)
        assertEquals(true, p.hunks.first().header.startsWith("@@"))
    }
}

