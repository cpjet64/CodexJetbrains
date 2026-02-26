package dev.curt.codexjb.proto

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import dev.curt.codexjb.core.CodexConfigService
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.CodexProcessService
import dev.curt.codexjb.core.LogSink
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the App Server protocol lifecycle:
 * - Starts stdout reader thread
 * - Creates AppServerClient
 * - Handles initialize/newConversation flow
 */
class AppServerProtocol(
    private val processService: CodexProcessService,
    private val processConfig: CodexProcessConfig,
    private val eventBus: EventBus
) : Disposable {
    private val log: LogSink = CodexLogger.forClass(AppServerProtocol::class.java)
    private val running = AtomicBoolean(false)
    private val approvalStore = ApprovalStore()
    private var readerThread: Thread? = null
    private var client: AppServerClient? = null
    private var currentConversationId: String? = null
    @Volatile var legacyOpObserver: ((String) -> Unit)? = null

    /**
     * Start the protocol and initialize the connection.
     */
    fun start(): CompletableFuture<String> {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(IllegalStateException("Already started"))
        }

        // Start the process
        val started = processService.start(processConfig)
        if (!started) {
            running.set(false)
            return CompletableFuture.failedFuture(IllegalStateException("Failed to start process"))
        }

        // Get streams
        val streams = processService.streams()
            ?: return CompletableFuture.failedFuture(IllegalStateException("No streams available"))

        val (stdout, stderr) = streams

        // Create readers/writers
        val stdoutReader = BufferedReader(InputStreamReader(stdout, StandardCharsets.UTF_8))

        // Get stdin writer from process - create a new BufferedWriter that wraps the raw OutputStream
        // (we can't use the existing BufferedWriter from the process handle because it's already being used)
        val stdinOutputStream = processService.getStdinOutputStream()
            ?: return CompletableFuture.failedFuture(IllegalStateException("No stdin available"))

        val stdinWriter = BufferedWriter(
            OutputStreamWriter(stdinOutputStream, StandardCharsets.UTF_8)
        )

        // Create client
        val appClient = AppServerClient(stdinWriter, eventBus)
        client = appClient

        // Set approval handlers
        appClient.execApprovalHandler = { request ->
            handleExecApproval(request)
        }

        appClient.patchApprovalHandler = { request ->
            handlePatchApproval(request)
        }

        // Start stderr reader thread
        val stderrReader = BufferedReader(InputStreamReader(stderr, StandardCharsets.UTF_8))
        Thread({
            try {
                while (running.get()) {
                    val line = stderrReader.readLine() ?: break
                    log.warn("stderr: $line")
                    dev.curt.codexjb.core.DiagnosticsService.append(line)
                }
            } catch (e: Exception) {
                if (running.get()) {
                    log.error("Stderr reader error: ${e.message}")
                }
            }
        }, "Codex-AppServer-Stderr").apply {
            isDaemon = true
            start()
        }

        // Start reader thread
        readerThread = Thread({
            try {
                while (running.get()) {
                    val line = stdoutReader.readLine() ?: break
                    if (line.isBlank()) continue

                    log.info("← $line")

                    // Filter out TUI output (box drawing, ANSI escape codes, progress spinners)
                    // Only log lines that look like JSON-RPC or useful diagnostics
                    val isTuiOutput = line.contains(Regex("[╭╮╯╰│─▌■]")) ||  // Box drawing chars
                                      line.contains(Regex("\u001B\\[")) ||    // ANSI escape codes
                                      line.matches(Regex(".*Working\\(\\d+s.*")) || // Progress spinners
                                      line.matches(Regex(".*Considering.*interrupt\\).*"))

                    if (!isTuiOutput) {
                        dev.curt.codexjb.core.DiagnosticsService.append("• stdout received: $line")
                    }

                    val msg = JsonRpcParser.parse(line)
                    if (msg != null) {
                        appClient.processMessage(msg)
                    } else {
                        if (!isTuiOutput) {
                            log.warn("Failed to parse JSON-RPC message: $line")
                        }
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    log.error("Reader thread error: ${e.message}")
                }
            }
        }, "Codex-AppServer-Reader").apply {
            isDaemon = true
            start()
        }

        // Initialize the connection
        return appClient.initialize("CodexJetbrains", "0.1.0")
            .thenCompose { initResp ->
                log.info("Initialized: ${initResp.userAgent}")

                // Start a thread (v2-first with legacy fallback)
                val params = NewConversationParams(
                    model = null, // Use default
                    cwd = processConfig.workingDirectory?.toString(),
                    approvalPolicy = "on-request",
                    sandbox = "workspace-write"
                )

                appClient.threadStart(params)
            }
            .thenCompose { threadResp ->
                log.info("Thread started with ID: ${threadResp.threadId}")
                currentConversationId = threadResp.threadId
                log.info("Stored conversationId for subsequent messages: $currentConversationId")

                // Legacy event subscription call (v2 typically auto-subscribes).
                // Do not fail startup if this endpoint is unavailable.
                appClient.addConversationListener(threadResp.threadId)
                    .exceptionally { err ->
                        log.info("addConversationListener unavailable or failed: ${err.message}")
                        ""
                    }
            }
            .thenApply { subscriptionId ->
                if (subscriptionId.isNotBlank()) {
                    log.info("Subscribed to events: $subscriptionId")
                }
                currentConversationId!!
            }
    }

    /**
     * Send a user message.
     */
    fun sendMessage(text: String): CompletableFuture<Unit> {
        val convId = currentConversationId
            ?: return CompletableFuture.failedFuture(IllegalStateException("No active conversation"))

        val appClient = client
            ?: return CompletableFuture.failedFuture(IllegalStateException("Not started"))

        log.info("Sending message to conversation: $convId")
        return appClient.sendUserMessage(convId, text)
    }

    /**
     * Interrupt the current turn.
     */
    fun interrupt(): CompletableFuture<Unit> {
        val convId = currentConversationId
            ?: return CompletableFuture.failedFuture(IllegalStateException("No active conversation"))

        val appClient = client
            ?: return CompletableFuture.failedFuture(IllegalStateException("Not started"))

        return appClient.interruptConversation(convId)
    }

    fun sendLegacyOp(op: String): CompletableFuture<Unit> {
        legacyOpObserver?.invoke(op)
        return when (op) {
            "list_mcp_tools" -> listMcpTools()
            "list_custom_prompts" -> listCustomPrompts()
            else -> CompletableFuture.failedFuture(IllegalArgumentException("Unsupported op: $op"))
        }
    }

    fun listMcpTools(): CompletableFuture<Unit> {
        val appClient = client
            ?: return CompletableFuture.failedFuture(IllegalStateException("Not started"))
        val convId = currentConversationId
        return appClient.listMcpTools(convId)
            .thenAccept { result ->
                val payload = JsonObject().apply {
                    addProperty("type", "mcp_list_tools_response")
                    when {
                        result.isJsonObject && result.asJsonObject.has("tools") -> add("tools", result.asJsonObject.get("tools"))
                        result.isJsonArray -> add("tools", result)
                        result.isJsonObject -> add("tools", result)
                    }
                }
                eventBus.dispatchEvent(convId ?: "", payload)
            }
            .exceptionally { error ->
                val payload = JsonObject().apply {
                    addProperty("type", "mcp_tools_error")
                    addProperty("message", error.message ?: "Failed to list MCP tools")
                }
                eventBus.dispatchEvent(convId ?: "", payload)
                throw error
            }
            .thenApply { Unit }
    }

    fun listCustomPrompts(): CompletableFuture<Unit> {
        val appClient = client
            ?: return CompletableFuture.failedFuture(IllegalStateException("Not started"))
        val convId = currentConversationId
        return appClient.listCustomPrompts(convId)
            .thenAccept { result ->
                val payload = JsonObject().apply {
                    addProperty("type", "list_custom_prompts_response")
                    when {
                        result.isJsonObject && result.asJsonObject.has("custom_prompts") ->
                            add("custom_prompts", result.asJsonObject.get("custom_prompts"))
                        result.isJsonObject && result.asJsonObject.has("prompts") ->
                            add("custom_prompts", result.asJsonObject.get("prompts"))
                        result.isJsonArray -> add("custom_prompts", result)
                    }
                }
                eventBus.dispatchEvent(convId ?: "", payload)
            }
            .thenApply { Unit }
    }

    fun runMcpTool(toolName: String): CompletableFuture<Unit> {
        val appClient = client
            ?: return CompletableFuture.failedFuture(IllegalStateException("Not started"))
        val convId = currentConversationId
            ?: return CompletableFuture.failedFuture(IllegalStateException("No active conversation"))

        val result = CompletableFuture<Unit>()
        appClient.runMcpTool(convId, toolName)
            .whenComplete { _, err ->
                if (err == null) {
                    result.complete(Unit)
                } else {
                    log.warn("runMcpTool RPC failed for '$toolName', falling back to user message: ${err.message}")
                    sendMessage("Please run MCP tool: $toolName")
                        .whenComplete { _, fallbackErr ->
                            if (fallbackErr == null) result.complete(Unit)
                            else result.completeExceptionally(fallbackErr)
                        }
                }
            }
        return result
    }

    fun resetApprovalDecisions() {
        approvalStore.reset()
    }

    /**
     * Stop the protocol and cleanup.
     */
    fun stop() {
        running.set(false)
        readerThread?.interrupt()
        readerThread = null
        currentConversationId = null
        client = null
        processService.stop()
    }

    override fun dispose() {
        stop()
    }

    fun isRunning(): Boolean = running.get()

    private fun handleExecApproval(request: ExecApprovalRequest): ApprovalDecision {
        val commandText = request.command.joinToString(" ")
        val key = "${request.cwd}::$commandText"
        return when (currentApprovalMode()) {
            ApprovalMode.FULL_ACCESS -> ApprovalDecision.APPROVED_FOR_SESSION
            ApprovalMode.AGENT -> {
                val remembered = approvalStore.lookupExec(key)
                if (remembered != null) {
                    if (remembered) ApprovalDecision.APPROVED_FOR_SESSION else ApprovalDecision.DENIED
                } else {
                    val decision = promptApprovalDecision(
                        title = "Approve Command Execution",
                        details = buildString {
                            appendLine("Command:")
                            appendLine(commandText)
                            appendLine()
                            appendLine("Working directory:")
                            appendLine(request.cwd)
                            request.reason?.takeIf { it.isNotBlank() }?.let {
                                appendLine()
                                appendLine("Reason:")
                                appendLine(it)
                            }
                        },
                        allowSessionDecision = true
                    )
                    approvalStore.rememberExec(key, decision != ApprovalDecision.DENIED && decision != ApprovalDecision.ABORT)
                    decision
                }
            }
            ApprovalMode.CHAT -> promptApprovalDecision(
                title = "Approve Command Execution",
                details = buildString {
                    appendLine("Command:")
                    appendLine(commandText)
                    appendLine()
                    appendLine("Working directory:")
                    appendLine(request.cwd)
                    request.reason?.takeIf { it.isNotBlank() }?.let {
                        appendLine()
                        appendLine("Reason:")
                        appendLine(it)
                    }
                },
                allowSessionDecision = false
            )
        }
    }

    private fun handlePatchApproval(request: PatchApprovalRequest): ApprovalDecision {
        val files = request.fileChanges.keySet().sorted()
        val fileSummary = if (files.isEmpty()) "(none)" else files.joinToString("\n")
        val key = files.joinToString("|")
        return when (currentApprovalMode()) {
            ApprovalMode.FULL_ACCESS -> ApprovalDecision.APPROVED_FOR_SESSION
            ApprovalMode.AGENT -> {
                val remembered = approvalStore.lookupPatch(key)
                if (remembered != null) {
                    if (remembered) ApprovalDecision.APPROVED_FOR_SESSION else ApprovalDecision.DENIED
                } else {
                    val decision = promptApprovalDecision(
                        title = "Approve Patch Application",
                        details = buildString {
                            appendLine("Files:")
                            appendLine(fileSummary)
                            request.reason?.takeIf { it.isNotBlank() }?.let {
                                appendLine()
                                appendLine("Reason:")
                                appendLine(it)
                            }
                        },
                        allowSessionDecision = true
                    )
                    approvalStore.rememberPatch(key, decision != ApprovalDecision.DENIED && decision != ApprovalDecision.ABORT)
                    decision
                }
            }
            ApprovalMode.CHAT -> promptApprovalDecision(
                title = "Approve Patch Application",
                details = buildString {
                    appendLine("Files:")
                    appendLine(fileSummary)
                    request.reason?.takeIf { it.isNotBlank() }?.let {
                        appendLine()
                        appendLine("Reason:")
                        appendLine(it)
                    }
                },
                allowSessionDecision = false
            )
        }
    }

    private fun promptApprovalDecision(title: String, details: String, allowSessionDecision: Boolean): ApprovalDecision {
        val app = ApplicationManager.getApplication()
        if (app == null || app.isDisposed) {
            return ApprovalDecision.DENIED
        }

        var decision = ApprovalDecision.DENIED
        app.invokeAndWait {
            decision = if (allowSessionDecision) {
                val idx = Messages.showDialog(
                    details,
                    title,
                    arrayOf("Approve Once", "Approve for Session", "Deny"),
                    0,
                    Messages.getQuestionIcon()
                )
                when (idx) {
                    0 -> ApprovalDecision.APPROVED
                    1 -> ApprovalDecision.APPROVED_FOR_SESSION
                    else -> ApprovalDecision.DENIED
                }
            } else {
                val idx = Messages.showDialog(
                    details,
                    title,
                    arrayOf("Approve", "Deny"),
                    0,
                    Messages.getQuestionIcon()
                )
                if (idx == 0) ApprovalDecision.APPROVED else ApprovalDecision.DENIED
            }
        }
        return decision
    }

    private fun currentApprovalMode(): ApprovalMode {
        val cfg = ApplicationManager.getApplication()?.getService(CodexConfigService::class.java)
        val modeName = cfg?.lastApprovalMode ?: cfg?.defaultApprovalMode
        return runCatching { ApprovalMode.valueOf(modeName ?: ApprovalMode.CHAT.name) }
            .getOrDefault(ApprovalMode.CHAT)
    }
}

// Extension to get stdin OutputStream from CodexProcessService
private fun CodexProcessService.getStdinOutputStream(): java.io.OutputStream? {
    val handle = this.currentHandle() ?: return null
    return handle.stdin.asOutputStream()
}
