package dev.curt.codexjb.core

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * Result of invoking a one-off Codex CLI command.
 */
data class CodexCliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

fun interface CodexCliExecutor {
    fun run(
        executable: Path,
        arguments: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>
    ): CodexCliResult
}

fun CodexCliExecutor.run(
    executable: Path,
    vararg arguments: String,
    workingDirectory: Path? = null,
    environment: Map<String, String> = emptyMap()
): CodexCliResult = run(executable, arguments.toList(), workingDirectory, environment)

class DefaultCodexCliExecutor : CodexCliExecutor {
    override fun run(
        executable: Path,
        arguments: List<String>,
        workingDirectory: Path?,
        environment: Map<String, String>
    ): CodexCliResult {
        val command = buildList {
            add(executable.toString())
            addAll(arguments)
        }
        val builder = ProcessBuilder(command)
        workingDirectory?.let { builder.directory(it.toFile()) }
        builder.environment().putAll(environment)
        builder.redirectErrorStream(false)
        return try {
            val process = builder.start()
            val stdout = process.inputStream.reader(StandardCharsets.UTF_8).use { it.readText() }
            val stderr = process.errorStream.reader(StandardCharsets.UTF_8).use { it.readText() }
            val exitCode = process.waitFor()
            CodexCliResult(exitCode, stdout, stderr)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            CodexCliResult(-1, "", "Process interrupted: ${ex.message}")
        } catch (ex: IOException) {
            CodexCliResult(-1, "", ex.message ?: ex.javaClass.simpleName)
        }
    }
}
