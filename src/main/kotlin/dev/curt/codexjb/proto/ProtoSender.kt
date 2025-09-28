package dev.curt.codexjb.proto

import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.CodexProcessService
import dev.curt.codexjb.core.DiagnosticsService
import dev.curt.codexjb.core.LogSink
import dev.curt.codexjb.core.ProcessHealth
import dev.curt.codexjb.ui.CodexStatusBarController
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface SenderBackend {
    fun start(config: CodexProcessConfig, restart: Boolean = true): Boolean
    fun send(line: String)
}

class ServiceBackend(private val svc: CodexProcessService) : SenderBackend {
    override fun start(config: CodexProcessConfig, restart: Boolean): Boolean =
        svc.start(config, restart)
    override fun send(line: String) = svc.send(line)
}

class ProtoSender(
    private val backend: SenderBackend,
    private val config: CodexProcessConfig,
    private val log: LogSink,
    private val onReconnect: () -> Unit = {},
    private val backoffCalculator: (Int) -> Long = { attempt ->
        (1L shl (attempt - 1)).coerceAtMost(10L) * 1_000L
    }
) {
    @Volatile private var onSendListener: (() -> Unit)? = null
    private val restartAttempts = AtomicInteger(0)
    private val restartLock = ReentrantLock()

    fun send(line: String) {
        try {
            backend.send(line)
            notifySent()
            return
        } catch (_: Exception) {
            log.warn("send failed; attempting restart")
        }
        restartLock.withLock {
            val attempt = restartAttempts.incrementAndGet()
            val delayMs = backoffCalculator(attempt)
            DiagnosticsService.append("Codex CLI send failure; retrying in ${delayMs}ms (attempt $attempt)")
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            ProcessHealth.onRestarting()
            CodexStatusBarController.updateHealth(ProcessHealth.Status.RESTARTING)
            val started = backend.start(config, restart = true)
            if (started) {
                restartAttempts.set(0)
                DiagnosticsService.append("Codex CLI restarted after backoff")
                ProcessHealth.onRestartSucceeded()
                CodexStatusBarController.updateHealth(ProcessHealth.Status.OK)
                onReconnect()
                backend.send(line)
                notifySent()
            } else {
                DiagnosticsService.append("Codex CLI restart failed after backoff")
                ProcessHealth.markError()
                CodexStatusBarController.updateHealth(ProcessHealth.Status.ERROR)
                throw IllegalStateException("Unable to restart Codex CLI")
            }
        }
    }

    fun setOnSendListener(listener: (() -> Unit)?) {
        onSendListener = listener
    }

    private fun notifySent() {
        onSendListener?.invoke()
    }
}
