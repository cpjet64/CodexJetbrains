package dev.curt.codexjb.proto

import com.intellij.openapi.application.ApplicationManager
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.CodexProcessConfig
import dev.curt.codexjb.core.CodexProcessService
import dev.curt.codexjb.core.LogSink
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
) {
    private val log: LogSink = CodexLogger.forClass(AppServerProtocol::class.java)
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private var client: AppServerClient? = null
    private var currentConversationId: String? = null

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
            // TODO: Show UI dialog to user
            log.warn("Auto-approving exec: ${request.command}")
            ApprovalDecision.APPROVED
        }

        appClient.patchApprovalHandler = { request ->
            // TODO: Show UI dialog to user
            log.warn("Auto-approving patch: ${request.fileChanges.keySet().size} files")
            ApprovalDecision.APPROVED
        }

        // Start stderr reader thread
        val stderrReader = BufferedReader(InputStreamReader(stderr, StandardCharsets.UTF_8))
        val stderrThread = Thread({
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

                // Create a new conversation
                val params = NewConversationParams(
                    model = null, // Use default
                    cwd = processConfig.workingDirectory?.toString(),
                    approvalPolicy = "on-request",
                    sandbox = "workspace-write"
                )

                appClient.newConversation(params)
            }
            .thenCompose { convResp ->
                log.info("Conversation created with ID: ${convResp.conversationId}")
                currentConversationId = convResp.conversationId
                log.info("Stored conversationId for subsequent messages: $currentConversationId")

                // Subscribe to events
                appClient.addConversationListener(convResp.conversationId)
            }
            .thenApply { subscriptionId ->
                log.info("Subscribed to events: $subscriptionId")
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

    fun isRunning(): Boolean = running.get()
}

// Extension to get stdin OutputStream from CodexProcessService
private fun CodexProcessService.getStdinOutputStream(): java.io.OutputStream? {
    val handle = this.currentHandle() ?: return null
    return handle.stdin.asOutputStream()
}

