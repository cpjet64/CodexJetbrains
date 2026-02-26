package dev.curt.codexjb.core

import com.intellij.openapi.Disposable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.io.InputStream

class CodexProcessService(
    private val factory: CodexProcessFactory = DefaultCodexProcessFactory()
) : Disposable {

    private val lock = ReentrantLock()
    private var state: ProcessState? = null

    fun start(config: CodexProcessConfig, restart: Boolean = false): Boolean {
        var toDestroy: CodexProcessHandle? = null
        val shouldStart = lock.withLock {
            val current = state?.handle
            if (current != null && current.isAlive() && !restart) {
                return false
            }
            if (current != null) {
                toDestroy = current
            }
            state = null
            true
        }
        if (!shouldStart) {
            return false
        }
        toDestroy?.destroy()
        val handle = factory.start(config)
        lock.withLock {
            state = ProcessState(handle)
        }
        return true
    }

    fun send(line: String) {
        val handle = lock.withLock { state?.handle }
            ?: error("Codex CLI process is not running.")
        handle.stdin.writeLine(line)
        handle.stdin.flush()
    }

    fun stop() {
        val toDestroy = lock.withLock {
            val current = state?.handle
            state = null
            current
        }
        toDestroy?.destroy()
    }

    fun isRunning(): Boolean = lock.withLock { state?.handle?.isAlive() == true }

    fun currentConfig(): CodexProcessConfig? = lock.withLock { state?.handle?.config }

    fun streams(): Pair<InputStream, InputStream>? = lock.withLock {
        val h = state?.handle ?: return null
        Pair(h.stdout.asInputStream(), h.stderr.asInputStream())
    }

    fun currentHandle(): CodexProcessHandle? = lock.withLock { state?.handle }

    override fun dispose() {
        stop()
    }

    private data class ProcessState(val handle: CodexProcessHandle)
}
