package com.luckyagent.android.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckyagent.android.data.AppContainer
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.MemoryGraphEdge
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.data.api.MemoryStats
import com.luckyagent.android.data.api.MemorySearchTrace
import com.luckyagent.android.data.api.ReceivedMemoryTrace
import com.luckyagent.android.data.api.CommandExecution
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.TokenUsage
import com.luckyagent.android.data.api.RuntimeCommand
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SessionToolTrace
import com.luckyagent.android.data.api.GatewayStatus
import com.luckyagent.android.data.api.SkillSummary
import com.luckyagent.android.data.api.SkillsResponse
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.data.api.TaskDetail
import com.luckyagent.android.data.api.TaskEvent
import com.luckyagent.android.data.api.TaskNode
import com.luckyagent.android.data.api.TaskOrigin
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.data.api.toTaskNode
import com.luckyagent.android.data.api.toTaskSummary
import com.luckyagent.android.data.api.WsChatHandle
import com.luckyagent.android.data.api.WsEvent
import com.luckyagent.android.data.settings.ClientSettings
import com.luckyagent.android.data.settings.RuntimeEndpoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class AppDestination {
    Chat, Tasks, Commands, Trajectory, Gateways, Skills, Settings, Memory
}

enum class TrajectoryFilter { All, Success, Failure }

enum class TaskFilter { All, Active, Completed, Failed, Cancelled }

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
    val createdAt: String? = null,
    val usage: TokenUsage? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val toolOutput: String? = null,
    val toolDone: Boolean = false,
    val toolSuccess: Boolean? = null,
    val stepId: String? = null,
    val reasoningRound: Int? = null,
    val reasoningHasContent: Boolean = false,
    val attachments: List<ChatMedia> = emptyList(),
)

data class ChatMedia(
    val descriptor: MediaAttachment,
    val localUri: String? = null,
)

data class PendingMedia(
    val id: String,
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val descriptor: MediaAttachment? = null,
    val error: String? = null,
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
    val pendingMedia: List<PendingMedia> = emptyList(),
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
    val memoryGraphIsolated: Boolean = false,
    val memoryTraceQuery: String = "",
    val memoryTraceDepth: Int = 1,
    val memoryTrace: MemorySearchTrace? = null,
    val memoryTraceLoading: Boolean = false,
    val memoryTraceError: String? = null,
    val memoryLiveTraces: List<ReceivedMemoryTrace> = emptyList(),
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
    val tasks: List<TaskSummary> = emptyList(),
    val tasksLoading: Boolean = false,
    val tasksError: String? = null,
    val taskFilter: TaskFilter = TaskFilter.All,
    val taskQuery: String = "",
    val selectedTaskId: String? = null,
    val selectedTask: TaskDetail? = null,
    val taskDetailLoading: Boolean = false,
    val taskDetailError: String? = null,
    val taskPolling: Boolean = false,
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(AppUiState(settings = container.settingsRepository.snapshot()))
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var eventsJob: Job? = null
    private var settingsReconnectJob: Job? = null
    private var taskPollingJob: Job? = null
    private var assistantBufferId: String? = null
    private val assistantPending = StringBuilder()
    private var assistantFlushJob: Job? = null
    private var currentTurnId = "turn-0"
    private val toolStepIndex = mutableMapOf<String, String>()
    private val mediaUploadSemaphore = Semaphore(1)
    private data class ActiveRun(
        val handle: WsChatHandle,
    )

    private val activeRuns = mutableMapOf<String, ActiveRun>()
    private var foregroundRunConnectionId: String? = null

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
            container.wsClient.events.collect { event ->
                val env = event.envelope
                val foreground = isForegroundEvent(event)
                when (env.type) {
                    "stream_chunk", "assistant_delta", "delta", "chunk" -> {
                        if (!foreground) return@collect
                        val piece = extractText(env.data) ?: return@collect
                        updatePhase("response", "Preparing response")
                        appendAssistant(piece)
                    }
                    "stream_end", "assistant_message", "final", "done", "chat_done", "message" -> {
                        if (!completeRun(event)) return@collect
                        val piece = extractFullResponse(env.data) ?: extractText(env.data)
                        if (foreground) {
                            finishAssistant(
                                full = piece,
                                createdAt = extractField(env.data, "created_at") ?: env.timestamp,
                                usage = extractUsage(env.data),
                            )
                            _ui.update { it.copy(isResponding = false) }
                            foregroundRunConnectionId = null
                        } else {
                            finishBackgroundRun(event.sessionId)
                        }
                        refreshSessions()
                    }
                    "tool_call", "tool" -> if (foreground) handleToolCall(env.data)
                    "tool_result" -> if (foreground) handleToolResult(env.data)
                    "cancel", "cancelled" -> {
                        if (!completeRun(event)) return@collect
                        if (foreground) {
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
                            foregroundRunConnectionId = null
                        } else {
                            finishBackgroundRun(event.sessionId)
                        }
                    }
                    "error" -> {
                        if (!completeRun(event)) return@collect
                        val msg = env.error
                            ?: extractField(env.data, "message")
                            ?: extractText(env.data)
                            ?: "Unknown error"
                        if (foreground) {
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
                            foregroundRunConnectionId = null
                        } else {
                            finishBackgroundRun(event.sessionId)
                        }
                    }
                    "status", "info" -> {
                        val state = extractField(env.data, "state")
                        val message = extractField(env.data, "message") ?: extractText(env.data)
                        when (state?.lowercase()) {
                            "thinking" -> if (foreground) updatePhase("thinking", "Thinking through the request")
                            "executing" -> if (foreground) updatePhase("executing", "Working on your request")
                            "idle" -> {
                                if (!completeRun(event)) return@collect
                                if (foreground) {
                                    finishAssistant(null)
                                    _ui.update { current ->
                                        current.copy(
                                            isResponding = false,
                                            activityLine = "complete",
                                            progressSteps = current.progressSteps.map { step ->
                                                if (step.status == ProgressStatus.Active) step.copy(status = ProgressStatus.Complete) else step
                                            },
                                        )
                                    }
                                    foregroundRunConnectionId = null
                                } else {
                                    finishBackgroundRun(event.sessionId)
                                }
                            }
                            else -> if (foreground) _ui.update {
                                it.copy(activityLine = listOfNotNull(state, message).joinToString(": ").ifBlank { env.type })
                            }
                        }
                    }
                    "reasoning" -> {
                        if (!foreground) return@collect
                        val stage = extractField(env.data, "stage").orEmpty()
                        val round = extractField(env.data, "round")?.toIntOrNull()
                        val summary = extractField(env.data, "summary")?.trim().orEmpty()
                        val content = extractField(env.data, "content")?.trim().orEmpty()
                        val label = when {
                            summary.isNotEmpty() -> summary
                            stage == "continue" -> "Reviewing tool results"
                            else -> "Analyzing the request"
                        }
                        if (stage == "content") {
                            if (content.isNotEmpty()) upsertReasoningBubble(round, content, true)
                        } else {
                            upsertReasoningBubble(round, label, false)
                            updatePhase("reasoning-${round ?: 0}", label)
                        }
                    }
                    else -> {
                        if (foreground && env.error != null) {
                            _ui.update { it.copy(activityLine = env.error) }
                        }
                    }
                }
            }
        }
    }

    private fun currentSessionId(): String =
        container.settingsRepository.snapshot().sessionId.ifBlank { "android-main" }

    private fun isCurrentSessionRunActive(): Boolean = activeRuns.containsKey(currentSessionId())

    private fun registerRun(handle: WsChatHandle) {
        activeRuns[handle.sessionId] = ActiveRun(handle)
        foregroundRunConnectionId = handle.connectionId
    }

    private fun isForegroundEvent(event: WsEvent): Boolean =
        event.sessionId == currentSessionId() && foregroundRunConnectionId == event.connectionId

    private fun completeRun(event: WsEvent): Boolean {
        val run = activeRuns[event.sessionId] ?: return false
        if (run.handle.connectionId != event.connectionId) return false
        releaseRun(event.sessionId)
        return true
    }

    private fun releaseRun(sessionId: String) {
        val run = activeRuns.remove(sessionId) ?: return
        container.wsClient.release(run.handle.connectionId)
        if (foregroundRunConnectionId == run.handle.connectionId) {
            foregroundRunConnectionId = null
        }
    }

    private fun finishBackgroundRun(sessionId: String) {
        if (sessionId != currentSessionId()) return
        foregroundRunConnectionId = null
        resetAssistantStream()
        loadHistory(sessionId)
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
        if (name == "__memory_trace") {
            val rawElement = (data as? JsonObject)?.get("output") ?: (data as? JsonObject)?.get("display")
            val raw = when (rawElement) {
                is JsonPrimitive -> rawElement.contentOrNull
                is JsonObject, is JsonArray -> rawElement.toString()
                else -> extractText(data)
            }
            val trace = raw?.let { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(MemorySearchTrace.serializer(), it) }.getOrNull() }
            if (trace != null) {
                _ui.update { it.copy(memoryLiveTraces = (listOf(ReceivedMemoryTrace(trace, System.currentTimeMillis())) + it.memoryLiveTraces).take(20)) }
            }
            return
        }
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
                list.insertBeforeStreamingAnswer(
                    ChatBubble(
                        id = id,
                        role = "tool",
                        content = buildToolContent(name, args, output, done, success),
                        toolName = name,
                        toolArgs = args,
                        toolOutput = output,
                        toolDone = done,
                        toolSuccess = success,
                        stepId = id.removePrefix("tool-").takeIf { it != id },
                    ),
                )
            }
            st.copy(bubbles = list)
        }
    }

    private fun upsertReasoningBubble(round: Int?, text: String, hasContent: Boolean) {
        val id = "reasoning-$currentTurnId-${round ?: 0}"
        _ui.update { state ->
            val bubbles = state.bubbles.toMutableList()
            val index = bubbles.indexOfLast { it.id == id }
            if (index >= 0) {
                val old = bubbles[index]
                if (!old.reasoningHasContent || hasContent) {
                    bubbles[index] = old.copy(content = text, reasoningHasContent = hasContent)
                }
            } else {
                bubbles.insertBeforeStreamingAnswer(
                    ChatBubble(
                        id = id,
                        role = "reasoning",
                        content = text,
                        reasoningRound = round,
                        reasoningHasContent = hasContent,
                    ),
                )
            }
            state.copy(bubbles = bubbles)
        }
    }

    private fun MutableList<ChatBubble>.insertBeforeStreamingAnswer(bubble: ChatBubble) {
        val answerIndex = assistantBufferId?.let { id -> indexOfLast { it.id == id } } ?: -1
        if (answerIndex >= 0) add(answerIndex, bubble) else add(bubble)
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

    private fun extractUsage(data: kotlinx.serialization.json.JsonElement?): TokenUsage? {
        val o = data as? JsonObject ?: return null
        val usage = o["usage"] as? JsonObject ?: return null
        fun int(key: String): Int = usage[key]?.jsonPrimitive?.intOrNull ?: 0
        val result = TokenUsage(
            inputTokens = int("input_tokens"),
            outputTokens = int("output_tokens"),
            totalTokens = int("total_tokens"),
            cachedInputTokens = int("cached_input_tokens"),
            model = usage["model"]?.jsonPrimitive?.contentOrNull,
        )
        return result.takeIf {
            it.totalTokens > 0 || it.inputTokens > 0 || it.outputTokens > 0 || !it.model.isNullOrBlank()
        }
    }

    private fun nowIsoTimestamp(): String = java.time.Instant.now().toString()

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
        if (piece.isEmpty()) return
        if (assistantBufferId == null) assistantBufferId = "a-${System.currentTimeMillis()}"
        assistantPending.append(piece)
        if (assistantFlushJob == null) {
            assistantFlushJob = viewModelScope.launch {
                kotlinx.coroutines.delay(40)
                assistantFlushJob = null
                flushAssistantPending()
            }
        }
    }

    private fun flushAssistantPending() {
        val id = assistantBufferId ?: return
        if (assistantPending.isEmpty()) return
        val piece = assistantPending.toString()
        assistantPending.setLength(0)
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

    private fun finishAssistant(full: String?, createdAt: String? = null, usage: TokenUsage? = null) {
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        val pending = assistantPending.toString()
        assistantPending.setLength(0)
        val id = assistantBufferId
        _ui.update { st ->
            val list = st.bubbles.toMutableList()
            if (id != null) {
                val idx = list.indexOfLast { it.id == id }
                if (idx >= 0) {
                    val content = full?.takeIf { it.isNotBlank() } ?: list[idx].content + pending
                    val answer = list.removeAt(idx)
                    list += answer.copy(
                        content = content,
                        streaming = false,
                        createdAt = createdAt ?: answer.createdAt,
                        usage = usage ?: answer.usage,
                    )
                } else if (!full.isNullOrBlank() || pending.isNotEmpty()) {
                    list += ChatBubble(
                        id = id,
                        role = "assistant",
                        content = full?.takeIf { it.isNotBlank() } ?: pending,
                        streaming = false,
                        createdAt = createdAt,
                        usage = usage,
                    )
                }
            } else if (!full.isNullOrBlank()) {
                list += ChatBubble(
                    id = "a-${System.currentTimeMillis()}",
                    role = "assistant",
                    content = full,
                    createdAt = createdAt,
                    usage = usage,
                )
            }
            st.copy(bubbles = list, isResponding = false)
        }
        assistantBufferId = null
    }

    private fun resetAssistantStream() {
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        assistantPending.setLength(0)
        assistantBufferId = null
    }

    private fun pushBubble(bubble: ChatBubble) {
        _ui.update { it.copy(bubbles = it.bubbles + bubble) }
    }

    fun navigate(dest: AppDestination) {
        _ui.update { it.copy(destination = dest) }
        if (dest != AppDestination.Tasks) stopTaskPolling()
        when (dest) {
            AppDestination.Tasks -> {
                startTaskPolling()
            }
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

    fun addPickedMedia(resolver: ContentResolver, uris: List<Uri>) {
        uris.forEach { uri ->
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "attachment"
            val id = "media-${System.nanoTime()}"
            val item = PendingMedia(id, uri.toString(), name, resolver.getType(uri) ?: "application/octet-stream")
            _ui.update { state -> state.copy(pendingMedia = state.pendingMedia + item) }
            viewModelScope.launch {
                mediaUploadSemaphore.withPermit { container.api.uploadAttachment(resolver, uri, name) }.onSuccess { descriptor ->
                    _ui.update { state ->
                        state.copy(pendingMedia = state.pendingMedia.map { if (it.id == id) it.copy(descriptor = descriptor) else it })
                    }
                }.onFailure { error ->
                    _ui.update { state ->
                        state.copy(pendingMedia = state.pendingMedia.map { if (it.id == id) it.copy(error = error.message ?: "上传失败") else it })
                    }
                }
            }
        }
    }

    fun removePendingMedia(id: String) {
        _ui.update { it.copy(pendingMedia = it.pendingMedia.filterNot { media -> media.id == id }) }
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
        updateSettings {
            it.copy(
                apiBase = apiBase,
                apiKey = apiKey,
                sessionId = sessionId,
                useBearer = useBearer,
                wsUrl = wsUrl,
            )
        }
        refreshSessions()
    }

    fun saveRuntimeEndpoint(endpoint: RuntimeEndpoint) {
        val normalized = endpoint.copy(
            name = endpoint.name.trim().ifBlank { "Runtime" },
            apiBase = endpoint.apiBase.trim().trimEnd('/'),
            apiKey = endpoint.apiKey.trim(),
            wsUrl = endpoint.wsUrl.trim(),
        )
        if (normalized.apiBase.isBlank()) {
            _ui.update { it.copy(activityLine = "API base URL is required") }
            return
        }
        updateSettings { settings ->
            val exists = settings.runtimeEndpoints.any { it.id == normalized.id }
            val endpoints = if (exists) {
                settings.runtimeEndpoints.map { if (it.id == normalized.id) normalized else it }
            } else {
                settings.runtimeEndpoints + normalized
            }
            settings.copy(runtimeEndpoints = endpoints)
        }
    }

    fun activateRuntimeEndpoint(id: String) {
        val endpoint = container.settingsRepository.snapshot().runtimeEndpoints.firstOrNull { it.id == id } ?: return
        updateSettings { it.copy(activeRuntimeEndpointId = endpoint.id) }
        refreshSessions()
    }

    fun deleteRuntimeEndpoint(id: String) {
        val settings = container.settingsRepository.snapshot()
        if (settings.runtimeEndpoints.size <= 1) {
            _ui.update { it.copy(activityLine = "Keep at least one runtime endpoint") }
            return
        }
        val endpoints = settings.runtimeEndpoints.filterNot { it.id == id }
        val activeId = if (settings.activeRuntimeEndpointId == id) endpoints.first().id else settings.activeRuntimeEndpointId
        updateSettings { it.copy(runtimeEndpoints = endpoints, activeRuntimeEndpointId = activeId) }
        if (settings.activeRuntimeEndpointId == id) refreshSessions()
    }

    fun updateSettings(transform: (com.luckyagent.android.data.settings.ClientSettings) -> com.luckyagent.android.data.settings.ClientSettings) {
        val prev = container.settingsRepository.snapshot()
        container.settingsRepository.update(transform)
        val next = container.settingsRepository.snapshot()
        val settingsChanged =
            prev.apiBase != next.apiBase ||
                prev.wsUrl != next.wsUrl ||
                prev.sessionId != next.sessionId ||
                prev.apiKey != next.apiKey ||
                prev.useBearer != next.useBearer
        if (settingsChanged) {
            settingsReconnectJob?.cancel()
            settingsReconnectJob = viewModelScope.launch {
                kotlinx.coroutines.delay(450)
                connectSocket()
                settingsReconnectJob = null
            }
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
        val target = id.ifBlank { "android-main" }
        container.settingsRepository.update { it.copy(sessionId = target) }
        foregroundRunConnectionId = null
        resetAssistantStream()
        toolStepIndex.clear()
        _ui.update {
            it.copy(
                bubbles = emptyList(),
                isResponding = activeRuns.containsKey(target),
                progressSteps = emptyList(),
            )
        }
        connectSocket()
        loadHistory(target)
    }

    fun loadHistory(sessionId: String = currentSessionId()) {
        viewModelScope.launch {
            val result = container.api.sessionHistory(sessionId)
            result.onSuccess { history ->
                if (currentSessionId() != sessionId) return@onSuccess
                resetAssistantStream()
                toolStepIndex.clear()
                val bubbles = historyToBubbles(history.messages)
                val running = activeRuns.containsKey(sessionId)
                _ui.update {
                    it.copy(
                        bubbles = bubbles,
                        activityLine = if (running) "Agent running · history loaded" else "loaded ${bubbles.size} messages",
                        isResponding = running,
                        progressSteps = emptyList(),
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(activityLine = "history: ${e.message}") }
            }
        }
    }

    private fun historyToBubbles(messages: List<ProviderMessage>): List<ChatBubble> {
        val bubbles = mutableListOf<ChatBubble>()
        messages.forEachIndexed { index, message ->
            message.toBubbles(index).forEach { bubble ->
                val callIndex = if (message.role == "tool" && bubble.role == "tool" && !message.toolCallId.isNullOrBlank()) {
                    bubbles.indexOfLast { it.role == "tool" && it.stepId == message.toolCallId }
                } else -1
                if (callIndex >= 0) {
                    val call = bubbles[callIndex]
                    bubbles[callIndex] = call.copy(
                        content = bubble.content,
                        toolOutput = bubble.toolOutput,
                    )
                } else {
                    bubbles += bubble
                }
            }
        }
        return bubbles
    }

    private fun ProviderMessage.toBubbles(idx: Int): List<ChatBubble> {
        val role = role ?: "assistant"
        val base = content.orEmpty()
        val result = mutableListOf<ChatBubble>()
        reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
            result += ChatBubble(
                id = "h-$idx-reasoning",
                role = "reasoning",
                content = reasoning,
                reasoningHasContent = true,
            )
        }
        toolCalls.forEachIndexed { toolIndex, tool ->
            result += ChatBubble(
                id = "h-$idx-tool-$toolIndex",
                role = "tool",
                content = buildToolContent(
                    name = tool.name ?: "tool",
                    args = tool.arguments,
                    output = null,
                    done = true,
                    success = null,
                ),
                toolName = tool.name,
                toolArgs = tool.arguments,
                toolDone = true,
                stepId = tool.id,
            )
        }
        if (role == "tool") {
            result += ChatBubble(
                id = "h-$idx-tool-result",
                role = "tool",
                content = base,
                toolName = name ?: "tool",
                toolOutput = base.takeIf { it.isNotBlank() },
                toolDone = true,
            )
        } else if (base.isNotBlank() || (result.isEmpty() && toolCalls.isEmpty())) {
            result += ChatBubble(
                id = "h-$idx-${role.hashCode()}",
                role = role,
                content = base,
                createdAt = createdAt,
                usage = usage,
            )
        }
        return result
    }

    fun connectSocket(force: Boolean = false) {
        settingsReconnectJob?.cancel()
        settingsReconnectJob = null
        container.wsClient.connect(currentSessionId(), force = force)
    }

    fun sendComposer() {
        val text = _ui.value.composer.trim()
        val pending = _ui.value.pendingMedia
        if (text.isEmpty() && pending.isEmpty()) return
        val parsedCommand = parseRuntimeCommand(text)
        val runtimeCommand = if (pending.isEmpty()) parsedCommand else null
        if (parsedCommand?.name?.equals("stop", ignoreCase = true) == true) {
            pushBubble(ChatBubble(id = "u-${System.currentTimeMillis()}", role = "user", content = text, createdAt = nowIsoTimestamp()))
            _ui.update { it.copy(composer = "", pendingMedia = emptyList()) }
            cancelRun()
            return
        }
        if (pending.any { it.descriptor == null }) {
            _ui.update { it.copy(activityLine = if (pending.any { media -> media.error != null }) "请移除上传失败的附件" else "附件仍在上传中") }
            return
        }
        val sessionId = currentSessionId()
        if (isCurrentSessionRunActive()) {
            _ui.update { it.copy(activityLine = "Agent 正在运行，请等待完成或发送 /stop") }
            return
        }
        if (runtimeCommand != null) {
            sendRuntimeCommand(text, runtimeCommand)
            return
        }
        val message = text.ifBlank { if (pending.isNotEmpty()) "请查看附件" else "" }
        val descriptors = pending.mapNotNull { it.descriptor }
        val media = pending.mapNotNull { item -> item.descriptor?.let { ChatMedia(it, item.uri) } }
        currentTurnId = "turn-${System.currentTimeMillis()}"
        toolStepIndex.clear()
        pushBubble(ChatBubble(id = "u-${System.currentTimeMillis()}", role = "user", content = text, createdAt = nowIsoTimestamp(), attachments = media))
        _ui.update {
            it.copy(
                composer = "",
                pendingMedia = emptyList(),
                isResponding = true,
                progressSteps = listOf(ChatProgressStep("phase-thinking", "Thinking through the request", ProgressStatus.Active)),
            )
        }
        resetAssistantStream()
        val handle = container.wsClient.sendChat(message, attachments = descriptors)
        if (handle != null) {
            registerRun(handle)
        } else {
            connectSocket(force = true)
            viewModelScope.launch {
                kotlinx.coroutines.delay(450)
                if (currentSessionId() != sessionId) {
                    _ui.update { it.copy(isResponding = false) }
                    return@launch
                }
                val retryHandle = container.wsClient.sendChat(message, attachments = descriptors)
                if (retryHandle == null) {
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
                } else {
                    registerRun(retryHandle)
                }
            }
        }
    }

    private data class ParsedRuntimeCommand(val name: String, val args: String)

    private fun parseRuntimeCommand(text: String): ParsedRuntimeCommand? {
        val input = text.trim()
        if (!input.startsWith("/") || input.startsWith("//")) return null
        val commandText = input.drop(1)
        val separator = commandText.indexOfFirst(Char::isWhitespace)
        val name = if (separator < 0) commandText else commandText.substring(0, separator)
        if (!name.matches(Regex("[A-Za-z][A-Za-z0-9_-]*"))) return null
        val args = if (separator < 0) "" else commandText.substring(separator).trim()
        return ParsedRuntimeCommand(name, args)
    }

    private fun sendRuntimeCommand(rawText: String, command: ParsedRuntimeCommand) {
        if (isCurrentSessionRunActive()) {
            _ui.update { it.copy(activityLine = "Agent 正在运行，请等待完成或发送 /stop") }
            return
        }
        if (_ui.value.commandExecuting) return
        val startedAt = System.currentTimeMillis()
        resetAssistantStream()
        pushBubble(ChatBubble(id = "u-$startedAt", role = "user", content = rawText, createdAt = nowIsoTimestamp()))
        _ui.update {
            it.copy(
                composer = "",
                isResponding = false,
                commandExecuting = true,
                commandExecution = null,
                commandsError = null,
                activityLine = "Running /${command.name}",
                progressSteps = emptyList(),
            )
        }

        viewModelScope.launch {
            val catalogResult = container.api.listCommands()
            val commands = catalogResult.getOrNull()
            if (commands == null) {
                val error = catalogResult.exceptionOrNull()?.message ?: "无法读取 Runtime 命令列表"
                finishRuntimeCommand(
                    command = command.name,
                    output = "无法读取 Runtime 命令列表：$error",
                    success = false,
                    error = error,
                )
                return@launch
            }
            _ui.update { it.copy(commands = commands, commandsError = null) }

            val runtimeCommand = commands.firstOrNull { it.name == command.name }
            if (runtimeCommand == null) {
                finishRuntimeCommand(
                    command = command.name,
                    output = "未找到 Runtime 命令 /${command.name}。请在 Runtime → Commands 查看当前服务支持的命令。",
                    success = false,
                )
                return@launch
            }

            val result = container.api.runCommand(runtimeCommand.name, command.args, _ui.value.settings.sessionId)
            val execution = result.getOrNull()
            if (execution == null) {
                val error = result.exceptionOrNull()?.message ?: "命令执行失败"
                finishRuntimeCommand(
                    command = runtimeCommand.name,
                    output = "执行 /${runtimeCommand.name} 失败：$error",
                    success = false,
                    error = error,
                )
                return@launch
            }

            val success = execution.ok
            val status = if (success) "执行完成" else "执行失败"
            finishRuntimeCommand(
                command = runtimeCommand.name,
                output = buildString {
                    append("/${runtimeCommand.name} · $status")
                    if (execution.output.isNotBlank()) append("\n\n${execution.output}")
                },
                success = success,
                execution = execution,
            )
            if (success) refreshAfterCommand(runtimeCommand.name)
        }
    }

    private fun finishRuntimeCommand(
        command: String,
        output: String,
        success: Boolean,
        error: String? = null,
        execution: CommandExecution? = null,
    ) {
        val now = System.currentTimeMillis()
        pushBubble(ChatBubble(id = "runtime-command-$now", role = "assistant", content = output, createdAt = nowIsoTimestamp()))
        _ui.update {
            it.copy(
                isResponding = false,
                commandExecuting = false,
                commandExecution = execution,
                commandsError = error,
                activityLine = if (success) "/$command completed" else "/$command failed",
                progressSteps = emptyList(),
            )
        }
    }

    private fun refreshAfterCommand(command: String) {
        when (command) {
            "remember", "remember_long", "memdecay", "promote" -> refreshMemory()
            "rename" -> refreshSessions()
        }
    }

    fun cancelRun() {
        val sessionId = currentSessionId()
        activeRuns[sessionId]?.let { run ->
            container.wsClient.cancel(run.handle)
            releaseRun(sessionId)
        }
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
            val includeIsolated = _ui.value.memoryGraphIsolated
            val (stats, recall, graph) = coroutineScope {
                val statsRequest = async { container.api.memoryStats() }
                val recallRequest = async { container.api.recallMemory(q) }
                val graphRequest = async { container.api.memoryGraph(includeIsolated = includeIsolated) }
                Triple(statsRequest.await(), recallRequest.await(), graphRequest.await())
            }
            _ui.update {
                it.copy(
                    memoryLoading = false,
                    memoryStats = stats.getOrNull() ?: it.memoryStats,
                    memoryEntries = recall.getOrNull() ?: it.memoryEntries,
                    memoryGraphNodes = graph.getOrNull()?.nodes ?: it.memoryGraphNodes,
                    memoryGraphEdges = graph.getOrNull()?.edges ?: it.memoryGraphEdges,
                    memoryGraphSummary = graph.getOrNull()?.let { g ->
                        "nodes=${g.nodes.size} edges=${g.edges.size} isolated=${g.isolatedCount ?: 0} notes=${g.totalNotes ?: "?"} unresolved=${g.unresolved ?: 0}" +
                            if (g.truncated == true) " truncated" else ""
                    },
                    memoryError = recall.exceptionOrNull()?.message
                        ?: graph.exceptionOrNull()?.message
                        ?: stats.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun updateMemoryTraceQuery(value: String) { _ui.update { it.copy(memoryTraceQuery = value) } }

    fun setMemoryTraceDepth(value: Int) { _ui.update { it.copy(memoryTraceDepth = value.coerceIn(1, 3)) } }

    fun setMemoryGraphIsolated(value: Boolean) {
        _ui.update { it.copy(memoryGraphIsolated = value, memoryLoading = true, memoryError = null) }
        viewModelScope.launch {
            val graph = container.api.memoryGraph(includeIsolated = value)
            _ui.update { current ->
                val result = graph.getOrNull()
                current.copy(
                    memoryLoading = false,
                    memoryGraphNodes = result?.nodes ?: current.memoryGraphNodes,
                    memoryGraphEdges = result?.edges ?: current.memoryGraphEdges,
                    memoryGraphSummary = result?.let { "nodes=${it.nodes.size} edges=${it.edges.size} isolated=${it.isolatedCount ?: 0} notes=${it.totalNotes ?: "?"} unresolved=${it.unresolved ?: 0}" + if (it.truncated == true) " truncated" else "" },
                    memoryError = graph.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun runMemoryTrace(query: String = _ui.value.memoryTraceQuery) {
        val q = query.trim()
        if (q.isEmpty()) return
        val depth = _ui.value.memoryTraceDepth
        _ui.update { it.copy(memoryTraceQuery = q, memoryTraceLoading = true, memoryTraceError = null) }
        viewModelScope.launch {
            val result = container.api.memoryRecallTrace(q, depth)
            _ui.update { it.copy(memoryTraceLoading = false, memoryTrace = result.getOrNull() ?: it.memoryTrace, memoryTraceError = result.exceptionOrNull()?.message) }
        }
    }

    fun selectMemoryTrace(trace: MemorySearchTrace?) {
        _ui.update { it.copy(memoryTrace = trace, memoryTraceError = null) }
    }


    fun updateTrajectoryQuery(value: String) {
        _ui.update { it.copy(trajectoryQuery = value) }
    }

    fun setTrajectoryFilter(filter: TrajectoryFilter) {
        _ui.update { it.copy(trajectoryFilter = filter) }
    }

    fun updateTaskQuery(value: String) {
        _ui.update { it.copy(taskQuery = value) }
    }

    fun setTaskFilter(filter: TaskFilter) {
        _ui.update { it.copy(taskFilter = filter) }
    }

    fun refreshTasks() {
        viewModelScope.launch { refreshTasksNow() }
    }

    private suspend fun refreshTasksNow() {
        _ui.update { it.copy(tasksLoading = true, tasksError = null) }
        val (unified, legacy) = coroutineScope {
            val unifiedRequest = async { container.api.listTaskRecords() }
            val legacyRequest = async { container.api.listLegacyTasks() }
            unifiedRequest.await() to legacyRequest.await()
        }
        val unifiedRecords = unified.getOrNull().orEmpty()
        val unifiedIds = unifiedRecords.mapTo(mutableSetOf()) { it.id }
        val unifiedSummaries = unifiedRecords.map { it.toTaskSummary() }
        val legacySummaries = legacy.getOrNull()
            .orEmpty()
            .filterNot { it.id in unifiedIds }
            .map { it.toTaskNode().summary }
        val merged = addChildCounts((unifiedSummaries + legacySummaries)
            .sortedByDescending { it.lastActivityAt.orEmpty() })
        val errors = listOfNotNull(
            unified.exceptionOrNull()?.message?.let { "tasks: $it" },
            legacy.exceptionOrNull()?.message?.let { "legacy: $it" },
        )
        val hasData = unified.isSuccess || legacy.isSuccess
        val selectedId = _ui.value.selectedTaskId
        _ui.update {
            it.copy(
                tasksLoading = false,
                tasks = if (hasData) merged else it.tasks,
                tasksError = errors.takeIf { messages -> messages.isNotEmpty() }?.joinToString(" · "),
            )
        }
        if (selectedId != null && merged.any { it.id == selectedId }) {
            loadTaskDetailNow(selectedId, showLoading = false)
        }
    }

    private fun addChildCounts(tasks: List<TaskSummary>): List<TaskSummary> {
        val counts = tasks.mapNotNull { it.parentId?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        return tasks.map { task ->
            task.copy(childCount = maxOf(task.childCount, counts[task.id] ?: 0))
        }
    }

    private fun startTaskPolling() {
        if (taskPollingJob?.isActive == true) return
        taskPollingJob = viewModelScope.launch {
            _ui.update { it.copy(taskPolling = true) }
            while (isActive && _ui.value.destination == AppDestination.Tasks) {
                refreshTasksNow()
                delay(TASK_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopTaskPolling() {
        taskPollingJob?.cancel()
        taskPollingJob = null
        _ui.update { it.copy(taskPolling = false) }
    }

    fun selectTask(id: String) {
        if (id.isBlank()) return
        _ui.update {
            it.copy(
                selectedTaskId = id,
                selectedTask = null,
                taskDetailLoading = true,
                taskDetailError = null,
            )
        }
        viewModelScope.launch { loadTaskDetailNow(id, showLoading = false) }
    }

    fun clearSelectedTask() {
        _ui.update {
            it.copy(
                selectedTaskId = null,
                selectedTask = null,
                taskDetailLoading = false,
                taskDetailError = null,
            )
        }
    }

    private suspend fun loadTaskDetailNow(id: String, showLoading: Boolean) {
        if (showLoading) _ui.update { it.copy(taskDetailLoading = true, taskDetailError = null) }
        val summary = _ui.value.tasks.firstOrNull { it.id == id }
        if (summary == null) {
            _ui.update { it.copy(taskDetailLoading = false, taskDetailError = "Task not found") }
            return
        }
        val detailResult = if (summary.origin == TaskOrigin.Legacy) {
            container.api.getLegacyTask(id).map { legacy ->
                TaskDetail(
                    root = legacy.toTaskNode(),
                    result = legacy.result,
                    origin = TaskOrigin.Legacy,
                )
            }
        } else {
            val tree = container.api.getTaskTree(id)
            if (tree.isFailure) {
                tree.map { node -> TaskDetail(root = node.toTaskNode(), origin = TaskOrigin.Unified) }
            } else {
                val events = container.api.getTaskEvents(id).getOrDefault(emptyList())
                val result = container.api.getTaskResult(id).getOrNull()
                tree.map { node ->
                    TaskDetail(
                        root = node.toTaskNode(result),
                        events = events,
                        result = result,
                        origin = TaskOrigin.Unified,
                    )
                }
            }
        }
        _ui.update { current ->
            if (current.selectedTaskId != id) current else current.copy(
                selectedTask = detailResult.getOrNull() ?: current.selectedTask,
                taskDetailLoading = false,
                taskDetailError = detailResult.exceptionOrNull()?.message,
            )
        }
    }

    fun cancelTask(task: TaskSummary) {
        if (task.status.isTerminalTaskStatus()) return
        viewModelScope.launch {
            _ui.update { it.copy(taskDetailError = null) }
            val result = container.api.cancelTask(task.id, task.origin)
            if (result.isSuccess) {
                refreshTasksNow()
            } else {
                _ui.update { it.copy(taskDetailError = result.exceptionOrNull()?.message ?: "Cancel request failed") }
            }
        }
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
                refreshAfterCommand(command.name)
            }
        }
    }

    fun createSession(title: String = "Android session") {
        viewModelScope.launch {
            val result = container.api.createSession(title)
            result.onSuccess { session ->
                container.settingsRepository.update { it.copy(sessionId = session.id) }
                foregroundRunConnectionId = null
                resetAssistantStream()
                toolStepIndex.clear()
                _ui.update {
                    it.copy(
                        bubbles = emptyList(),
                        isResponding = activeRuns.containsKey(session.id),
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

    private companion object {
        const val TASK_POLL_INTERVAL_MS = 3_000L
    }

    override fun onCleared() {
        stopTaskPolling()
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
