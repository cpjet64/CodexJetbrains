package dev.curt.codexjb.proto

import com.intellij.openapi.Disposable
import dev.curt.codexjb.core.LogSink
import java.time.Clock
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class HeartbeatScheduler(
    private val sendHeartbeat: () -> Unit,
    private val log: LogSink,
    private val interval: Duration = Duration.ofSeconds(30),
    private val clock: Clock = Clock.systemUTC(),
    private val schedulerFactory: () -> ScheduledExecutorService = {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "codex-heartbeat").apply { isDaemon = true }
        }
    }
) : Disposable {

    private val futureRef = AtomicReference<ScheduledFuture<*>?>()
    private val schedulerRef = AtomicReference<ScheduledExecutorService?>()
    @Volatile private var lastActivityMillis: Long = clock.millis()

    fun start() {
        if (futureRef.get() != null) return
        val scheduler = schedulerRef.updateAndGet { existing -> existing ?: schedulerFactory() } ?: return
        lastActivityMillis = clock.millis()
        val delayMillis = interval.toMillis().coerceAtLeast(1)
        val scheduled = scheduler.scheduleWithFixedDelay(
            { runHeartbeatCheck() },
            delayMillis,
            delayMillis,
            TimeUnit.MILLISECONDS
        )
        futureRef.set(scheduled)
    }

    fun markActivity() {
        lastActivityMillis = clock.millis()
    }

    internal fun runHeartbeatCheck() {
        val now = clock.millis()
        if (now - lastActivityMillis < interval.toMillis()) {
            return
        }
        try {
            sendHeartbeat()
        } catch (t: Throwable) {
            log.warn("heartbeat send failed: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            lastActivityMillis = now
        }
    }

    override fun dispose() {
        futureRef.getAndSet(null)?.cancel(false)
        schedulerRef.getAndSet(null)?.shutdownNow()
    }
}
