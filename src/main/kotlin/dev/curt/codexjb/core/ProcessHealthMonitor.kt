package dev.curt.codexjb.core

import dev.curt.codexjb.ui.CodexStatusBarController
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ProcessHealthMonitor(
    private val service: CodexProcessService,
    private val config: CodexProcessConfig,
    private val diagnostics: LogSink = CodexLogger.forClass(ProcessHealthMonitor::class.java),
    private val staleThresholdMs: Long = TimeUnit.MINUTES.toMillis(5),
    private val checkIntervalMs: Long = TimeUnit.SECONDS.toMillis(30)
) : AutoCloseable {

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "codex-process-health").apply { isDaemon = true }
    }
    @Volatile private var restartedForStale = false

    fun start() {
        scheduler.scheduleWithFixedDelay(::check, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS)
    }

    private fun check() {
        val snapshot = ProcessHealth.snapshot()
        val now = System.currentTimeMillis()
        if (service.isRunning() && now - snapshot.lastStdout > staleThresholdMs) {
            if (!restartedForStale) {
                DiagnosticsService.append("Codex CLI appears stale; attempting restart")
                ProcessHealth.markStale()
                CodexStatusBarController.updateHealth(ProcessHealth.Status.STALE)
                ProcessHealth.onRestarting()
                val restarted = service.start(config, restart = true)
                restartedForStale = true
                if (restarted) {
                    ProcessHealth.onRestartSucceeded()
                    DiagnosticsService.append("Codex CLI restarted after staleness detection")
                    CodexStatusBarController.updateHealth(ProcessHealth.Status.OK)
                } else {
                    ProcessHealth.markError()
                    DiagnosticsService.append("Codex CLI restart failed during staleness recovery")
                    CodexStatusBarController.updateHealth(ProcessHealth.Status.ERROR)
                }
            }
        } else {
            restartedForStale = false
        }
    }

    override fun close() {
        scheduler.shutdownNow()
    }
}
