package dev.curt.codexjb.proto

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import dev.curt.codexjb.core.CodexLogger
import dev.curt.codexjb.core.LogSink
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * App Server client that communicates with Codex CLI using JSON-RPC protocol over stdin/stdout.
 *
 * Protocol flow:
 * 1. initialize → response → initialized notification
 * 2. newConversation → response with conversationId
 * 3. addConversationListener → response with subscriptionId
 * 4. sendUserMessage → events stream via notifications
 * 5. Handle server requests (execCommandApproval, applyPatchApproval)
 */
class AppServerClient(
    private val stdin: BufferedWriter,
    private val eventBus: EventBus
) {
    private val log: LogSink = CodexLogger.forClass(AppServerClient::class.java)
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<RequestId, CompletableFuture<JsonElement>>()

    // Handlers for server-initiated requests
    var execApprovalHandler: ((ExecApprovalRequest) -> ApprovalDecision)? = null
    var patchApprovalHandler: ((PatchApprovalRequest) -> ApprovalDecision)? = null

    /**
     * Process an incoming JSON-RPC message from the server.
     */
    fun processMessage(msg: JsonRpcMessage) {
        when (msg) {
            is JsonRpcMessage.Response -> handleResponse(msg.value)
            is JsonRpcMessage.Error -> handleError(msg.value)
            is JsonRpcMessage.Request -> handleServerRequest(msg.value)
            is JsonRpcMessage.Notification -> handleNotification(msg.value)
        }
    }

    /**
     * Send initialize request and wait for response.
     */
    fun initialize(clientName: String, clientVersion: String): CompletableFuture<InitializeResponse> {
        val params = JsonObject().apply {
            val clientInfo = JsonObject().apply {
                addProperty("name", clientName)
                addProperty("version", clientVersion)
            }
            add("clientInfo", clientInfo)
        }

        return sendRequest("initialize", params).thenApply { result ->
            val userAgent = result.asJsonObject["userAgent"]?.asString ?: "unknown"

            // Send initialized notification
            sendNotification("initialized", null)

            InitializeResponse(userAgent)
        }
    }

    /**
     * Create a new conversation.
     */
    fun newConversation(params: NewConversationParams): CompletableFuture<NewConversationResponse> {
        val jsonParams = JsonObject().apply {
            params.model?.let { addProperty("model", it) }
            params.cwd?.let { addProperty("cwd", it) }
            params.approvalPolicy?.let { addProperty("approvalPolicy", it) }
            params.sandbox?.let { addProperty("sandbox", it) }
        }

        return sendRequest("newConversation", jsonParams).thenApply { result ->
            val obj = result.asJsonObject
            val conversationId = obj["conversationId"]?.asString ?: error("Missing conversationId")
            val model = obj["model"]?.asString ?: ""
            val rolloutPath = obj["rolloutPath"]?.asString

            NewConversationResponse(conversationId, model, rolloutPath)
        }
    }

    /**
     * Subscribe to conversation events.
     */
    fun addConversationListener(conversationId: String): CompletableFuture<String> {
        val params = JsonObject().apply {
            addProperty("conversationId", conversationId)
        }

        return sendRequest("addConversationListener", params).thenApply { result ->
            result.asJsonObject["subscriptionId"]?.asString ?: error("Missing subscriptionId")
        }
    }

    /**
     * Send user message to conversation.
     */
    fun sendUserMessage(conversationId: String, text: String): CompletableFuture<Unit> {
        val params = JsonObject().apply {
            addProperty("conversationId", conversationId)
            val items = com.google.gson.JsonArray().apply {
                val item = JsonObject().apply {
                    addProperty("type", "text")
                    val data = JsonObject().apply {
                        addProperty("text", text)
                    }
                    add("data", data)
                }
                add(item)
            }
            add("items", items)
        }

        return sendRequest("sendUserMessage", params).thenApply { }
    }

    /**
     * Interrupt the current conversation turn.
     */
    fun interruptConversation(conversationId: String): CompletableFuture<Unit> {
        val params = JsonObject().apply {
            addProperty("conversationId", conversationId)
        }

        return sendRequest("interruptConversation", params).thenApply { }
    }

    /**
     * Send a JSON-RPC request and return a future for the response.
     */
    private fun sendRequest(method: String, params: JsonObject?): CompletableFuture<JsonElement> {
        val id = nextId.getAndIncrement().toString()
        val request = JsonRpcRequest(id, method, params)
        val future = CompletableFuture<JsonElement>()

        pendingRequests[id] = future

        try {
            val json = JsonRpcParser.encodeRequest(request)
            log.info("→ $json")
            synchronized(stdin) {
                stdin.write(json)
                stdin.write("\n")
                stdin.flush()
            }
        } catch (e: IOException) {
            pendingRequests.remove(id)
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Send a JSON-RPC notification (no response expected).
     */
    private fun sendNotification(method: String, params: JsonObject?) {
        try {
            val notification = JsonRpcNotification(method, params)
            val json = JsonRpcParser.encodeNotification(notification)
            log.info("→ notification: $json")
            synchronized(stdin) {
                stdin.write(json)
                stdin.write("\n")
                stdin.flush()
            }
        } catch (e: IOException) {
            log.error("Failed to send notification: ${e.message}")
        }
    }

    /**
     * Handle a response from the server.
     */
    private fun handleResponse(resp: JsonRpcResponse) {
        val future = pendingRequests.remove(resp.id)
        if (future != null) {
            future.complete(resp.result)
        } else {
            log.warn("Received response for unknown request ID: ${resp.id}")
        }
    }

    /**
     * Handle an error from the server.
     */
    private fun handleError(err: JsonRpcError) {
        val future = pendingRequests.remove(err.id)
        if (future != null) {
            val exception = Exception("JSON-RPC error ${err.error.code}: ${err.error.message}")
            future.completeExceptionally(exception)
        } else {
            log.error("Received error for unknown request ID: ${err.id}: ${err.error.message}")
        }
    }

    /**
     * Handle a server-initiated request (approval).
     */
    private fun handleServerRequest(req: JsonRpcRequest) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                when (req.method) {
                    "execCommandApproval" -> handleExecApproval(req)
                    "applyPatchApproval" -> handlePatchApproval(req)
                    else -> {
                        log.warn("Unknown server request method: ${req.method}")
                        sendErrorResponse(req.id, -32601, "Method not found")
                    }
                }
            } catch (e: Exception) {
                log.error("Error handling server request: ${e.message}")
                sendErrorResponse(req.id, -32603, "Internal error: ${e.message}")
            }
        }
    }

    /**
     * Handle exec command approval request.
     */
    private fun handleExecApproval(req: JsonRpcRequest) {
        val params = req.params ?: run {
            sendErrorResponse(req.id, -32602, "Invalid params")
            return
        }

        val conversationId = params["conversationId"]?.asString ?: ""
        val callId = params["callId"]?.asString ?: ""
        val commandArray = params["command"]?.asJsonArray ?: com.google.gson.JsonArray()
        val command = commandArray.map { it.asString }
        val cwd = params["cwd"]?.asString ?: ""
        val reason = params["reason"]?.asString

        val request = ExecApprovalRequest(conversationId, callId, command, cwd, reason)
        val decision = execApprovalHandler?.invoke(request) ?: ApprovalDecision.DENIED

        sendApprovalResponse(req.id, decision)
    }

    /**
     * Handle patch approval request.
     */
    private fun handlePatchApproval(req: JsonRpcRequest) {
        val params = req.params ?: run {
            sendErrorResponse(req.id, -32602, "Invalid params")
            return
        }

        val conversationId = params["conversationId"]?.asString ?: ""
        val callId = params["callId"]?.asString ?: ""
        val fileChanges = params["fileChanges"]?.asJsonObject ?: JsonObject()
        val reason = params["reason"]?.asString

        val request = PatchApprovalRequest(conversationId, callId, fileChanges, reason)
        val decision = patchApprovalHandler?.invoke(request) ?: ApprovalDecision.DENIED

        sendApprovalResponse(req.id, decision)
    }

    /**
     * Send approval decision response.
     */
    private fun sendApprovalResponse(id: RequestId, decision: ApprovalDecision) {
        val result = JsonObject().apply {
            addProperty("decision", decision.value)
        }
        val response = JsonRpcResponse(id, result)
        val json = JsonRpcParser.encodeResponse(response)

        try {
            synchronized(stdin) {
                stdin.write(json)
                stdin.write("\n")
                stdin.flush()
            }
        } catch (e: IOException) {
            log.error("Failed to send approval response: ${e.message}")
        }
    }

    /**
     * Send error response.
     */
    private fun sendErrorResponse(id: RequestId, code: Int, message: String) {
        val error = JsonRpcError(id, JsonRpcError.ErrorObject(code, message))
        val json = JsonRpcParser.encodeError(error)

        try {
            synchronized(stdin) {
                stdin.write(json)
                stdin.write("\n")
                stdin.flush()
            }
        } catch (e: IOException) {
            log.error("Failed to send error response: ${e.message}")
        }
    }

    /**
     * Handle a notification from the server.
     */
    private fun handleNotification(note: JsonRpcNotification) {
        when {
            note.method.startsWith("codex/event/") -> {
                // Extract event type from method name
                val eventType = note.method.removePrefix("codex/event/")
                log.info("← event: $eventType")

                // Forward to event bus
                val params = note.params ?: JsonObject()
                val id = params["id"]?.asString ?: ""
                val msg = params["msg"]?.asJsonObject ?: JsonObject()

                eventBus.dispatchEvent(id, msg)
            }
            note.method == "sessionConfigured" -> {
                log.info("← sessionConfigured")
                val params = note.params ?: JsonObject()
                eventBus.dispatchEvent("", params)
            }
            else -> {
                log.info("← notification: ${note.method}")
            }
        }
    }
}

// Data classes for requests/responses

data class InitializeResponse(
    val userAgent: String
)

data class NewConversationParams(
    val model: String? = null,
    val cwd: String? = null,
    val approvalPolicy: String? = null,
    val sandbox: String? = null
)

data class NewConversationResponse(
    val conversationId: String,
    val model: String,
    val rolloutPath: String?
)

data class ExecApprovalRequest(
    val conversationId: String,
    val callId: String,
    val command: List<String>,
    val cwd: String,
    val reason: String?
)

data class PatchApprovalRequest(
    val conversationId: String,
    val callId: String,
    val fileChanges: JsonObject,
    val reason: String?
)

enum class ApprovalDecision(val value: String) {
    APPROVED("approved"),
    APPROVED_FOR_SESSION("approved_for_session"),
    DENIED("denied"),
    ABORT("abort")
}
