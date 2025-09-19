package dev.curt.codexjb.core

import java.nio.file.Path

class CodexWhoamiTask(
    private val executor: CodexCliExecutor = DefaultCodexCliExecutor()
) {

    fun ensureLogin(cliPath: Path, workingDirectory: Path? = null): WhoamiOutcome {
        val whoami = executor.run(cliPath, "whoami", workingDirectory = workingDirectory)
        if (!requiresLogin(whoami)) {
            return WhoamiOutcome(WhoamiStatus.ALREADY_LOGGED_IN, whoami, null)
        }
        val login = executor.run(cliPath, "login", workingDirectory = workingDirectory)
        val status = if (login.isSuccess) WhoamiStatus.LOGIN_TRIGGERED else WhoamiStatus.LOGIN_FAILED
        return WhoamiOutcome(status, whoami, login)
    }

    private fun requiresLogin(result: CodexCliResult): Boolean {
        if (!result.isSuccess) return true
        val text = (result.stdout + "\n" + result.stderr).lowercase()
        val markers = listOf("not logged", "login required", "please log in")
        return markers.any(text::contains)
    }
}

data class WhoamiOutcome(
    val status: WhoamiStatus,
    val whoamiResult: CodexCliResult,
    val loginResult: CodexCliResult?
)

enum class WhoamiStatus {
    ALREADY_LOGGED_IN,
    LOGIN_TRIGGERED,
    LOGIN_FAILED
}
