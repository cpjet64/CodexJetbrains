package dev.curt.codexjb.tooling

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.diff.UnifiedDiffParser
import java.nio.file.Files
import kotlin.test.assertEquals

class PatchApplierIntegrationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().getService(CodexConfigService::class.java).apply {
            autoOpenChangedFiles = false
            autoStageAppliedChanges = false
        }
    }

    fun `test applies diff into existing file`() {
        val tempDir = Files.createTempDirectory("patch-test")
        val srcDir = tempDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("foo.txt")
        Files.writeString(testFile, "hello\n")

        val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(testFile.toString())!!
        val diff = """
            --- a/src/foo.txt
            +++ b/src/foo.txt
            @@ -1 +1 @@
            -hello
            +world
        """.trimIndent()

        val patches = UnifiedDiffParser.parse(diff)

        var success = true
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                PatchApplier.doApplyWithBase(project, patches.first(), tempDir)
            } catch (e: Exception) {
                success = false
            }
        }

        assertTrue(success)
        val updated = FileDocumentManager.getInstance().getDocument(vf)
        assertEquals("world\n", updated!!.text)

        tempDir.toFile().deleteRecursively()
    }

    fun `test reports failure on conflicting diff`() {
        val tempDir = Files.createTempDirectory("patch-test")
        val srcDir = tempDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("bar.txt")
        Files.writeString(testFile, "base\n")

        LocalFileSystem.getInstance().refreshAndFindFileByPath(testFile.toString())!!
        val diff = """
            --- a/src/bar.txt
            +++ b/src/bar.txt
            @@ -1 +1 @@
            -different
            +change
        """.trimIndent()

        val patches = UnifiedDiffParser.parse(diff)

        var failed = false
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                PatchApplier.doApplyWithBase(project, patches.first(), tempDir)
            } catch (e: Exception) {
                failed = true
            }
        }

        assertTrue(failed)

        tempDir.toFile().deleteRecursively()
    }
}
