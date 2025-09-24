package dev.curt.codexjb.diff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UnifiedDiffParserTest {
    @Test
    fun parsesSimpleDiff() {
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
        val p = patches.first()
        assertEquals("a/hello.txt", p.oldPath)
        assertEquals("b/hello.txt", p.newPath)
        assertEquals(1, p.hunks.size)
        assertEquals(true, p.hunks.first().header.startsWith("@@"))
    }
    
    @Test
    fun parsesMultipleFiles() {
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
    fun parsesMultipleHunks() {
        val diff = """
            --- a/test.txt
            +++ b/test.txt
            @@ -1,3 +1,4 @@
             line1
            -line2
            +line2 modified
             line3
            @@ -4,1 +5,2 @@
             line4
            +line5
        """.trimIndent()
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        val patch = patches.first()
        assertEquals(2, patch.hunks.size)
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
    fun parsesContextLines() {
        val diff = """
            --- a/test.txt
            +++ b/test.txt
            @@ -1,5 +1,6 @@
             line1
             line2
            -line3
            +line3 modified
             line4
             line5
            +line6
        """.trimIndent()
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        val patch = patches.first()
        assertEquals(1, patch.hunks.size)
        
        val hunk = patch.hunks.first()
        assertEquals("@@ -1,5 +1,6 @@", hunk.header)
        assertEquals(7, hunk.lines.size)
        assertEquals(" line1", hunk.lines[0])
        assertEquals(" line2", hunk.lines[1])
        assertEquals("-line3", hunk.lines[2])
        assertEquals("+line3 modified", hunk.lines[3])
        assertEquals(" line4", hunk.lines[4])
        assertEquals(" line5", hunk.lines[5])
        assertEquals("+line6", hunk.lines[6])
    }
    
    @Test
    fun parsesNewFile() {
        val diff = """
            --- /dev/null
            +++ b/newfile.txt
            @@ -0,0 +1,2 @@
            +line1
            +line2
        """.trimIndent()
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        val patch = patches.first()
        assertEquals("/dev/null", patch.oldPath)
        assertEquals("b/newfile.txt", patch.newPath)
    }
    
    @Test
    fun parsesDeletedFile() {
        val diff = """
            --- a/oldfile.txt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -line1
            -line2
        """.trimIndent()
        val patches = UnifiedDiffParser.parse(diff)
        assertEquals(1, patches.size)
        val patch = patches.first()
        assertEquals("a/oldfile.txt", patch.oldPath)
        assertEquals("/dev/null", patch.newPath)
    }
}

