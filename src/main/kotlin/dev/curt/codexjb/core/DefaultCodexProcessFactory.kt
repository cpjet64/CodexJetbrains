package dev.curt.codexjb.core

import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class DefaultCodexProcessFactory : CodexProcessFactory {
    override fun start(config: CodexProcessConfig): CodexProcessHandle {
        val command = buildList {
            add(config.executable.toString())
            addAll(config.arguments)
        }
        val builder = ProcessBuilder(command)
        config.workingDirectory?.let { builder.directory(it.toFile()) }
        if (!config.inheritParentEnvironment) {
            builder.environment().clear()
        }
        builder.environment().putAll(config.environment)
        builder.redirectErrorStream(false)
        val process = builder.start()
        return RealCodexProcess(config, process)
    }
}

private class RealCodexProcess(
    override val config: CodexProcessConfig,
    private val process: Process
) : CodexProcessHandle {

    private val writer = BufferedWriter(
        OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
    )

    override val stdin: AppendableProcessWriter = BufferedProcessWriter(writer)
    override val stdout: ProcessStream = InputStreamAdapter(process.inputStream)
    override val stderr: ProcessStream = InputStreamAdapter(process.errorStream)

    override fun isAlive(): Boolean = process.isAlive

    override fun destroy() {
        kotlin.runCatching { writer.flush() }
        kotlin.runCatching { writer.close() }
        process.destroy()
    }
}

private class BufferedProcessWriter(
    private val delegate: BufferedWriter
) : AppendableProcessWriter {
    override fun writeLine(line: String) {
        delegate.write(line)
        delegate.newLine()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}

private class InputStreamAdapter(
    private val input: InputStream
) : ProcessStream {
    override fun asInputStream(): InputStream = input
}
