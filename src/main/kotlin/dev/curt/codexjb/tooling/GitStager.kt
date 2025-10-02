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
            val basePath = project.basePath
            if (basePath == null) {
                log.warn("git stage skipped: project has no base path")
                return
            }

            val baseFile = File(basePath).canonicalFile

            // Validate paths are within project directory to prevent command injection
            val validatedPaths = absolutePaths.mapNotNull { path ->
                try {
                    val file = File(path).canonicalFile
                    if (file.startsWith(baseFile)) {
                        path
                    } else {
                        log.warn("Rejecting path outside project: $path")
                        null
                    }
                } catch (e: Exception) {
                    log.warn("Invalid path rejected: $path - ${e.message}")
                    null
                }
            }

            if (validatedPaths.isEmpty()) {
                log.warn("No valid paths to stage")
                return
            }

            val processBuilder = ProcessBuilder("git", "add")
            processBuilder.directory(baseFile)
            validatedPaths.forEach { processBuilder.command().add(it) }

            val process = processBuilder.start()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                log.warn("git add failed: $error")
            } else {
                log.info("Successfully staged ${validatedPaths.size} files")
            }
        } catch (t: Throwable) {
            log.warn("git stage skipped: ${t.message}")
        }
    }
}

