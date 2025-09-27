package dev.curt.codexjb.proto

import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.CodexProcessService
import dev.curt.codexjb.core.LogSink

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
    private val onReconnect: () -> Unit = {}
) {
    @Volatile private var onSendListener: (() -> Unit)? = null

    fun send(line: String) {
        try {
            backend.send(line)
            notifySent()
            return
        } catch (_: Exception) {
            log.warn("send failed; attempting restart")
        }
        val started = backend.start(config, restart = true)
        if (started) onReconnect()
        backend.send(line)
        notifySent()
    }

    fun setOnSendListener(listener: (() -> Unit)?) {
        onSendListener = listener
    }

    private fun notifySent() {
        onSendListener?.invoke()
    }
}