package dev.curt.codexjb.core

fun interface ProcessStopper { fun stop() }

class CodexShutdown(private val stopper: ProcessStopper) {
    fun onProjectClosed() {
        stopper.stop()
    }
}

class ServiceStopper(private val service: CodexProcessService) : ProcessStopper {
    override fun stop() = service.stop()
}
