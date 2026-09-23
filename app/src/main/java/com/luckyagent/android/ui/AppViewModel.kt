package com.luckyagent.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckyagent.android.data.AppContainer
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.api.MemoryGraphEdge
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.data.api.MemoryStats
import com.luckyagent.android.data.api.CommandExecution
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeCommand
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SessionToolTrace
import com.luckyagent.android.data.api.GatewayStatus
import com.luckyagent.android.data.api.SkillSummary
import com.luckyagent.android.data.api.SkillsResponse
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
    Chat, Commands, Trajectory, Gateways, Skills, Settings, Memory
}

enum class TrajectoryFilter { All, Success, Failure }

enum class ProgressStatus { Active, Complete, Failed }

data class ChatProgressStep(
    val id: String,
    val label: String,
    val status: ProgressStatus,
)

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
    val trajectory: SessionToolTrace? = null,
    val trajectoryLoading: Boolean = false,
    val trajectoryError: String? = null,
    val trajectoryFilter: TrajectoryFilter = TrajectoryFilter.All,
    val trajectoryQuery: String = "",
    val gateways: List<GatewayStatus> = emptyList(),
    val gatewaysLoading: Boolean = false,
    val gatewaysError: String? = null,
    val skills: List<SkillSummary> = emptyList(),
    val skillsDir: String? = null,
    val skillsLoading: Boolean = false,
    val skillsError: String? = null,
    val skillsQuery: String = "",
    val commands: List<RuntimeCommand> = emptyList(),
    val commandsLoading: Boolean = false,
    val commandsError: String? = null,
    val commandExecuting: Boolean = false,
    val commandExecution: CommandExecution? = null,
    val activityLine: String? = null,
    val isResponding: Boolean = false,
    val progressSteps: List<ChatProgressStep> = emptyList(),
    val drawerOpenHint: Boolean = false,
    val suggestionPrompts: List<String> = listOf(
        "Summarize the current session trajectory",
        "What skills are loaded on this runtime?",
        "Recall project decisions from memory",
        "Check gateway status and recent errors",
    ),
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(AppUiState(settings = container.settingsRepository.snapshot()))
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var eventsJob: Job? = null
    private var assistantBufferId: String? = null
    private var currentTurnId = "turn-0"
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
                        updatePhase("response", "Preparing response")
                        appendAssistant(piece)
                    }
                    "stream_end", "assistant_message", "final", "done", "chat_done", "message" -> {
                        val piece = extractFullResponse(env.data) ?: extractText(env.data)
                        finishAssistant(piece)
                        _ui.update { it.copy(isResponding = false) }
                        refreshSessions()
                    }
                    "tool_call", "tool" -> handleToolCall(env.data)
                    "tool_result" -> handleToolResult(env.data)
                    "cancel", "cancelled" -> {
                        finishAssistant(null)
                        _ui.update {
                            it.copy(
                                isResponding = false,
                                activityLine = "cancelled",
                                progressSteps = it.progressSteps.map { step ->
                                    if (step.status == ProgressStatus.Active) step.copy(label = "Cancelled", status = ProgressStatus.Complete) else step
                                },
                            )
                        }
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
                        _ui.update {
                            it.copy(
                                activityLine = "error · $msg",
                                isResponding = false,
                                progressSteps = it.progressSteps.map { step ->
                                    if (step.status == ProgressStatus.Active) step.copy(label = "Request failed", status = ProgressStatus.Failed) else step
                                },
                            )
                        }
                    }
                    "status", "info" -> {
                        val state = extractField(env.data, "state")
                        val message = extractField(env.data, "message") ?: extractText(env.data)
                        when (state?.lowercase()) {
                            "thinking" -> updatePhase("thinking", "Thinking through the request")
                            "executing" -> updatePhase("executing", "Working on your request")
                            "idle" -> _ui.update { current ->
                                current.copy(
                                    isResponding = false,
                                    activityLine = "complete",
                                    progressSteps = current.progressSteps.map { step ->
                                        if (step.status == ProgressStatus.Active) step.copy(status = ProgressStatus.Complete) else step
                                    },
                                )
                            }
                            else -> _ui.update {
                                it.copy(activityLine = listOfNotNull(state, message).joinToString(": ").ifBlank { env.type })
                            }
                        }
                    }
                    "reasoning" -> {
                        val stage = extractField(env.data, "stage").orEmpty()
                        val round = extractField(env.data, "round")?.toIntOrNull()
                        val suffix = round?.let { " · round $it" }.orEmpty()
                        val label = when (stage) {
                            "start" -> "Analyzing the request$suffix"
                            "continue" -> "Reviewing tool results$suffix"
                            else -> "Thinking through the next step$suffix"
                        }
                        updatePhase("reasoning-${round ?: 0}-$stage", label)
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
        val stepKey = "$currentTurnId:$stepId"
        val id = when {
            stepId.isNotBlank() -> toolStepIndex[stepKey] ?: "tool-$stepKey".also { toolStepIndex[stepKey] = it }
            else -> "tool-$currentTurnId-${System.currentTimeMillis()}"
        }
        upsertToolBubble(
            id = id,
            name = name,
            args = args,
            done = false,
            success = null,
            output = null,
        )
        upsertProgress(ChatProgressStep("tool-$id", "Using $name", ProgressStatus.Active))
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
        val stepKey = "$currentTurnId:$stepId"
        val id = when {
            stepId.isNotBlank() -> toolStepIndex[stepKey] ?: "tool-$stepKey".also { toolStepIndex[stepKey] = it }
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
        upsertProgress(
            ChatProgressStep(
                id = "tool-$id",
                label = if (success) "$name finished" else "$name failed",
                status = if (success) ProgressStatus.Complete else ProgressStatus.Failed,
            ),
        )
        _ui.update {
            it.copy(activityLine = if (success) "tool done · $name" else "tool failed · $name")
        }
    }

    private fun updatePhase(id: String, label: String) {
        _ui.update { current ->
            if (current.progressSteps.any { it.id == "phase-$id" && it.label == label && it.status == ProgressStatus.Active }) {
                return@update current
            }
            val previous = current.progressSteps.map { step ->
                if (step.id.startsWith("phase-") && step.status == ProgressStatus.Active) {
                    step.copy(status = ProgressStatus.Complete)
                } else {
                    step
                }
            }
            val next = upsertProgressStep(previous, ChatProgressStep("phase-$id", label, ProgressStatus.Active))
            current.copy(isResponding = true, activityLine = label, progressSteps = next)
        }
    }

    private fun upsertProgress(step: ChatProgressStep) {
        _ui.update { current -> current.copy(progressSteps = upsertProgressStep(current.progressSteps, step)) }
    }

    private fun upsertProgressStep(
        steps: List<ChatProgressStep>,
        step: ChatProgressStep,
    ): List<ChatProgressStep> {
        val updated = steps.toMutableList()
        val index = updated.indexOfFirst { it.id == step.id }
        if (index >= 0) updated.removeAt(index)
        updated += step
        return updated.takeLast(8)
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
            st.copy(bubbles = list, isResponding = true)
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
            st.copy(bubbles = list, isResponding = false)
        }
        assistantBufferId = null
    }

    private fun pushBubble(bubble: ChatBubble) {
        _ui.update { it.copy(bubbles = it.bubbles + bubble) }
    }

    fun navigate(dest: AppDestination) {
        _ui.update { it.copy(destination = dest) }
        when (dest) {
            AppDestination.Commands -> refreshCommands()
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
        wsUrl: String = container.settingsRepository.snapshot().wsUrl,
    ) {
        container.settingsRepository.update {
            it.copy(
                apiBase = apiBase,
                apiKey = apiKey,
                sessionId = sessionId,
                useBearer = useBearer,
                wsUrl = wsUrl,
            )
        }
        connectSocket()
        refreshSessions()
    }

    fun updateSettings(transform: (com.luckyagent.android.data.settings.ClientSettings) -> com.luckyagent.android.data.settings.ClientSettings) {
        val prev = container.settingsRepository.snapshot()
        container.settingsRepository.update(transform)
        val next = container.settingsRepository.snapshot()
        val endpointChanged =
            prev.apiBase != next.apiBase ||
                prev.wsUrl != next.wsUrl ||
                prev.sessionId != next.sessionId ||
                prev.apiKey != next.apiKey ||
                prev.useBearer != next.useBearer
        if (endpointChanged && (
                prev.apiBase != next.apiBase ||
                    prev.wsUrl != next.wsUrl ||
                    prev.sessionId != next.sessionId
                )
        ) {
            connectSocket()
        }
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
        _ui.update { it.copy(isResponding = false, progressSteps = emptyList()) }
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
                _ui.update {
                    it.copy(
                        bubbles = bubbles,
                        activityLine = "loaded ${bubbles.size} messages",
                        isResponding = false,
                        progressSteps = emptyList(),
                    )
                }
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
                content = base,
            )
        }
    }

    fun connectSocket() {
        container.wsClient.connect(_ui.value.settings.sessionId)
    }

    fun sendComposer() {
        val text = _ui.value.composer.trim()
        if (text.isEmpty()) return
        currentTurnId = "turn-${System.currentTimeMillis()}"
        toolStepIndex.clear()
        pushBubble(ChatBubble(id = "u-${System.currentTimeMillis()}", role = "user", content = text))
        _ui.update {
            it.copy(
                composer = "",
                isResponding = true,
                progressSteps = listOf(ChatProgressStep("phase-thinking", "Thinking through the request", ProgressStatus.Active)),
            )
        }
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
                    _ui.update {
                        it.copy(
                            isResponding = false,
                            progressSteps = it.progressSteps.map { step ->
                                if (step.status == ProgressStatus.Active) step.copy(label = "Connection unavailable", status = ProgressStatus.Failed) else step
                            },
                        )
                    }
                }
            }
        }
    }

    fun cancelRun() {
        container.wsClient.cancel()
        finishAssistant(null)
        _ui.update {
            it.copy(
                activityLine = "cancel requested",
                isResponding = false,
                progressSteps = it.progressSteps.map { step ->
                    if (step.status == ProgressStatus.Active) step.copy(label = "Cancelled", status = ProgressStatus.Complete) else step
                },
            )
        }
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


    fun updateTrajectoryQuery(value: String) {
        _ui.update { it.copy(trajectoryQuery = value) }
    }

    fun setTrajectoryFilter(filter: TrajectoryFilter) {
        _ui.update { it.copy(trajectoryFilter = filter) }
    }

    fun updateSkillsQuery(value: String) {
        _ui.update { it.copy(skillsQuery = value) }
    }

    fun refreshCommands() {
        viewModelScope.launch {
            _ui.update { it.copy(commandsLoading = true, commandsError = null) }
            val result = container.api.listCommands()
            _ui.update {
                if (result.isSuccess) {
                    it.copy(
                        commandsLoading = false,
                        commands = result.getOrDefault(emptyList()),
                        commandsError = null,
                    )
                } else {
                    it.copy(
                        commandsLoading = false,
                        commandsError = result.exceptionOrNull()?.message ?: "Unable to load commands",
                    )
                }
            }
        }
    }

    fun runCommand(command: RuntimeCommand, args: String) {
        viewModelScope.launch {
            _ui.update { it.copy(commandExecuting = true, commandExecution = null, commandsError = null) }
            val sessionId = _ui.value.settings.sessionId
            val result = container.api.runCommand(command.name, args.trim(), sessionId)
            _ui.update {
                it.copy(
                    commandExecuting = false,
                    commandExecution = result.getOrNull(),
                    commandsError = result.exceptionOrNull()?.message,
                    activityLine = result.getOrNull()?.let { execution ->
                        if (execution.ok) "/${execution.command} completed" else "/${execution.command} returned an error"
                    } ?: it.activityLine,
                )
            }
            if (result.getOrNull()?.ok == true) {
                when (command.name) {
                    "remember", "remember_long", "memdecay", "promote" -> refreshMemory()
                    "rename" -> refreshSessions()
                }
            }
        }
    }

    fun applySuggestion(text: String) {
        _ui.update { it.copy(composer = text) }
    }

    fun createSession(title: String = "Android session") {
        viewModelScope.launch {
            val result = container.api.createSession(title)
            result.onSuccess { session ->
                container.settingsRepository.update { it.copy(sessionId = session.id) }
                assistantBufferId = null
                toolStepIndex.clear()
                _ui.update {
                    it.copy(
                        bubbles = emptyList(),
                        activityLine = "new session · ${session.id}",
                    )
                }
                connectSocket()
                refreshSessions()
            }.onFailure { e ->
                _ui.update { it.copy(activityLine = "create session: ${e.message}") }
            }
        }
    }

    fun renameSession(id: String, title: String) {
        val trimmed = title.trim()
        if (id.isBlank() || trimmed.isEmpty()) return
        viewModelScope.launch {
            val result = container.api.renameSession(id, trimmed)
            result.onSuccess {
                _ui.update { it.copy(activityLine = "renamed · $trimmed") }
                refreshSessions()
            }.onFailure { e ->
                _ui.update { it.copy(activityLine = "rename: ${e.message}") }
            }
        }
    }

    fun refreshSkills() {
        viewModelScope.launch {
            _ui.update { it.copy(skillsLoading = true, skillsError = null) }
            val result = container.api.listSkills()
            _ui.update {
                if (result.isSuccess) {
                    val payload = result.getOrNull() ?: SkillsResponse()
                    it.copy(
                        skillsLoading = false,
                        skills = payload.skills,
                        skillsDir = payload.skillsDir,
                        skillsJson = null,
                        skillsError = null,
                        activityLine = "skills · ${payload.skills.size}",
                    )
                } else {
                    it.copy(
                        skillsLoading = false,
                        skillsError = result.exceptionOrNull()?.message,
                        skillsJson = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun refreshGateways() {
        viewModelScope.launch {
            _ui.update { it.copy(gatewaysLoading = true, gatewaysError = null) }
            val result = container.api.listGateways()
            _ui.update {
                if (result.isSuccess) {
                    val list = result.getOrDefault(emptyList())
                    it.copy(
                        gatewaysLoading = false,
                        gateways = list,
                        gatewaysJson = null,
                        gatewaysError = null,
                        activityLine = "gateways · ${list.size}",
                    )
                } else {
                    it.copy(
                        gatewaysLoading = false,
                        gatewaysError = result.exceptionOrNull()?.message,
                        gatewaysJson = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    fun refreshTrajectory() {
        viewModelScope.launch {
            val sessionId = _ui.value.settings.sessionId
            if (sessionId.isBlank()) {
                _ui.update {
                    it.copy(
                        trajectory = null,
                        trajectoryError = "Select a session in Chat first",
                        trajectoryLoading = false,
                        trajectoryJson = null,
                    )
                }
                return@launch
            }
            _ui.update { it.copy(trajectoryLoading = true, trajectoryError = null) }
            val result = container.api.sessionToolTrace(sessionId)
            _ui.update {
                if (result.isSuccess) {
                    val trace = result.getOrNull()
                    it.copy(
                        trajectoryLoading = false,
                        trajectory = trace,
                        trajectoryJson = null,
                        trajectoryError = null,
                        activityLine = "trajectory · ${trace?.tools?.size ?: 0} tools",
                    )
                } else {
                    // Fallback: keep raw JSON for diagnostics without blocking UI empty state message
                    val raw = container.api.getJson("/api/v1/sessions/$sessionId")
                    it.copy(
                        trajectoryLoading = false,
                        trajectory = null,
                        trajectoryError = result.exceptionOrNull()?.message,
                        trajectoryJson = raw.getOrNull() ?: result.exceptionOrNull()?.message,
                    )
                }
            }
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
