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
        // 1. Flush and close stdin (signals no more input)
        kotlin.runCatching {
            writer.flush()
            writer.close()
        }

        // 2. Wait briefly for graceful exit (2 seconds)
        val exited = kotlin.runCatching {
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        }.getOrDefault(false)

        // 3. Normal terminate if still alive
        if (!exited && process.isAlive) {
            process.destroy()
        }

        // 4. Last resort: force kill (kills process tree on most platforms)
        kotlin.runCatching {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()  // Wait indefinitely for forceful kill
            }
        }
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
