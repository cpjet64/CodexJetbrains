package dev.curt.codexjb.core

import dev.curt.codexjb.core.AppendableProcessWriter
import dev.curt.codexjb.core.ProcessStream
import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.CodexProcessFactory
import dev.curt.codexjb.core.CodexProcessHandle
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProcessHealthMonitorTest {

    @Before
    fun resetHealth() {
        ProcessHealth.resetForTests()
    }

    @Test
    fun restartsWhenStale() {
        val factory = RecordingProcessFactory()
        val service = CodexProcessService(factory)
        val config = CodexProcessConfig(Path.of("/usr/bin/codex"))
        service.start(config)
        val monitor = ProcessHealthMonitor(
            service = service,
            config = config,
            diagnostics = CodexLogger.forClass(ProcessHealthMonitorTest::class.java),
            staleThresholdMs = 30,
            checkIntervalMs = 20
        )
        monitor.start()

        Thread.sleep(80)

        monitor.close()
        assertTrue("expected restart when stale", factory.starts.get() >= 2)
    }

    private class RecordingProcessFactory : CodexProcessFactory {
        val starts = AtomicInteger(0)
        override fun start(config: CodexProcessConfig): CodexProcessHandle {
            starts.incrementAndGet()
            return object : CodexProcessHandle {
                override val config: CodexProcessConfig = config
                override val stdin: AppendableProcessWriter = object : AppendableProcessWriter {
                    override fun writeLine(line: String) {}
                    override fun flush() {}
                    override fun close() {}
                    override fun asOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
                }
                override val stdout: ProcessStream = object : ProcessStream {
                    override fun asInputStream(): InputStream = InputStream.nullInputStream()
                }
                override val stderr: ProcessStream = object : ProcessStream {
                    override fun asInputStream(): InputStream = InputStream.nullInputStream()
                }
                override fun isAlive(): Boolean = true
                override fun destroy() {}
            }
        }
    }
}
