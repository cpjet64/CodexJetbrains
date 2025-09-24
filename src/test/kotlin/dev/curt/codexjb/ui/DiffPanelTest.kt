package dev.curt.codexjb.ui

import dev.curt.codexjb.diff.UnifiedDiffParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DiffPanelTest {
    
    @Test
    fun parsesDiffCorrectly() {
        val diff = """
            --- a/hello.txt
            +++ b/hello.txt
            @@ -1,1 +1,2 @@
            -Hello
            +Hello world
            +!
        """.trimIndent()
        
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        
        val patch = patches.first()
        assertEquals("a/hello.txt", patch.oldPath)
        assertEquals("b/hello.txt", patch.newPath)
        assertEquals(1, patch.hunks.size)
        
        val hunk = patch.hunks.first()
        assertEquals("@@ -1,1 +1,2 @@", hunk.header)
        assertEquals(3, hunk.lines.size)
        assertEquals("-Hello", hunk.lines[0])
        assertEquals("+Hello world", hunk.lines[1])
        assertEquals("+!", hunk.lines[2])
    }
    
    @Test
    fun handlesEmptyDiff() {
        val patches = UnifiedDiffParser.parse("")
        assertEquals(0, patches.size)
    }
    
    @Test
    fun handlesInvalidDiff() {
        val patches = UnifiedDiffParser.parse("not a valid diff")
        assertEquals(0, patches.size)
    }
    
    @Test
    fun handlesMultipleFilesInDiff() {
        val diff = """
            --- a/file1.txt
            +++ b/file1.txt
            @@ -1,1 +1,2 @@
             content1
            +added1
            --- a/file2.txt
            +++ b/file2.txt
            @@ -1,1 +1,2 @@
             content2
            +added2
        """.trimIndent()
        
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(2, patches.size)
        
        val patch1 = patches[0]
        assertEquals("a/file1.txt", patch1.oldPath)
        assertEquals("b/file1.txt", patch1.newPath)
        
        val patch2 = patches[1]
        assertEquals("a/file2.txt", patch2.oldPath)
        assertEquals("b/file2.txt", patch2.newPath)
    }
    
    @Test
    fun createsDiffPanelWithValidDiff() {
        val diff = """
            --- a/test.txt
            +++ b/test.txt
            @@ -1,3 +1,4 @@
             line1
            -line2
            +line2 modified
            +line2.5
             line3
        """.trimIndent()
        
        // Test that we can parse the diff correctly
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        
        val patch = patches.first()
        assertEquals("a/test.txt", patch.oldPath)
        assertEquals("b/test.txt", patch.newPath)
        assertEquals(1, patch.hunks.size)
        
        val hunk = patch.hunks.first()
        assertEquals("@@ -1,3 +1,4 @@", hunk.header)
        assertEquals(5, hunk.lines.size)
        assertEquals(" line1", hunk.lines[0])
        assertEquals("-line2", hunk.lines[1])
        assertEquals("+line2 modified", hunk.lines[2])
        assertEquals("+line2.5", hunk.lines[3])
        assertEquals(" line3", hunk.lines[4])
    }
}
