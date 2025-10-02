package dev.curt.codexjb.tooling

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.TelemetryService
import dev.curt.codexjb.tooling.GitStager
import dev.curt.codexjb.diff.FilePatch
import dev.curt.codexjb.diff.UnifiedDiffParser
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path

object PatchApplier {
    private val log: LogSink = CodexLogger.forClass(PatchApplier::class.java)

    fun apply(project: Project, diffText: String, include: Set<String>): ResultSummary {
        val patches = UnifiedDiffParser.parse(diffText)
        var ok = 0
        var fail = 0
        val stagedCandidates = mutableListOf<String>()
        for (p in patches) {
            if (!include.contains(p.newPath)) continue
            val success = applySingle(project, p)
            if (success) {
                ok++
                TelemetryService.recordPatchApplySuccess()
            } else {
                fail++
                TelemetryService.recordPatchApplyFailure()
            }
            if (success && p.newPath != "/dev/null") {
                val base = project.basePath
                if (base != null) stagedCandidates += java.nio.file.Path.of(base, stripPrefix(p.newPath)).toString()
            }
        }
        // optional staging
        val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
        if (cfg.autoStageAppliedChanges) {
            GitStager.stage(project, stagedCandidates)
        }
        return ResultSummary(ok, fail)
    }

    data class ResultSummary(val success: Int, val failed: Int)

    private fun applySingle(project: Project, patch: FilePatch): Boolean {
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                doApply(project, patch)
            }
            true
        } catch (t: Throwable) {
            val msg = "Apply failed for ${patch.newPath}: ${t.message}"
            if (t is IllegalStateException) log.warn(msg) else log.error(msg, t)
            false
        }
    }

    private fun doApply(project: Project, patch: FilePatch) {
        val base = project.basePath ?: throw IllegalStateException("No project basePath")
        doApplyWithBase(project, patch, Path.of(base))
    }

    internal fun doApplyWithBase(project: Project, patch: FilePatch, basePath: Path) {
        val targetRel = stripPrefix(patch.newPath)
        val oldRel = stripPrefix(patch.oldPath)

        if (patch.newPath == "/dev/null") {
            // deletion
            val vf = findVirtualFile(basePath.resolve(oldRel)) ?: return
            vf.delete(this)
            return
        }

        val target = basePath.resolve(targetRel)
        val vf = findOrCreateVirtualFile(target)
        val doc = FileDocumentManager.getInstance().getDocument(vf)
            ?: throw IllegalStateException("No document for ${vf.path}")
        val original = doc.text
        val updated = PatchEngine.apply(original, patch.hunks)
        doc.setText(updated)
        FileDocumentManager.getInstance().saveDocument(doc)
        val cfg = ApplicationManager.getApplication().getService(CodexConfigService::class.java)
        if (cfg.autoOpenChangedFiles) {
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
    }

    private fun findVirtualFile(path: Path): VirtualFile? {
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
    }

    private fun findOrCreateVirtualFile(path: Path): VirtualFile {
        val ioFile = path.toFile()
        val parent = ioFile.parentFile
        val vParent = VfsUtil.createDirectories(parent.absolutePath)
        val result = vParent.findChild(ioFile.name) ?: vParent.createChildData(this, ioFile.name)
        vParent.refresh(false, false) // Refresh to ensure VFS consistency
        return result
    }

    private fun stripPrefix(p: String): String {
        val s = p.removePrefix("a/").removePrefix("b/")
        val cleaned = if (s.startsWith("./")) s.substring(2) else s

        // Reject paths with traversal attempts to prevent path traversal attacks
        if (cleaned.contains("..")) {
            throw IllegalStateException("Path traversal not allowed: $p")
        }

        return cleaned.replace("\\", "/") // Normalize path separators
    }

    // Logic moved to PatchEngine for testability
}
