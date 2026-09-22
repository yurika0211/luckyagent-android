package com.luckyagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckyagent.android.data.AppContainer
import com.luckyagent.android.data.api.MemoryEntry
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
    val healthText: String? = null,
    val healthOk: Boolean? = null,
    val memoryEntries: List<MemoryEntry> = emptyList(),
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
        observeWs()
        refreshSessions()
        connectSocket()
    }

    private fun observeWs() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            container.wsClient.events.collect { env ->
                when (env.type) {
                    "assistant_delta", "delta", "chunk" -> {
                        val piece = extractText(env.data) ?: return@collect
                        appendAssistant(piece)
                    }
                    "assistant_message", "final", "done", "chat_done", "message" -> {
                        val piece = extractText(env.data)
                        if (!piece.isNullOrBlank()) finishAssistant(piece) else finishAssistant(null)
                    }
                    "tool_call", "tool" -> {
                        val name = extractToolName(env.data) ?: "tool"
                        pushBubble(ChatBubble(
                            id = "tool-${System.currentTimeMillis()}",
                            role = "tool",
                            content = "Calling $name…",
                            toolName = name,
                        ))
                        _ui.update { it.copy(activityLine = "tool · $name") }
                    }
                    "error" -> {
                        val msg = env.error ?: extractText(env.data) ?: "Unknown error"
                        pushBubble(ChatBubble(
                            id = "err-${System.currentTimeMillis()}",
                            role = "error",
                            content = msg,
                        ))
                        _ui.update { it.copy(activityLine = "error · $msg") }
                    }
                    "status", "info" -> {
                        _ui.update { it.copy(activityLine = extractText(env.data) ?: env.type) }
                    }
                    else -> {
                        // keep raw signal light
                        if (env.error != null) {
                            _ui.update { it.copy(activityLine = env.error) }
                        }
                    }
                }
            }
        }
    }

    private fun extractText(data: kotlinx.serialization.json.JsonElement?): String? {
        if (data == null) return null
        return when (data) {
            is JsonPrimitive -> data.contentOrNull
            is JsonObject -> {
                val o = data
                sequenceOf("content", "text", "delta", "message", "response")
                    .mapNotNull { key -> o[key]?.jsonPrimitive?.contentOrNull }
                    .firstOrNull()
            }
            else -> data.toString()
        }
    }

    private fun extractToolName(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        return o["name"]?.jsonPrimitive?.contentOrNull
            ?: o["tool"]?.jsonPrimitive?.contentOrNull
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
        connectSocket()
        loadHistory(id)
    }

    fun loadHistory(sessionId: String = _ui.value.settings.sessionId) {
        viewModelScope.launch {
            val result = container.api.sessionHistory(sessionId)
            result.onSuccess { history ->
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
        val tool = toolCalls.firstOrNull()?.name
        return ChatBubble(
            id = "h-$idx-${role.hashCode()}",
            role = if (tool != null && role == "assistant") "tool" else role,
            content = when {
                tool != null && base.isBlank() -> "tool_call · $tool"
                else -> base.ifBlank { reasoningContent.orEmpty() }
            },
            toolName = tool,
        )
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
            // reconnect once then retry
            connectSocket()
            viewModelScope.launch {
                kotlinx.coroutines.delay(400)
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
    }

    fun refreshMemory() {
        viewModelScope.launch {
            val result = container.api.listMemory()
            _ui.update {
                it.copy(
                    memoryEntries = result.getOrDefault(emptyList()),
                    memoryError = result.exceptionOrNull()?.message,
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
            // dashboard-ish endpoints may vary; try a few
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
