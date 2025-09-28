package dev.curt.codexjb.core

import dev.curt.codexjb.ui.CodexStatusBarController
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

object TurnMetricsService {
    data class Metrics(
        val streamDurationMs: Long = 0,
        val diffDurationMs: Long = 0,
        val applyDurationMs: Long = 0,
        val execDurationMs: Long = 0,
        val tokensPerSecond: Double? = null
    )

    private data class InFlight(
        val submissionTime: Long,
        var streamStart: Long? = null,
        var diffStart: Long? = null,
        var applyStart: Long? = null,
        var execStart: Long? = null,
        var tokenCount: Int = 0
    )

    private val turns = ConcurrentHashMap<String, InFlight>()
    private val latest = AtomicReference(Metrics())

    fun onSubmission(id: String) {
        turns[id] = InFlight(System.currentTimeMillis())
        updateLatest { copy(tokensPerSecond = null) }
    }

    fun onStreamDelta(id: String) {
        turns[id]?.let { if (it.streamStart == null) it.streamStart = System.currentTimeMillis() }
    }

    fun onStreamComplete(id: String) {
        turns.remove(id)?.let { inflight ->
            val end = System.currentTimeMillis()
            val start = inflight.streamStart ?: inflight.submissionTime
            val duration = end - start
            val tokensPerSecond = if (duration > 0 && inflight.tokenCount > 0) {
                inflight.tokenCount.toDouble() / (duration / 1000.0)
            } else null
            updateLatest { copy(streamDurationMs = duration, tokensPerSecond = tokensPerSecond) }
        }
    }

    fun onTokenCount(id: String, tokens: Int) {
        turns[id]?.tokenCount = tokens
    }

    fun onDiffStart(id: String) {
        turns[id]?.let { if (it.diffStart == null) it.diffStart = System.currentTimeMillis() }
    }

    fun onDiffEnd(id: String) {
        val end = System.currentTimeMillis()
        val start = turns[id]?.diffStart ?: return
        updateLatest { copy(diffDurationMs = end - start) }
    }

    fun onApplyBegin(id: String) {
        turns[id]?.applyStart = System.currentTimeMillis()
    }

    fun onApplyEnd(id: String) {
        val end = System.currentTimeMillis()
        val start = turns[id]?.applyStart ?: return
        updateLatest { copy(applyDurationMs = end - start) }
    }

    fun onExecStart(id: String) {
        turns[id]?.execStart = System.currentTimeMillis()
    }

    fun onExecEnd(id: String) {
        val end = System.currentTimeMillis()
        val start = turns[id]?.execStart ?: return
        updateLatest { copy(execDurationMs = end - start) }
    }

    fun latestMetrics(): Metrics = latest.get()

    private fun updateLatest(transform: Metrics.() -> Metrics) {
        latest.updateAndGet { transform(it) }
        CodexStatusBarController.updateTokens(latest.get().tokensPerSecond)
    }
}
