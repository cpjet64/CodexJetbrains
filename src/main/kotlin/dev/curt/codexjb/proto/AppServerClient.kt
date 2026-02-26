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
 * Protocol flow (V2-first with legacy fallback):
 * 1. initialize → response → initialized notification
 * 2. thread/start (fallback: newConversation)
 * 3. turn/start (fallback: sendUserMessage)
 * 4. stream events via notifications (turn and item namespaces, or legacy codex/event namespace)
 * 5. handle server requests (v2 item approvals + legacy approval requests)
 */
class AppServerClient(
    private val stdin: BufferedWriter,
    private val eventBus: EventBus
) {
    private data class RequestAttempt(val method: String, val params: JsonObject?)

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
            val capabilities = JsonObject().apply {
                addProperty("experimentalApi", true)
            }
            add("capabilities", capabilities)
        }

        return sendRequest("initialize", params).thenApply { result ->
            val userAgent = result.asJsonObject["userAgent"]?.asString ?: "unknown"

            // Send initialized notification
            sendNotification("initialized", null)

            InitializeResponse(userAgent)
        }
    }

    /**
     * Start a thread (V2). Falls back to legacy newConversation.
     */
    fun threadStart(params: NewConversationParams): CompletableFuture<ThreadStartResponse> {
        val v2Params = JsonObject().apply {
            params.model?.let { addProperty("model", it) }
            params.cwd?.let { addProperty("cwd", it) }
            params.approvalPolicy?.let { addProperty("approvalPolicy", it) }
            params.sandbox?.let { addProperty("sandbox", it) }
        }

        return sendRequestWithFallbackAttempts(
            listOf(
                RequestAttempt("thread/start", v2Params),
                RequestAttempt("threadStart", v2Params),
                RequestAttempt(
                    "newConversation",
                    JsonObject().apply {
                        params.model?.let { addProperty("model", it) }
                        params.cwd?.let { addProperty("cwd", it) }
                        params.approvalPolicy?.let { addProperty("approvalPolicy", it) }
                        params.sandbox?.let { addProperty("sandbox", it) }
                    }
                )
            )
        ).thenApply { result ->
            val obj = result.asJsonObject
            val threadObj = obj["thread"]?.takeIf { it.isJsonObject }?.asJsonObject
            val threadId = threadObj?.get("id")?.asString
                ?: obj["threadId"]?.asString
                ?: obj["conversationId"]?.asString
                ?: error("Missing thread id")
            val model = threadObj?.get("model")?.asString ?: obj["model"]?.asString
            val rolloutPath = obj["rolloutPath"]?.asString
            ThreadStartResponse(threadId, model, rolloutPath)
        }
    }

    /**
     * Create a new conversation.
     * Compatibility wrapper on top of V2 thread/start.
     */
    fun newConversation(params: NewConversationParams): CompletableFuture<NewConversationResponse> {
        return threadStart(params).thenApply { thread ->
            NewConversationResponse(
                conversationId = thread.threadId,
                model = thread.model ?: "",
                rolloutPath = thread.rolloutPath
            )
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
     * V2-first: turn/start with input text. Falls back to legacy sendUserMessage.
     */
    fun sendUserMessage(conversationId: String, text: String): CompletableFuture<Unit> {
        val v2Params = JsonObject().apply {
            addProperty("threadId", conversationId)
            val input = com.google.gson.JsonArray().apply {
                val item = JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", text)
                }
                add(item)
            }
            add("input", input)
        }
        val legacyParams = JsonObject().apply {
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

        return sendRequestWithFallbackAttempts(
            listOf(
                RequestAttempt("turn/start", v2Params),
                RequestAttempt("turnStart", v2Params),
                RequestAttempt("sendUserMessage", legacyParams)
            )
        ).thenApply { }
    }

    /**
     * Interrupt the current conversation turn.
     * V2-first: turn/interrupt. Falls back to legacy interruptConversation.
     */
    fun interruptConversation(conversationId: String): CompletableFuture<Unit> {
        val v2Params = JsonObject().apply {
            addProperty("threadId", conversationId)
        }
        val legacyParams = JsonObject().apply {
            addProperty("conversationId", conversationId)
        }

        return sendRequestWithFallbackAttempts(
            listOf(
                RequestAttempt("turn/interrupt", v2Params),
                RequestAttempt("turnInterrupt", v2Params),
                RequestAttempt("interruptConversation", legacyParams)
            )
        ).thenApply { }
    }

    /**
     * List MCP tools using the JSON-RPC app server.
     * Supports both camelCase and snake_case method variants for compatibility.
     */
    fun listMcpTools(conversationId: String?): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            if (!conversationId.isNullOrBlank()) addProperty("conversationId", conversationId)
        }
        return sendRequestWithFallback(listOf("listMcpTools", "list_mcp_tools"), params)
    }

    /**
     * List custom prompts using the JSON-RPC app server.
     * Supports both camelCase and snake_case method variants for compatibility.
     */
    fun listCustomPrompts(conversationId: String?): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            if (!conversationId.isNullOrBlank()) addProperty("conversationId", conversationId)
        }
        return sendRequestWithFallback(listOf("listCustomPrompts", "list_custom_prompts"), params)
    }

    /**
     * Run an MCP tool. Falls back across common method name variants.
     */
    fun runMcpTool(conversationId: String?, toolName: String): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            if (!conversationId.isNullOrBlank()) addProperty("conversationId", conversationId)
            addProperty("tool", toolName)
            addProperty("name", toolName)
        }
        return sendRequestWithFallback(listOf("runMcpTool", "run_mcp_tool", "callMcpTool", "call_mcp_tool"), params)
    }

    fun modelList(includeHidden: Boolean = false, limit: Int? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            addProperty("includeHidden", includeHidden)
            limit?.let { addProperty("limit", it) }
        }
        return sendRequestWithFallback(listOf("model/list", "modelList"), params)
    }

    fun appList(
        threadId: String? = null,
        cursor: String? = null,
        limit: Int? = null,
        forceRefetch: Boolean? = null
    ): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            threadId?.let { addProperty("threadId", it) }
            cursor?.let { addProperty("cursor", it) }
            limit?.let { addProperty("limit", it) }
            forceRefetch?.let { addProperty("forceRefetch", it) }
        }
        return sendRequestWithFallback(listOf("app/list", "appList"), params)
    }

    fun skillsList(cwds: List<String>? = null, forceReload: Boolean? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            cwds?.let {
                val arr = com.google.gson.JsonArray()
                it.forEach(arr::add)
                add("cwds", arr)
            }
            forceReload?.let { addProperty("forceReload", it) }
        }
        return sendRequestWithFallback(listOf("skills/list", "skillsList"), params)
    }

    fun mcpServerStatusList(cursor: String? = null, limit: Int? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            cursor?.let { addProperty("cursor", it) }
            limit?.let { addProperty("limit", it) }
        }
        return sendRequestWithFallback(listOf("mcpServerStatus/list", "mcpServerStatusList"), params)
    }

    fun configRead(includeLayers: Boolean? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            includeLayers?.let { addProperty("includeLayers", it) }
        }
        return sendRequestWithFallback(listOf("config/read", "configRead"), params)
    }

    fun configValueWrite(keyPath: String, value: JsonElement, mergeStrategy: String? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            addProperty("keyPath", keyPath)
            add("value", value)
            mergeStrategy?.let { addProperty("mergeStrategy", it) }
        }
        return sendRequestWithFallback(listOf("config/value/write", "configValueWrite"), params)
    }

    fun configBatchWrite(edits: com.google.gson.JsonArray): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            add("edits", edits)
        }
        return sendRequestWithFallback(listOf("config/batchWrite", "configBatchWrite"), params)
    }

    fun accountRead(refreshToken: Boolean? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            refreshToken?.let { addProperty("refreshToken", it) }
        }
        return sendRequestWithFallback(listOf("account/read", "accountRead"), params)
    }

    fun accountLoginStart(params: JsonObject): CompletableFuture<JsonElement> =
        sendRequestWithFallback(listOf("account/login/start", "accountLoginStart"), params)

    fun accountLogout(): CompletableFuture<JsonElement> =
        sendRequestWithFallback(listOf("account/logout", "accountLogout"), JsonObject())

    fun accountRateLimitsRead(): CompletableFuture<JsonElement> =
        sendRequestWithFallback(listOf("account/rateLimits/read", "accountRateLimitsRead"), JsonObject())

    fun commandExec(command: List<String>, cwd: String? = null): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            val cmd = com.google.gson.JsonArray()
            command.forEach(cmd::add)
            add("command", cmd)
            cwd?.let { addProperty("cwd", it) }
        }
        return sendRequestWithFallback(listOf("command/exec", "commandExec"), params)
    }

    fun reviewStart(threadId: String): CompletableFuture<JsonElement> {
        val params = JsonObject().apply {
            addProperty("threadId", threadId)
        }
        return sendRequestWithFallback(listOf("review/start", "reviewStart"), params)
    }

    fun toolRequestUserInput(params: JsonObject): CompletableFuture<JsonElement> =
        sendRequestWithFallback(listOf("tool/requestUserInput", "toolRequestUserInput"), params)

    private fun sendRequestWithFallback(methods: List<String>, params: JsonObject?): CompletableFuture<JsonElement> {
        return sendRequestWithFallbackAttempts(methods.map { RequestAttempt(it, params) })
    }

    private fun sendRequestWithFallbackAttempts(attempts: List<RequestAttempt>): CompletableFuture<JsonElement> {
        require(attempts.isNotEmpty()) { "At least one request attempt must be provided" }
        val result = CompletableFuture<JsonElement>()

        fun attempt(index: Int, lastError: Throwable?) {
            if (index >= attempts.size) {
                result.completeExceptionally(lastError ?: IllegalStateException("No methods attempted"))
                return
            }

            val next = attempts[index]
            sendRequest(next.method, next.params)
                .whenComplete { value, error ->
                    when {
                        error == null -> result.complete(value)
                        index < attempts.lastIndex -> attempt(index + 1, error)
                        else -> result.completeExceptionally(error)
                    }
                }
        }

        attempt(0, null)
        return result
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
        val runner: (() -> Unit) -> Unit = { block ->
            val app = runCatching { ApplicationManager.getApplication() }.getOrNull()
            if (app != null) app.executeOnPooledThread { block() } else block()
        }
        runner {
            try {
                when (req.method) {
                    "execCommandApproval" -> handleExecApproval(req)
                    "applyPatchApproval" -> handlePatchApproval(req)
                    "item/commandExecution/requestApproval" -> handleExecApprovalV2(req)
                    "item/fileChange/requestApproval" -> handlePatchApprovalV2(req)
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

        sendApprovalResponseLegacy(req.id, decision)
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

        sendApprovalResponseLegacy(req.id, decision)
    }

    /**
     * Handle v2 command approval request.
     */
    private fun handleExecApprovalV2(req: JsonRpcRequest) {
        val params = req.params ?: run {
            sendErrorResponse(req.id, -32602, "Invalid params")
            return
        }
        val cmdArray = params["command"]?.takeIf { it.isJsonArray }?.asJsonArray
        val command = cmdArray?.map { it.asString } ?: emptyList()
        val cwd = params["cwd"]?.asString ?: ""
        val reason = params["reason"]?.asString
        val threadId = params["threadId"]?.asString ?: ""
        val callId = params["itemId"]?.asString ?: params["callId"]?.asString ?: ""

        val request = ExecApprovalRequest(threadId, callId, command, cwd, reason)
        val decision = execApprovalHandler?.invoke(request) ?: ApprovalDecision.DENIED
        sendApprovalResponseV2(req.id, decision)
    }

    /**
     * Handle v2 file change approval request.
     */
    private fun handlePatchApprovalV2(req: JsonRpcRequest) {
        val params = req.params ?: run {
            sendErrorResponse(req.id, -32602, "Invalid params")
            return
        }
        val threadId = params["threadId"]?.asString ?: ""
        val callId = params["itemId"]?.asString ?: params["callId"]?.asString ?: ""
        val reason = params["reason"]?.asString

        val changesObj = JsonObject().apply {
            params["changes"]?.let { add("changes", it) }
            params["grantRoot"]?.let { add("grantRoot", it) }
        }

        val request = PatchApprovalRequest(threadId, callId, changesObj, reason)
        val decision = patchApprovalHandler?.invoke(request) ?: ApprovalDecision.DENIED
        sendApprovalResponseV2(req.id, decision)
    }

    private fun sendApprovalResponseLegacy(id: RequestId, decision: ApprovalDecision) {
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

    private fun sendApprovalResponseV2(id: RequestId, decision: ApprovalDecision) {
        val mapped = when (decision) {
            ApprovalDecision.APPROVED -> "accept"
            ApprovalDecision.APPROVED_FOR_SESSION -> "acceptForSession"
            ApprovalDecision.DENIED -> "decline"
            ApprovalDecision.ABORT -> "cancel"
        }
        val result = JsonObject().apply {
            addProperty("decision", mapped)
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
            log.error("Failed to send v2 approval response: ${e.message}")
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
            note.method == "turn/started" -> {
                val turnId = note.params?.getAsJsonObject("turn")?.get("id")?.asString
                    ?: note.params?.get("turnId")?.asString
                    ?: ""
                dispatchTypedEvent(turnId, "task_started")
            }
            note.method == "turn/completed" -> {
                val turnId = note.params?.getAsJsonObject("turn")?.get("id")?.asString
                    ?: note.params?.get("turnId")?.asString
                    ?: ""
                dispatchTypedEvent(turnId, "task_complete")
            }
            note.method == "turn/diff/updated" -> {
                val turnId = note.params?.get("turnId")?.asString ?: ""
                val diffEl = note.params?.get("diff")
                val diffText = when {
                    diffEl == null -> ""
                    diffEl.isJsonPrimitive -> diffEl.asString
                    else -> diffEl.toString()
                }
                dispatchTypedEvent(turnId, "turn_diff") {
                    addProperty("diff", diffText)
                    addProperty("text", diffText)
                }
            }
            note.method == "item/agentMessage/delta" -> {
                val turnId = note.params?.get("turnId")?.asString ?: ""
                val delta = note.params?.get("delta")?.asString
                    ?: note.params?.get("textDelta")?.asString
                    ?: ""
                dispatchTypedEvent(turnId, "AgentMessageDelta") {
                    addProperty("delta", delta)
                }
            }
            note.method == "item/reasoning/textDelta" || note.method == "item/reasoning/summaryTextDelta" -> {
                val turnId = note.params?.get("turnId")?.asString ?: ""
                val delta = note.params?.get("delta")?.asString
                    ?: note.params?.get("textDelta")?.asString
                    ?: ""
                dispatchTypedEvent(turnId, "agent_reasoning_delta") {
                    addProperty("delta", delta)
                }
            }
            note.method == "item/reasoning/summaryPartAdded" -> {
                val turnId = note.params?.get("turnId")?.asString ?: ""
                dispatchTypedEvent(turnId, "agent_reasoning_section_break")
            }
            note.method == "item/started" -> {
                val turnId = note.params?.get("turnId")?.asString ?: ""
                val item = note.params?.getAsJsonObject("item")
                val itemType = item?.get("type")?.asString
                if (itemType == "mcpToolCall") {
                    dispatchTypedEvent(turnId, "mcp_tool_call_begin") {
                        val invocation = JsonObject().apply {
                            add("tool", item.get("tool"))
                            add("server", item.get("server"))
                            add("name", item.get("tool"))
                        }
                        add("invocation", invocation)
                    }
                }
            }
            note.method == "item/completed" -> {
                val turnId = note.params?.get("turnId")?.asString ?: ""
                val item = note.params?.getAsJsonObject("item")
                val itemType = item?.get("type")?.asString
                when (itemType) {
                    "agentMessage" -> dispatchTypedEvent(turnId, "AgentMessage")
                    "mcpToolCall" -> {
                        dispatchTypedEvent(turnId, "mcp_tool_call_end") {
                            val invocation = JsonObject().apply {
                                add("tool", item.get("tool"))
                                add("server", item.get("server"))
                                add("name", item.get("tool"))
                            }
                            add("invocation", invocation)
                            item.get("result")?.let { add("result", it) }
                            item.get("error")?.let { add("error", it) }
                        }
                    }
                }
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

    private fun dispatchTypedEvent(id: String, type: String, populate: (JsonObject.() -> Unit)? = null) {
        val msg = JsonObject().apply {
            addProperty("type", type)
            if (populate != null) populate()
        }
        eventBus.dispatchEvent(id, msg)
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

data class ThreadStartResponse(
    val threadId: String,
    val model: String?,
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
