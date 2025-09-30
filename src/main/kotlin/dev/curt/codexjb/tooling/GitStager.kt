package dev.curt.codexjb.tooling

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vfs.LocalFileSystem
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.LogSink
import java.io.File

object GitStager {
    private val log: LogSink = CodexLogger.forClass(GitStager::class.java)

    fun stage(project: Project, absolutePaths: Collection<String>) {
        if (absolutePaths.isEmpty()) return
        try {
            // Simple git add implementation without git4idea dependency
            val processBuilder = ProcessBuilder("git", "add")
            val basePath = project.basePath
            if (basePath == null) {
                log.warn("git stage skipped: project has no base path")
                return
            }
            processBuilder.directory(File(basePath))
            absolutePaths.forEach { processBuilder.command().add(it) }
            
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                log.warn("git add failed: $error")
            } else {
                log.info("Successfully staged ${absolutePaths.size} files")
            }
        } catch (t: Throwable) {
            log.warn("git stage skipped: ${t.message}")
        }
    }
}

