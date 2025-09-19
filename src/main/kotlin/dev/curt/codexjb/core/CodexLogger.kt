package dev.curt.codexjb.core

import com.intellij.openapi.diagnostic.Logger

interface LogSink {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, t: Throwable? = null)
}

object CodexLogger {
    @Volatile
    private var provider: (String) -> LogSink = { IntelliJLogSink(it) }

    fun get(category: String): LogSink = provider(category)

    fun forClass(clazz: Class<*>): LogSink = get(clazz.name)

    fun installProvider(p: (String) -> LogSink) {
        provider = p
    }
}

private class IntelliJLogSink(category: String) : LogSink {
    private val logger = Logger.getInstance(category)

    override fun info(message: String) {
        logger.info(message)
    }

    override fun warn(message: String) {
        logger.warn(message)
    }

    override fun error(message: String, t: Throwable?) {
        if (t != null) logger.error(message, t) else logger.error(message)
    }
}

