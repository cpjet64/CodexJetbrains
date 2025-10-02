package dev.curt.codexjb.core

import com.intellij.openapi.application.PathManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Centralized diagnostics log buffer. Captures Codex CLI stderr and other runtime
 * notes, stores them in-memory for UI consumption, and mirrors to a rotating
 * log file under the IDE log directory.
 */
object DiagnosticsService {
    private val log: LogSink = CodexLogger.forClass(DiagnosticsService::class.java)
    private val lock = ReentrantLock()
    private val listeners = CopyOnWriteArrayList<(List<String>) -> Unit>()
    private val lines = ArrayDeque<String>()
    private const val MAX_LINES = 2000
    private const val MAX_BYTES = 512 * 1024 // 512KB per log file
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val logFile: Path by lazy {
        val logsDir = Path.of(PathManager.getLogPath())
        logsDir.resolve("codex-diagnostics.log")
    }

    fun append(rawLine: String) {
        val normalized = rawLine.removeSuffix("\n").removeSuffix("\r")
        val redacted = SensitiveDataRedactor.redact(normalized)
        val timestamp = timestampFormatter.format(Instant.now().atZone(ZoneId.systemDefault()))
        val stamped = "$timestamp | $redacted"
        lock.withLock {
            lines.addLast(stamped)
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
            }
            writeToFile(stamped)
        }
        notifyListeners()
    }

    fun snapshot(): List<String> = lock.withLock { lines.toList() }

    fun addListener(listener: (List<String>) -> Unit) {
        listeners += listener
        listener(snapshot())
    }

    fun removeListener(listener: (List<String>) -> Unit) {
        listeners -= listener
    }

    fun copyToClipboard() {
        val text = snapshot().joinToString(separator = System.lineSeparator())
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }

    fun clear() {
        lock.withLock { lines.clear() }
        notifyListeners()
    }

    fun logException(message: String, t: Throwable) {
        append(message + ": " + (t.message ?: t::class.java.simpleName))
        log.error(message, t)
    }

    private fun writeToFile(line: String) {
        try {
            Files.createDirectories(logFile.parent)
            rotateIfNecessary()
            Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND).use { writer ->
                writer.appendLine(line)
            }
        } catch (ex: IOException) {
            log.warn("Failed to write diagnostics log: ${ex.message}")
        }
    }

    private fun rotateIfNecessary() {
        if (Files.exists(logFile) && Files.size(logFile) > MAX_BYTES) {
            val rotated = logFile.resolveSibling("${logFile.fileName}.1")
            try {
                Files.deleteIfExists(rotated)
                Files.move(logFile, rotated)
            } catch (ex: IOException) {
                log.warn("Failed to rotate diagnostics log: ${ex.message}")
            }
        }
    }

    private fun notifyListeners() {
        val snapshot = snapshot()
        listeners.forEach { listener ->
            try {
                listener(snapshot)
            } catch (ex: Exception) {
                log.warn("Diagnostics listener threw: ${ex.message}")
            }
        }
    }
}
