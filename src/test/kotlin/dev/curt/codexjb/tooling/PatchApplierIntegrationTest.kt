package dev.curt.codexjb.tooling

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.curt.codexjb.core.CodexConfigService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PatchApplierIntegrationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().getService(CodexConfigService::class.java).apply {
            autoOpenChangedFiles = false
            autoStageAppliedChanges = false
        }
    }

    @Test
    fun `applies diff into existing file`() {
        val vf = myFixture.tempDirFixture.createFile("src/foo.txt", "hello\n").virtualFile
        val diff = """
            --- a/src/foo.txt
            +++ b/src/foo.txt
            @@ -1 +1 @@
            -hello
            +world
        """.trimIndent()

        val summary = PatchApplier.apply(project, diff, setOf("b/src/foo.txt"))

        assertEquals(1, summary.success)
        assertEquals(0, summary.failed)
        val updated = FileDocumentManager.getInstance().getDocument(vf)
        assertEquals("world\n", updated!!.text)
    }

    @Test
    fun `reports failure on conflicting diff`() {
        myFixture.tempDirFixture.createFile("src/bar.txt", "base\n")
        val diff = """
            --- a/src/bar.txt
            +++ b/src/bar.txt
            @@ -1 +1 @@
            -different
            +change
        """.trimIndent()

        val summary = PatchApplier.apply(project, diff, setOf("b/src/bar.txt"))

        assertEquals(0, summary.success)
        assertEquals(1, summary.failed)
    }
}
