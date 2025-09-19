package dev.curt.codexjb.core

import java.io.ByteArrayInputStream
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CodexProcessServiceTest {

    private val factory = RecordingFactory()
    private val service = CodexProcessService(factory)

    @Test
    fun startLaunchesProcessWhenNotRunning() {
        val config = sampleConfig()
        val started = service.start(config)

        assertTrue(started)
        assertTrue(service.isRunning())
        assertEquals(1, factory.startCount)
        assertEquals(config, service.currentConfig())
    }

    @Test
    fun startWhileRunningReturnsFalse() {
        val config = sampleConfig()
        service.start(config)

        val result = service.start(config)

        assertFalse(result)
        assertEquals(1, factory.startCount)
    }

    @Test
    fun restartReplacesProcess() {
        val config = sampleConfig()
        service.start(config)
        val first = factory.lastHandle()

        val result = service.start(config, restart = true)

        assertTrue(result)
        assertEquals(2, factory.startCount)
        assertTrue(first.destroyCalls > 0)
        assertTrue(service.isRunning())
    }

    @Test
    fun sendWritesLineAndFlushes() {
        val config = sampleConfig()
        service.start(config)
        val handle = factory.lastHandle()

        service.send("{\"hello\":true}")

        assertEquals(listOf("{\"hello\":true}"), handle.writer.lines)
        assertEquals(1, handle.writer.flushCount)
    }

    @Test
    fun sendWithoutStartThrows() {
        assertFailsWith<IllegalStateException> {
            service.send("{}")
        }
    }

    @Test
    fun stopDestroysProcess() {
        val config = sampleConfig()
        service.start(config)
        val handle = factory.lastHandle()

        service.stop()

        assertFalse(service.isRunning())
        assertEquals(1, handle.destroyCalls)
        assertTrue(handle.writer.closed)
    }

    private fun sampleConfig() = CodexProcessConfig(executable = Paths.get("/usr/bin/codex"))
}

private class RecordingFactory : CodexProcessFactory {
    var startCount = 0
        private set

    private val handles = mutableListOf<FakeHandle>()

    override fun start(config: CodexProcessConfig): CodexProcessHandle {
        startCount += 1
        return FakeHandle(config).also(handles::add)
    }

    fun lastHandle(): FakeHandle = handles.last()
}

private class FakeHandle(
    override val config: CodexProcessConfig
) : CodexProcessHandle {
    var destroyCalls = 0
        private set

    val writer = RecordingWriter()

    private var alive = true

    override val stdin: AppendableProcessWriter = writer
    override val stdout: ProcessStream = StaticStream()
    override val stderr: ProcessStream = StaticStream()

    override fun isAlive(): Boolean = alive

    override fun destroy() {
        destroyCalls += 1
        writer.close()
        alive = false
    }
}

private class RecordingWriter : AppendableProcessWriter {
    val lines = mutableListOf<String>()
    var flushCount = 0
        private set
    var closed = false
        private set

    override fun writeLine(line: String) {
        lines += line
    }

    override fun flush() {
        flushCount += 1
    }

    override fun close() {
        closed = true
    }
}

private class StaticStream : ProcessStream {
    override fun asInputStream() = ByteArrayInputStream(ByteArray(0))
}
