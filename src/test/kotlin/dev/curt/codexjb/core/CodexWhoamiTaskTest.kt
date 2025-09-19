package dev.curt.codexjb.core

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodexWhoamiTaskTest {

    private val cliPath = Paths.get("/usr/bin/codex")

    @Test
    fun alreadyLoggedInSkipsLogin() {
        val executor = RecordingCliExecutor(
            CodexCliResult(0, "user: test", "")
        )
        val task = CodexWhoamiTask(executor)

        val outcome = task.ensureLogin(cliPath)

        assertEquals(WhoamiStatus.ALREADY_LOGGED_IN, outcome.status)
        assertEquals(listOf(listOf(cliPath.toString(), "whoami")), executor.commands)
        assertNull(outcome.loginResult)
    }

    @Test
    fun failedWhoamiTriggersLogin() {
        val executor = RecordingCliExecutor(
            CodexCliResult(1, "", "Not logged in"),
            CodexCliResult(0, "Login successful", "")
        )
        val task = CodexWhoamiTask(executor)

        val outcome = task.ensureLogin(cliPath)

        assertEquals(WhoamiStatus.LOGIN_TRIGGERED, outcome.status)
        assertEquals(2, executor.commands.size)
        assertEquals(listOf(cliPath.toString(), "login"), executor.commands.last())
    }

    @Test
    fun loginFailureReported() {
        val executor = RecordingCliExecutor(
            CodexCliResult(1, "", "Not logged"),
            CodexCliResult(2, "", "Auth failed")
        )
        val task = CodexWhoamiTask(executor)

        val outcome = task.ensureLogin(cliPath)

        assertEquals(WhoamiStatus.LOGIN_FAILED, outcome.status)
        assertEquals(2, executor.commands.size)
    }

    @Test
    fun zeroExitWithPromptStillTriggersLogin() {
        val executor = RecordingCliExecutor(
            CodexCliResult(0, "Please log in", ""),
            CodexCliResult(0, "Login done", "")
        )
        val task = CodexWhoamiTask(executor)

        val outcome = task.ensureLogin(cliPath)

        assertEquals(WhoamiStatus.LOGIN_TRIGGERED, outcome.status)
    }
}

private class RecordingCliExecutor(vararg results: CodexCliResult) : CodexCliExecutor {
    private val queue = ArrayDeque(results.asList())
    val commands = mutableListOf<List<String>>()

    override fun run(
        executable: java.nio.file.Path,
        arguments: List<String>,
        workingDirectory: java.nio.file.Path?,
        environment: Map<String, String>
    ): CodexCliResult {
        commands.add(buildList {
            add(executable.toString())
            addAll(arguments)
        })
        return queue.removeFirstOrNull() ?: error("No more results")
    }
}
