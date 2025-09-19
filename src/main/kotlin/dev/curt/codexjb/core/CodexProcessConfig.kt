package dev.curt.codexjb.core

import java.nio.file.Path

/**
 * Describes how to launch the Codex CLI process.
 */
data class CodexProcessConfig(
    val executable: Path,
    val arguments: List<String> = listOf("proto"),
    val workingDirectory: Path? = null,
    val environment: Map<String, String> = emptyMap(),
    val inheritParentEnvironment: Boolean = true
)

/**
 * Factory abstraction so tests can inject lightweight process handles.
 */
fun interface CodexProcessFactory {
    fun start(config: CodexProcessConfig): CodexProcessHandle
}

/**
 * Minimal surface the service needs to interact with a running process.
 */
interface CodexProcessHandle {
    val config: CodexProcessConfig
    val stdin: AppendableProcessWriter
    val stdout: ProcessStream
    val stderr: ProcessStream

    fun isAlive(): Boolean
    fun destroy()
}

/**
 * Simple wrapper over the process input stream so tests can intercept writes.
 */
interface AppendableProcessWriter {
    fun writeLine(line: String)
    fun flush()
    fun close()
}

/**
 * Exposes a readable stream; the implementation detail is deferred to later tasks.
 */
interface ProcessStream {
    fun asInputStream(): java.io.InputStream
}
