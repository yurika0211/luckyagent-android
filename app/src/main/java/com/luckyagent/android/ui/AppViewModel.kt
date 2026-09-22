package com.luckyagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckyagent.android.data.AppContainer
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.api.MemoryGraphEdge
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.data.api.MemoryStats
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.data.settings.ClientSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class AppDestination {
    Chat, Trajectory, Gateways, Skills, Settings, Memory
}

data class ChatBubble(
    val id: String,
    val role: String,
    val content: String,
    val streaming: Boolean = false,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolOutput: String? = null,
    val toolDone: Boolean = false,
    val toolSuccess: Boolean? = null,
    val stepId: String? = null,
)

data class AppUiState(
    val destination: AppDestination = AppDestination.Chat,
    val settings: ClientSettings = ClientSettings(),
    val sessions: List<RuntimeSession> = emptyList(),
    val sessionsLoading: Boolean = false,
    val sessionsError: String? = null,
    val sessionQuery: String = "",
    val bubbles: List<ChatBubble> = emptyList(),
    val composer: String = "",
    val socketState: SocketState = SocketState.Idle,
    val socketError: String? = null,
    val reconnectInfo: String? = null,
    val healthText: String? = null,
    val healthOk: Boolean? = null,
    val memoryEntries: List<MemoryEntry> = emptyList(),
    val memoryStats: MemoryStats? = null,
    val memoryGraphNodes: List<MemoryGraphNode> = emptyList(),
    val memoryGraphEdges: List<MemoryGraphEdge> = emptyList(),
    val memoryGraphSummary: String? = null,
    val memoryQuery: String = "project",
    val memoryLoading: Boolean = false,
    val memoryError: String? = null,
    val skillsJson: String? = null,
    val gatewaysJson: String? = null,
    val trajectoryJson: String? = null,
    val activityLine: String? = null,
    val drawerOpenHint: Boolean = false,
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(AppUiState(settings = container.settingsRepository.snapshot()))
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var eventsJob: Job? = null
    private var assistantBufferId: String? = null
    private val toolStepIndex = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { s ->
                _ui.update { it.copy(settings = s) }
            }
        }
        viewModelScope.launch {
            container.wsClient.state.collect { st ->
                _ui.update { it.copy(socketState = st) }
            }
        }
        viewModelScope.launch {
            container.wsClient.lastError.collect { err ->
                _ui.update { it.copy(socketError = err) }
            }
        }
        viewModelScope.launch {
            container.wsClient.reconnectInfo.collect { info ->
                _ui.update { it.copy(reconnectInfo = info) }
            }
        }
        observeWs()
        refreshSessions()
        connectSocket()
    }

    private fun observeWs() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            container.wsClient.events.collect { env ->
                when (env.type) {
                    "stream_chunk", "assistant_delta", "delta", "chunk" -> {
                        val piece = extractText(env.data) ?: return@collect
                        appendAssistant(piece)
                    }
                    "stream_end", "assistant_message", "final", "done", "chat_done", "message" -> {
                        val piece = extractFullResponse(env.data) ?: extractText(env.data)
                        finishAssistant(piece)
                        refreshSessions()
                    }
                    "tool_call", "tool" -> handleToolCall(env.data)
                    "tool_result" -> handleToolResult(env.data)
                    "cancel", "cancelled" -> {
                        finishAssistant(null)
                        _ui.update { it.copy(activityLine = "cancelled") }
                    }
                    "error" -> {
                        val msg = env.error
                            ?: extractField(env.data, "message")
                            ?: extractText(env.data)
                            ?: "Unknown error"
                        finishAssistant(null)
                        pushBubble(
                            ChatBubble(
                                id = "err-${System.currentTimeMillis()}",
                                role = "error",
                                content = msg,
                            ),
                        )
                        _ui.update { it.copy(activityLine = "error · $msg") }
                    }
                    "status", "info" -> {
                        val state = extractField(env.data, "state")
                        val message = extractField(env.data, "message") ?: extractText(env.data)
                        _ui.update {
                            it.copy(activityLine = listOfNotNull(state, message).joinToString(": ").ifBlank { env.type })
                        }
                    }
                    "reasoning" -> {
                        val summary = extractField(env.data, "summary")
                            ?: extractField(env.data, "content")
                            ?: extractText(env.data)
                        if (!summary.isNullOrBlank()) {
                            _ui.update { it.copy(activityLine = "reasoning · ${summary.take(120)}") }
                        }
                    }
                    else -> {
                        if (env.error != null) {
                            _ui.update { it.copy(activityLine = env.error) }
                        }
                    }
                }
            }
        }
    }

    private fun handleToolCall(data: kotlinx.serialization.json.JsonElement?) {
        val name = extractToolName(data) ?: "tool"
        val stepId = extractField(data, "step_id").orEmpty()
        val args = extractToolArgs(data)
        val id = when {
            stepId.isNotBlank() -> toolStepIndex[stepId] ?: "tool-$stepId".also { toolStepIndex[stepId] = it }
            else -> "tool-${System.currentTimeMillis()}"
        }
        upsertToolBubble(
            id = id,
            name = name,
            args = args,
            done = false,
            success = null,
            output = null,
        )
        _ui.update { it.copy(activityLine = "tool · $name") }
    }

    private fun handleToolResult(data: kotlinx.serialization.json.JsonElement?) {
        val name = extractToolName(data) ?: "tool"
        if (name == "__memory_trace") return
        val stepId = extractField(data, "step_id").orEmpty()
        val output = extractField(data, "output")
            ?: extractField(data, "display")
            ?: extractText(data)
            ?: ""
        val success = when (val raw = (data as? JsonObject)?.get("success")) {
            is JsonPrimitive -> when {
                raw.isString -> raw.contentOrNull?.toBooleanStrictOrNull() ?: true
                else -> runCatching { raw.content.toBooleanStrict() }.getOrElse {
                    raw.contentOrNull?.toBooleanStrictOrNull() ?: true
                }
            }
            else -> true
        }
        val id = when {
            stepId.isNotBlank() -> toolStepIndex[stepId] ?: "tool-$stepId".also { toolStepIndex[stepId] = it }
            else -> _ui.value.bubbles.lastOrNull { it.role == "tool" && it.toolName == name && !it.toolDone }?.id
                ?: "tool-${System.currentTimeMillis()}"
        }
        upsertToolBubble(
            id = id,
            name = name,
            args = null,
            done = true,
            success = success,
            output = output,
        )
        _ui.update {
            it.copy(activityLine = if (success) "tool done · $name" else "tool failed · $name")
        }
    }

    private fun upsertToolBubble(
        id: String,
        name: String,
        args: String?,
        done: Boolean,
        success: Boolean?,
        output: String?,
    ) {
        _ui.update { st ->
            val list = st.bubbles.toMutableList()
            val idx = list.indexOfLast { it.id == id }
            if (idx >= 0) {
                val old = list[idx]
                list[idx] = old.copy(
                    role = "tool",
                    toolName = name,
                    toolArgs = args?.takeIf { it.isNotBlank() } ?: old.toolArgs,
                    toolOutput = output?.takeIf { it.isNotBlank() } ?: old.toolOutput,
                    toolDone = done || old.toolDone,
                    toolSuccess = success ?: old.toolSuccess,
                    content = buildToolContent(
                        name = name,
                        args = args?.takeIf { it.isNotBlank() } ?: old.toolArgs,
                        output = output?.takeIf { it.isNotBlank() } ?: old.toolOutput,
                        done = done || old.toolDone,
                        success = success ?: old.toolSuccess,
                    ),
                    stepId = old.stepId,
                )
            } else {
                list += ChatBubble(
                    id = id,
                    role = "tool",
                    content = buildToolContent(name, args, output, done, success),
                    toolName = name,
                    toolArgs = args,
                    toolOutput = output,
                    toolDone = done,
                    toolSuccess = success,
                    stepId = id.removePrefix("tool-").takeIf { it != id },
                )
            }
            st.copy(bubbles = list)
        }
    }

    private fun buildToolContent(
        name: String,
        args: String?,
        output: String?,
        done: Boolean,
        success: Boolean?,
    ): String {
        val status = when {
            !done -> "running"
            success == false -> "failed"
            else -> "done"
        }
        val parts = mutableListOf("$name · $status")
        if (!args.isNullOrBlank()) parts += "args: ${args.take(400)}"
        if (!output.isNullOrBlank()) parts += "out: ${output.take(600)}"
        return parts.joinToString("\n")
    }

    private fun extractText(data: kotlinx.serialization.json.JsonElement?): String? {
        if (data == null) return null
        return when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> {
                sequenceOf("content", "text", "delta", "message", "response", "full_response")
                    .mapNotNull { key -> data[key]?.jsonPrimitive?.contentOrNull }
                    .firstOrNull()
            }
            else -> data.toString()
        }
    }

    private fun extractFullResponse(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        return o["full_response"]?.jsonPrimitive?.contentOrNull
    }

    private fun extractField(data: kotlinx.serialization.json.JsonElement?, key: String): String? {
        val o = data as? JsonObject ?: return null
        return o[key]?.jsonPrimitive?.contentOrNull
    }

    private fun extractToolName(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        return o["name"]?.jsonPrimitive?.contentOrNull
            ?: o["tool"]?.jsonPrimitive?.contentOrNull
    }

    private fun extractToolArgs(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        o["args"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        o["display"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        val params = o["params"]
        return when (params) {
            null -> null
            is JsonPrimitive -> params.contentOrNull
            is JsonObject, is JsonArray -> params.toString()
            else -> params.toString()
        }
    }

    private fun appendAssistant(piece: String) {
        val id = assistantBufferId ?: "a-${System.currentTimeMillis()}".also { assistantBufferId = it }
        _ui.update { st ->
            val list = st.bubbles.toMutableList()
            val idx = list.indexOfLast { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(content = list[idx].content + piece, streaming = true)
            } else {
                list += ChatBubble(id = id, role = "assistant", content = piece, streaming = true)
            }
            st.copy(bubbles = list)
        }
    }

    private fun finishAssistant(full: String?) {
        val id = assistantBufferId
        _ui.update { st ->
            val list = st.bubbles.toMutableList()
            if (id != null) {
                val idx = list.indexOfLast { it.id == id }
                if (idx >= 0) {
                    val content = full?.takeIf { it.isNotBlank() } ?: list[idx].content
                    list[idx] = list[idx].copy(content = content, streaming = false)
                } else if (!full.isNullOrBlank()) {
                    list += ChatBubble(id = id, role = "assistant", content = full, streaming = false)
                }
            } else if (!full.isNullOrBlank()) {
                list += ChatBubble(id = "a-${System.currentTimeMillis()}", role = "assistant", content = full)
            }
            st.copy(bubbles = list)
        }
        assistantBufferId = null
    }

    private fun pushBubble(bubble: ChatBubble) {
        _ui.update { it.copy(bubbles = it.bubbles + bubble) }
    }

    fun navigate(dest: AppDestination) {
        _ui.update { it.copy(destination = dest) }
        when (dest) {
            AppDestination.Memory -> refreshMemory()
            AppDestination.Skills -> refreshSkills()
            AppDestination.Gateways -> refreshGateways()
            AppDestination.Trajectory -> refreshTrajectory()
            AppDestination.Chat -> Unit
            AppDestination.Settings -> Unit
        }
    }

    fun updateComposer(value: String) {
        _ui.update { it.copy(composer = value) }
    }

    fun updateSessionQuery(value: String) {
        _ui.update { it.copy(sessionQuery = value) }
    }

    fun updateMemoryQuery(value: String) {
        _ui.update { it.copy(memoryQuery = value) }
    }

    fun saveSettings(
        apiBase: String,
        apiKey: String,
        sessionId: String,
        useBearer: Boolean,
    ) {
        container.settingsRepository.update {
            it.copy(
                apiBase = apiBase,
                apiKey = apiKey,
                sessionId = sessionId,
                useBearer = useBearer,
            )
        }
        connectSocket()
        refreshSessions()
    }

    fun probeHealth() {
        viewModelScope.launch {
            val result = container.api.healthLive()
            _ui.update {
                it.copy(
                    healthOk = result.isSuccess,
                    healthText = result.getOrElse { e -> e.message ?: "health failed" },
                    activityLine = if (result.isSuccess) "health ok" else "health failed",
                )
            }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _ui.update { it.copy(sessionsLoading = true, sessionsError = null) }
            val q = _ui.value.sessionQuery
            val result = container.api.listSessions(q)
            _ui.update {
                it.copy(
                    sessionsLoading = false,
                    sessions = result.getOrDefault(emptyList()),
                    sessionsError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun selectSession(id: String) {
        container.settingsRepository.update { it.copy(sessionId = id) }
        assistantBufferId = null
        toolStepIndex.clear()
        connectSocket()
        loadHistory(id)
    }

    fun loadHistory(sessionId: String = _ui.value.settings.sessionId) {
        viewModelScope.launch {
            val result = container.api.sessionHistory(sessionId)
            result.onSuccess { history ->
                assistantBufferId = null
                toolStepIndex.clear()
                val bubbles = history.messages.mapIndexed { idx, msg -> msg.toBubble(idx) }
                _ui.update { it.copy(bubbles = bubbles, activityLine = "loaded ${bubbles.size} messages") }
            }.onFailure { e ->
                _ui.update { it.copy(activityLine = "history: ${e.message}") }
            }
        }
    }

    private fun ProviderMessage.toBubble(idx: Int): ChatBubble {
        val role = role ?: "assistant"
        val base = content.orEmpty()
        val tool = toolCalls.firstOrNull()
        return if (tool != null && (role == "assistant" || role == "tool")) {
            ChatBubble(
                id = "h-$idx-tool",
                role = "tool",
                content = buildToolContent(
                    name = tool.name ?: "tool",
                    args = tool.arguments,
                    output = base.takeIf { it.isNotBlank() },
                    done = true,
                    success = true,
                ),
                toolName = tool.name,
                toolArgs = tool.arguments,
                toolOutput = base.takeIf { it.isNotBlank() },
                toolDone = true,
                toolSuccess = true,
            )
        } else {
            ChatBubble(
                id = "h-$idx-${role.hashCode()}",
                role = role,
                content = base.ifBlank { reasoningContent.orEmpty() },
            )
        }
    }

    fun connectSocket() {
        container.wsClient.connect(_ui.value.settings.sessionId)
    }

    fun sendComposer() {
        val text = _ui.value.composer.trim()
        if (text.isEmpty()) return
        pushBubble(ChatBubble(id = "u-${System.currentTimeMillis()}", role = "user", content = text))
        _ui.update { it.copy(composer = "") }
        assistantBufferId = null
        val ok = container.wsClient.sendChat(text)
        if (!ok) {
            connectSocket()
            viewModelScope.launch {
                kotlinx.coroutines.delay(450)
                if (!container.wsClient.sendChat(text)) {
                    pushBubble(
                        ChatBubble(
                            id = "err-${System.currentTimeMillis()}",
                            role = "error",
                            content = "WebSocket not connected. Check API Base / Key / lh serve.",
                        ),
                    )
                }
            }
        }
    }

    fun cancelRun() {
        container.wsClient.cancel()
        finishAssistant(null)
        _ui.update { it.copy(activityLine = "cancel requested") }
    }

    fun refreshMemory() {
        viewModelScope.launch {
            _ui.update { it.copy(memoryLoading = true, memoryError = null) }
            val q = _ui.value.memoryQuery
            val stats = container.api.memoryStats()
            val recall = container.api.recallMemory(q)
            val graph = container.api.memoryGraph()
            _ui.update {
                it.copy(
                    memoryLoading = false,
                    memoryStats = stats.getOrNull() ?: it.memoryStats,
                    memoryEntries = recall.getOrDefault(emptyList()),
                    memoryGraphNodes = graph.getOrNull()?.nodes.orEmpty(),
                    memoryGraphEdges = graph.getOrNull()?.edges.orEmpty(),
                    memoryGraphSummary = graph.getOrNull()?.let { g ->
                        "nodes=${g.nodes.size} edges=${g.edges.size} notes=${g.totalNotes ?: "?"} unresolved=${g.unresolved ?: 0}" +
                            if (g.truncated == true) " truncated" else ""
                    },
                    memoryError = recall.exceptionOrNull()?.message
                        ?: graph.exceptionOrNull()?.message
                        ?: stats.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun refreshSkills() {
        viewModelScope.launch {
            val result = container.api.getJson("/api/v1/skills")
            _ui.update {
                it.copy(skillsJson = result.getOrElse { e -> e.message ?: "failed" })
            }
        }
    }

    fun refreshGateways() {
        viewModelScope.launch {
            val paths = listOf("/api/v1/gateways", "/api/v1/msg-gateway", "/api/data")
            var body: String? = null
            var err: String? = null
            for (p in paths) {
                val r = container.api.getJson(p)
                if (r.isSuccess) {
                    body = r.getOrNull()
                    break
                }
                err = r.exceptionOrNull()?.message
            }
            _ui.update { it.copy(gatewaysJson = body ?: err) }
        }
    }

    fun refreshTrajectory() {
        viewModelScope.launch {
            val paths = listOf(
                "/api/v1/sessions/${_ui.value.settings.sessionId}/trajectory",
                "/api/v1/trajectory",
                "/api/v1/sessions/${_ui.value.settings.sessionId}",
            )
            var body: String? = null
            var err: String? = null
            for (p in paths) {
                val r = container.api.getJson(p)
                if (r.isSuccess) {
                    body = r.getOrNull()
                    break
                }
                err = r.exceptionOrNull()?.message
            }
            _ui.update { it.copy(trajectoryJson = body ?: err) }
        }
    }

    override fun onCleared() {
        container.wsClient.disconnect()
        super.onCleared()
    }
}

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
