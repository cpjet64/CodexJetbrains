package dev.curt.codexjb.core

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ProcessHealth {
    enum class Status { OK, RESTARTING, STALE, ERROR }

    private val lastStdoutTime = AtomicLong(System.currentTimeMillis())
    private val lastRestartTime = AtomicLong(0)
    private val restartAttempts = AtomicInteger(0)
    private val statusRef = AtomicReference(Status.OK)

    fun onStdout() {
        lastStdoutTime.set(System.currentTimeMillis())
        statusRef.compareAndSet(Status.STALE, Status.OK)
        statusRef.compareAndSet(Status.RESTARTING, Status.OK)
    }

    fun onRestarting() {
        lastRestartTime.set(System.currentTimeMillis())
        statusRef.set(Status.RESTARTING)
        restartAttempts.incrementAndGet()
    }

    fun onRestartSucceeded() {
        lastStdoutTime.set(System.currentTimeMillis())
        statusRef.set(Status.OK)
        restartAttempts.set(0)
    }

    fun markStale() {
        statusRef.set(Status.STALE)
    }

    fun markError() {
        statusRef.set(Status.ERROR)
    }

    fun resetAttempts() {
        restartAttempts.set(0)
    }

    fun resetForTests() {
        lastStdoutTime.set(System.currentTimeMillis())
        lastRestartTime.set(0)
        restartAttempts.set(0)
        statusRef.set(Status.OK)
    }

    fun snapshot(): Snapshot = Snapshot(
        lastStdout = lastStdoutTime.get(),
        lastRestart = lastRestartTime.get(),
        restartAttempts = restartAttempts.get(),
        status = statusRef.get()
    )

    data class Snapshot(
        val lastStdout: Long,
        val lastRestart: Long,
        val restartAttempts: Int,
        val status: Status
    )
}
