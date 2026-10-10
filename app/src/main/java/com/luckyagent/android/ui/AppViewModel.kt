package com.luckyagent.android.ui

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckyagent.android.data.AppContainer
import com.luckyagent.android.data.notification.RuntimeNoticeKind
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.cache.HistorySync
import com.luckyagent.android.data.cache.LatestMerge
import com.luckyagent.android.data.cache.MessagePage
import com.luckyagent.android.data.cache.SessionCachePolicy
import com.luckyagent.android.data.cache.historySyncAction
import com.luckyagent.android.data.cache.mergeLatestPage
import com.luckyagent.android.data.cache.olderHistoryOffset
import com.luckyagent.android.data.cache.prependOlderPage
import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ModelRef
import com.luckyagent.android.data.api.modelKindLabel
import com.luckyagent.android.data.api.MemoryGraphEdge
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.data.api.MemoryStats
import com.luckyagent.android.data.api.MemorySearchTrace
import com.luckyagent.android.data.api.ReceivedMemoryTrace
import com.luckyagent.android.data.api.CommandExecution
import com.luckyagent.android.data.api.CronJob
import com.luckyagent.android.data.api.AutonomyDashboardResponse
import com.luckyagent.android.data.api.AutonomyTaskDetailResponse
import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.CompactTraceRecord
import com.luckyagent.android.data.api.ContextInspectResponse
import com.luckyagent.android.data.api.TokenUsage
import com.luckyagent.android.data.api.ApprovalOption
import com.luckyagent.android.data.api.PendingApproval
import com.luckyagent.android.data.api.LuckyAction
import com.luckyagent.android.data.api.LuckyCommand
import com.luckyagent.android.data.api.parseLuckyCommand
import com.luckyagent.android.data.api.RuntimeCommand
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SessionPatchRequest
import com.luckyagent.android.data.api.SessionToolTrace
import com.luckyagent.android.data.api.GatewayStatus
import com.luckyagent.android.data.api.SkillSummary
import com.luckyagent.android.data.api.SkillsResponse
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.data.api.TaskDetail
import com.luckyagent.android.data.api.TaskOrigin
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.data.api.toTaskNode
import com.luckyagent.android.data.api.toTaskSummary
import com.luckyagent.android.data.api.WsChatHandle
import com.luckyagent.android.data.api.WsEvent
import com.luckyagent.android.data.settings.ClientSettings
import com.luckyagent.android.data.settings.RuntimeEndpoint
import com.luckyagent.android.data.update.AvailableUpdate
import com.luckyagent.android.BuildConfig
import com.luckyagent.android.ui.util.MessageQuote
import com.luckyagent.android.ui.util.TokenFormat
import com.luckyagent.android.ui.util.bubbleCopyText
import com.luckyagent.android.ui.util.buildOutboundMessage
import com.luckyagent.android.ui.util.quoteFromBubble
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.io.File
import java.util.UUID

enum class AppDestination {
    Chat, Tasks, Background, Cron, Trajectory, Gateways, Skills, Settings, Memory
}

enum class TrajectoryFilter { All, Success, Failure }

enum class TaskFilter { All, Active, Completed, Failed, Cancelled }

enum class BackgroundFilter { All, Ready, Running, Blocked, Done }

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
    val quote: MessageQuote? = null,
)

data class ChatMedia(
    val descriptor: MediaAttachment,
    val localUri: String? = null,
)

enum class UpdatePhase { Idle, Checking, Available, Downloading, ReadyToInstall, UpToDate, Error }

data class AppUpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val latest: AvailableUpdate? = null,
    val downloadedPath: String? = null,
    val downloadProgress: Int? = null,
    val installBlockReason: String? = null,
    val error: String? = null,
)

data class PendingMedia(
    val id: String,
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val descriptor: MediaAttachment? = null,
    val error: String? = null,
)

data class LuckyPendingSegment(
    val id: String,
    val sessionId: String,
    val preview: String,
    val message: String,
    val attachments: List<MediaAttachment> = emptyList(),
    val error: String? = null,
)

data class AttachmentNotice(
    val text: String,
    val isError: Boolean = false,
    val isComplete: Boolean = false,
    val openUri: String? = null,
    val mimeType: String? = null,
)

data class AppUiState(
    val destination: AppDestination = AppDestination.Chat,
    val settings: ClientSettings = ClientSettings(),
    val sessions: List<RuntimeSession> = emptyList(),
    val sessionsLoading: Boolean = false,
    val sessionsError: String? = null,
    /** True when the visible sessions or history came from disk after a failed refresh. */
    val showingOfflineCache: Boolean = false,
    val sessionQuery: String = "",
    /** Session IDs currently running an agent turn (foreground or background). */
    val workingSessionIds: Set<String> = emptySet(),
    val bubbles: List<ChatBubble> = emptyList(),
    val historyLoading: Boolean = false,
    val historyLoadingMore: Boolean = false,
    val historyHasMore: Boolean = false,
    val composer: String = "",
    val pendingMedia: List<PendingMedia> = emptyList(),
    /** Messages already handed to the runtime's per-session FIFO. */
    val outboundQueue: List<OutboundQueueItem> = emptyList(),
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
    val commandsError: String? = null,
    val commandExecuting: Boolean = false,
    val commandExecution: CommandExecution? = null,
    val activityLine: String? = null,
    val attachmentNotice: AttachmentNotice? = null,
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
    val backgroundDashboard: AutonomyDashboardResponse? = null,
    val backgroundTasks: List<AutonomyTaskSummary> = emptyList(),
    val backgroundLoading: Boolean = false,
    val backgroundError: String? = null,
    val backgroundFilter: BackgroundFilter = BackgroundFilter.All,
    val backgroundQuery: String = "",
    val selectedBackgroundTaskId: String? = null,
    val selectedBackgroundTask: AutonomyTaskDetailResponse? = null,
    val backgroundDetailLoading: Boolean = false,
    val backgroundDetailError: String? = null,
    val backgroundPolling: Boolean = false,
    val cronJobs: List<CronJob> = emptyList(),
    val cronRunning: Boolean = false,
    val cronCount: Int = 0,
    val cronLoading: Boolean = false,
    val cronError: String? = null,
    val pendingQuote: MessageQuote? = null,
    /** Server-owned /lucky collector for the visible session. */
    val luckyActive: Boolean = false,
    val luckySegments: Int = 0,
    val luckyAttachments: Int = 0,
    /** Segments accepted locally but not yet acknowledged by the runtime collector. */
    val luckyPending: List<LuckyPendingSegment> = emptyList(),
    val snackbarMessage: String? = null,
    val appearanceBusy: Boolean = false,
    val personaName: String = "",
    val models: List<ModelRef> = emptyList(),
    val activeModels: Map<String, ModelRef> = emptyMap(),
    val modelsLoaded: Boolean = false,
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val modelSwitchingKey: String? = null,
    val contextInspect: ContextInspectResponse? = null,
    val contextInspectLoading: Boolean = false,
    val contextInspectError: String? = null,
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val approvalsLoading: Boolean = false,
    val approvalsError: String? = null,
    val update: AppUpdateUiState = AppUpdateUiState(),
)

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(AppUiState(settings = container.settingsRepository.snapshot()))
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private val backStack = ArrayDeque<AppDestination>()
    private var eventsJob: Job? = null
    private var settingsReconnectJob: Job? = null
    private var taskPollingJob: Job? = null
    private var backgroundPollingJob: Job? = null
    private var cronPollingJob: Job? = null
    private val taskFingerprints = mutableMapOf<String, String>()
    private val backgroundFingerprints = mutableMapOf<String, String>()
    private val cronFingerprints = mutableMapOf<String, String>()
    private var progressWatchReady = false
    private var approvalPollingJob: Job? = null
    private var historyJob: Job? = null
    private var contextInspectJob: Job? = null
    private var historySessionId: String? = null
    private var historyMessages: List<ProviderMessage> = emptyList()
    private var historyStartIndex = 0
    private var assistantBufferId: String? = null
    private var assistantBufferIndex: Int? = null
    private val assistantPending = StringBuilder()
    private var assistantFlushJob: Job? = null
    private var currentTurnId = "turn-0"
    private val toolStepIndex = mutableMapOf<String, String>()
    private val mediaUploadSemaphore = Semaphore(1)
    private val outboundChatQueue = OutboundChatQueue()
    /** One local dispatcher per session keeps sends FIFO even while the user keeps typing. */
    private val outboundDispatchJobs = mutableMapOf<String, Job>()
    private val luckyPending = LinkedHashMap<String, LuckyPendingSegment>()
    private data class ActiveRun(
        val handle: WsChatHandle,
    )

    private val activeRuns = mutableMapOf<String, LinkedHashMap<String, ActiveRun>>()
    private val foregroundRequestIds = mutableMapOf<String, String>()
    private var appInForeground = false
    private var updateDownloadJob: Job? = null
    private var attachmentNoticeJob: Job? = null

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { s ->
                var endpointChanged = false
                _ui.update { current ->
                    endpointChanged = current.settings.apiBase != s.apiBase ||
                        current.settings.apiKey != s.apiKey ||
                        current.settings.useBearer != s.useBearer
                    current.copy(
                        settings = s,
                        pendingQuote = if (current.settings.sessionId != s.sessionId) null else current.pendingQuote,
                        models = if (endpointChanged) emptyList() else current.models,
                        activeModels = if (endpointChanged) emptyMap() else current.activeModels,
                        modelsLoaded = if (endpointChanged) false else current.modelsLoaded,
                        modelsLoading = if (endpointChanged) false else current.modelsLoading,
                        modelsError = if (endpointChanged) null else current.modelsError,
                    )
                }
                if (endpointChanged) resetForEndpointChange(s)
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
        ensureRuntimeWatch()
        restoreCachedSession()
        refreshSessions()
        refreshPersona()
        connectSocket()
        startApprovalPolling()
        // Keep the composer chip useful as soon as the runtime is reachable; this is
        // asynchronous and never gates sending a chat message.
        loadModels()
    }

    /** Drop the previous runtime's in-memory history before loading the new namespace. */
    private fun resetForEndpointChange(settings: com.luckyagent.android.data.settings.ClientSettings) {
        val target = settings.sessionId.ifBlank { "android-main" }
        historyJob?.cancel()
        contextInspectJob?.cancel()
        historySessionId = target
        historyMessages = emptyList()
        historyStartIndex = 0
        resetAssistantStream()
        toolStepIndex.clear()
        _ui.update {
            it.copy(
                sessions = emptyList(),
                sessionsLoading = true,
                sessionsError = null,
                showingOfflineCache = false,
                bubbles = emptyList(),
                historyLoading = true,
                historyLoadingMore = false,
                historyHasMore = false,
                isResponding = false,
                outboundQueue = outboundChatQueue.items(target),
                pendingQuote = null,
                contextInspect = null,
                contextInspectError = null,
                contextInspectLoading = true,
                luckyActive = false,
                luckySegments = 0,
                luckyAttachments = 0,
                luckyPending = luckyPending.values.filter { segment -> segment.sessionId == target },
                progressSteps = emptyList(),
                activityLine = "loading history…",
            )
        }
        loadHistory(target)
        refreshContextInspect(target)
    }

    private fun handleApprovalEvent(data: kotlinx.serialization.json.JsonElement?) {
        val obj = data as? kotlinx.serialization.json.JsonObject ?: return
        fun text(key: String): String? = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)
            ?.content
            ?.takeIf { it.isNotBlank() }
        val id = text("request_id") ?: return
        val kind = text("kind") ?: "approval"
        val prompt = text("prompt") ?: text("reason") ?: text("summary")
        val tool = text("tool")
        val credential = kind.equals("credential", ignoreCase = true)
        val options = if (credential) {
            listOf(
                ApprovalOption(id = "submit", label = "保存凭据", kind = "submit"),
                ApprovalOption(id = "cancel", label = "取消", kind = "cancel"),
            )
        } else if (kind.equals("input", ignoreCase = true)) {
            listOf(
                ApprovalOption(id = "submit", label = "提交", kind = "submit"),
                ApprovalOption(id = "cancel", label = "取消", kind = "cancel"),
            )
        } else {
            listOf(
                ApprovalOption(id = "allow", label = "允许", kind = "allow_once"),
                ApprovalOption(id = "deny", label = "拒绝", kind = "deny"),
            )
        }
        val approval = PendingApproval(
            provider = text("provider") ?: "runtime",
            id = id,
            method = kind,
            sessionId = text("session_id") ?: currentSessionId(),
            reason = prompt,
            summary = text("summary") ?: prompt,
            params = buildJsonObject {
                prompt?.let { put("prompt", it) }
                tool?.let { put("tool", it) }
                put("kind", kind)
                if (credential) put("secure", true)
                (obj["fields"] as? kotlinx.serialization.json.JsonObject)?.let { put("fields", it) }
            },
            options = options,
        )
        _ui.update { state ->
            state.copy(
                pendingApprovals = listOf(approval) + state.pendingApprovals.filterNot { it.id == id },
                approvalsError = null,
                activityLine = when {
                    credential -> "需要填写凭据"
                    kind.equals("input", ignoreCase = true) -> "需要你补充信息"
                    else -> "需要审批"
                },
            )
        }
    }

    private fun startApprovalPolling() {
        approvalPollingJob?.cancel()
        approvalPollingJob = viewModelScope.launch {
            while (isActive) {
                refreshApprovalsInternal()
                delay(1500)
            }
        }
    }

    private suspend fun refreshApprovalsInternal() {
        val showLoading = _ui.value.pendingApprovals.isEmpty()
        if (showLoading) _ui.update { it.copy(approvalsLoading = true, approvalsError = null) }
        container.api.listApprovals()
            .onSuccess { approvals ->
                _ui.update {
                    it.copy(
                        pendingApprovals = approvals,
                        approvalsLoading = false,
                        approvalsError = null,
                    )
                }
            }
            .onFailure { error ->
                _ui.update {
                    it.copy(
                        approvalsLoading = false,
                        approvalsError = error.message ?: "无法读取待审批请求",
                    )
                }
            }
    }

    fun refreshApprovals() {
        viewModelScope.launch { refreshApprovalsInternal() }
    }

    fun resolveApproval(approval: PendingApproval, decision: String, input: String = "") {
        viewModelScope.launch {
            _ui.update { it.copy(approvalsError = null, pendingApprovals = it.pendingApprovals.filterNot { item -> item.id == approval.id }) }
            val sent = container.wsClient.sendApprovalResponse(
                sessionId = approval.sessionId?.takeIf { it.isNotBlank() } ?: currentSessionId(),
                requestId = approval.id,
                provider = approval.provider,
                decision = decision,
                input = input,
            )
            if (sent) {
                refreshApprovalsInternal()
                return@launch
            }
            container.api.resolveApproval(approval, decision, input)
                .onSuccess { refreshApprovalsInternal() }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            pendingApprovals = listOf(approval) + it.pendingApprovals.filterNot { item -> item.id == approval.id },
                            approvalsError = error.message ?: "审批处理失败",
                        )
                    }
                }
        }
    }

    private fun observeWs() {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            container.wsClient.events.collect { event ->
                try {
                    val env = event.envelope
                    ensureReplayedRun(event)
                    val foreground = isForegroundEvent(event)
                    when (env.type) {
                        "stream_chunk", "assistant_delta", "delta", "chunk" -> {
                            if (!foreground) return@collect
                            val piece = extractText(env.data) ?: return@collect
                            updatePhase("response", "Preparing response")
                            appendAssistant(piece)
                        }
                        "stream_end", "assistant_message", "final", "done", "chat_done", "message" -> {
                            val requestId = eventRequestId(event)
                            if (requestId == null) {
                                val piece = extractFullResponse(env.data) ?: extractText(env.data)
                                if (!piece.isNullOrBlank()) {
                                    if (event.sessionId == currentSessionId()) {
                                        pushBubble(ChatBubble(id = env.id ?: "cron-${System.currentTimeMillis()}", role = "assistant", content = piece, createdAt = env.timestamp))
                                        refreshSessions()
                                    }
                                    notifyCronMessage(event.sessionId, env.id ?: env.eventId, piece)
                                }
                                return@collect
                            }
                            if (!completeRun(event)) return@collect
                            finishOutboundRequest(event.sessionId, requestId, success = true)
                            val piece = extractFullResponse(env.data) ?: extractText(env.data)
                            val attachments = distinctMedia(
                                extractAttachments(env.data) + extractArtifactAttachments(piece.orEmpty()),
                            )
                            if (foreground) {
                                finishAssistant(
                                    full = piece,
                                    createdAt = extractField(env.data, "created_at") ?: env.timestamp,
                                    usage = extractUsage(env.data),
                                    attachments = attachments,
                                )
                                notifyChatCompleted(event.sessionId, requestId, piece ?: _ui.value.bubbles.lastOrNull { it.role == "assistant" }?.content, foreground)
                                _ui.update { it.copy(isResponding = false) }
                                prepareNextForeground(event.sessionId)
                            } else {
                                notifyChatCompleted(event.sessionId, requestId, piece, false)
                                finishBackgroundRun(event.sessionId)
                            }
                            refreshSessions()
                            if (foreground) refreshContextInspect()
                        }
                        "tool_call", "tool" -> if (foreground) handleToolCall(env.data)
                        "tool_result" -> if (foreground) handleToolResult(env.data)
                        "approval", "approval_required" -> handleApprovalEvent(env.data)
                        "approval_resolved" -> refreshApprovals()
                        "cancel", "cancelled" -> {
                            val requestId = eventRequestId(event)
                            if (!completeRun(event)) return@collect
                            finishOutboundRequest(event.sessionId, requestId, success = true)
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
                                prepareNextForeground(event.sessionId)
                            } else {
                                finishBackgroundRun(event.sessionId)
                            }
                        }
                        "error" -> {
                            val requestId = eventRequestId(event)
                            if (!completeRun(event)) return@collect
                            val msg = env.error
                                ?: extractField(env.data, "message")
                                ?: extractText(env.data)
                                ?: "Unknown error"
                            finishOutboundRequest(event.sessionId, requestId, success = false, error = msg)
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
                                prepareNextForeground(event.sessionId)
                            } else {
                                finishBackgroundRun(event.sessionId)
                            }
                        }
                        "compact" -> if (foreground) handleCompactEvent(env.data)
                        "task_event" -> handleTaskEvent(env.data)
                        "status", "info" -> {
                            val state = extractField(env.data, "state")
                            val message = extractField(env.data, "message") ?: extractText(env.data)
                            when (state?.lowercase()) {
                                "approval_resolved" -> refreshApprovals()
                                "thinking" -> if (foreground) updatePhase("thinking", "Thinking through the request")
                                "executing" -> if (foreground) updatePhase("executing", "Working on your request")
                                "lucky" -> if (event.sessionId == currentSessionId() && !message.isNullOrBlank()) {
                                    // Lucky acknowledgements have no active run ID,
                                    // so they are not foreground events. They still
                                    // belong to the visible session.
                                    val requestId = event.envelope.parentId
                                    if (requestId != null && message.contains("已收集")) {
                                        acknowledgeLuckySegment(requestId)
                                    }
                                    applyLuckyStatus(message)
                                    if (!message.contains("已收集")) {
                                        pushBubble(
                                            ChatBubble(
                                                id = "lucky-${System.currentTimeMillis()}",
                                                role = "assistant",
                                                content = message,
                                                createdAt = nowIsoTimestamp(),
                                            ),
                                        )
                                    }
                                }
                                "idle" -> {
                                    val requestId = eventRequestId(event)
                                    if (!completeRun(event)) return@collect
                                    finishOutboundRequest(event.sessionId, requestId, success = true)
                                    if (foreground) {
                                        finishAssistant(null)
                                        notifyChatCompleted(event.sessionId, requestId, _ui.value.bubbles.lastOrNull { it.role == "assistant" }?.content, true)
                                        _ui.update { current ->
                                            current.copy(
                                                isResponding = false,
                                                activityLine = "complete",
                                                progressSteps = current.progressSteps.map { step ->
                                                    if (step.status == ProgressStatus.Active) step.copy(status = ProgressStatus.Complete) else step
                                                },
                                            )
                                        }
                                        prepareNextForeground(event.sessionId)
                                    } else {
                                        notifyChatCompleted(event.sessionId, requestId, null, false)
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
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    // A malformed replay event must not cancel the collector and
                    // take down the whole ViewModel. Keep the error generic because
                    // event payloads can contain credentials or user content.
                    _ui.update {
                        it.copy(activityLine = "WebSocket event ignored · ${error::class.simpleName ?: "error"}")
                    }
                }
            }
        }
    }

    private fun currentSessionId(): String =
        container.settingsRepository.snapshot().sessionId.ifBlank { "android-main" }

    private fun currentCacheConnectionMatches(apiBase: String, apiKey: String): Boolean {
        val current = container.settingsRepository.snapshot()
        return current.apiBase.trim().trimEnd('/').equals(apiBase.trim().trimEnd('/'), ignoreCase = true) &&
            current.apiKey.trim() == apiKey.trim()
    }

    private fun publishOutboundQueue(sessionId: String = currentSessionId()) {
        val items = outboundChatQueue.items(sessionId)
        _ui.update { current ->
            if (currentSessionId() == sessionId) current.copy(outboundQueue = items) else current
        }
    }

    private fun queueItemId(): String = "outbound-${System.currentTimeMillis()}-${UUID.randomUUID()}"

    private fun dispatchNextOutbound(sessionId: String) {
        if (activeRuns[sessionId]?.isNotEmpty() == true) return
        if (outboundDispatchJobs[sessionId]?.isActive == true) return
        val item = outboundChatQueue.firstQueued(sessionId) ?: return
        outboundChatQueue.update(item.id, status = OutboundQueueStatus.Sending, clearError = true)
        publishOutboundQueue(sessionId)
        val handle = container.wsClient.sendChat(item.message, attachments = item.attachments, sessionId = sessionId)
        if (handle != null) {
            markOutboundSent(item.id, handle)
            registerRun(handle)
            return
        }

        // One retry per session. Later composer submits stay queued and cannot
        // start a second connection attempt for the same session.
        outboundChatQueue.update(item.id, status = OutboundQueueStatus.Queued, error = "正在连接")
        publishOutboundQueue(sessionId)
        container.wsClient.connect(sessionId)
        outboundDispatchJobs[sessionId] = viewModelScope.launch {
            delay(450)
            val retryHandle = container.wsClient.sendChat(item.message, attachments = item.attachments, sessionId = sessionId)
            if (retryHandle == null) {
                markOutboundFailed(item.id, sessionId, "WebSocket 未连接，请检查 API Base / Key")
            } else {
                markOutboundSent(item.id, retryHandle)
                registerRun(retryHandle)
            }
            outboundDispatchJobs.remove(sessionId)
            if (activeRuns[sessionId].isNullOrEmpty()) dispatchNextOutbound(sessionId)
        }
    }

    private fun markOutboundSent(itemId: String, handle: WsChatHandle) {
        outboundChatQueue.update(
            id = itemId,
            status = OutboundQueueStatus.Sending,
            requestId = handle.requestId,
            clearError = true,
        )
        publishOutboundQueue(handle.sessionId)
    }

    private fun finishOutboundRequest(
        sessionId: String,
        requestId: String?,
        success: Boolean,
        error: String? = null,
    ) {
        if (requestId.isNullOrBlank()) return
        val item = outboundChatQueue.findByRequest(sessionId, requestId) ?: return
        if (success) {
            outboundChatQueue.remove(item.id)
        } else {
            outboundChatQueue.update(
                item.id,
                status = OutboundQueueStatus.Failed,
                error = error ?: "消息发送失败",
            )
        }
        publishOutboundQueue(sessionId)
        dispatchNextOutbound(sessionId)
    }

    private fun markOutboundFailed(itemId: String, sessionId: String, error: String) {
        outboundChatQueue.update(itemId, status = OutboundQueueStatus.Failed, error = error)
        publishOutboundQueue(sessionId)
    }

    fun retryOutboundMessage(itemId: String) {
        val item = outboundChatQueue.items(currentSessionId()).firstOrNull { it.id == itemId }
            ?: return
        if (item.status != OutboundQueueStatus.Failed) return
        outboundChatQueue.update(item.id, status = OutboundQueueStatus.Queued, clearError = true)
        publishOutboundQueue(item.sessionId)
        dispatchNextOutbound(item.sessionId)
    }

    private fun sessionRuns(sessionId: String): LinkedHashMap<String, ActiveRun> =
        activeRuns.getOrPut(sessionId) { LinkedHashMap() }

    private fun isCurrentSessionRunActive(): Boolean = activeRuns[currentSessionId()]?.isNotEmpty() == true

    private fun workingSessionIdsSnapshot(): Set<String> =
        activeRuns.filterValues { it.isNotEmpty() }.keys.toSet()

    private fun publishWorkingSessions() {
        val working = workingSessionIdsSnapshot()
        _ui.update { current ->
            if (current.workingSessionIds == working) current
            else current.copy(workingSessionIds = working)
        }
    }

    private fun registerRun(handle: WsChatHandle) {
        val runs = sessionRuns(handle.sessionId)
        val existing = runs[handle.requestId]
        if (existing != null) {
            if (handle.ownsLease && !existing.handle.ownsLease) {
                runs[handle.requestId] = ActiveRun(handle)
                publishWorkingSessions()
            }
            return
        }
        runs[handle.requestId] = ActiveRun(handle)
        foregroundRequestIds.putIfAbsent(handle.sessionId, handle.requestId)
        publishWorkingSessions()
        refreshApprovals()
    }

    private fun eventRequestId(event: WsEvent): String? =
        event.envelope.parentId
            ?: event.envelope.runId?.takeIf { id -> activeRuns[event.sessionId]?.containsKey(id) == true }

    private fun isForegroundEvent(event: WsEvent): Boolean =
        event.sessionId == currentSessionId() &&
            eventRequestId(event) == foregroundRequestIds[event.sessionId]

    private fun completeRun(event: WsEvent): Boolean {
        val runs = activeRuns[event.sessionId] ?: return false
        val requestId = eventRequestId(event) ?: return false
        val run = runs.remove(requestId) ?: return false
        if (run.handle.ownsLease) container.wsClient.release(run.handle.connectionId)
        if (runs.isEmpty()) {
            activeRuns.remove(event.sessionId)
            foregroundRequestIds.remove(event.sessionId)
        } else if (foregroundRequestIds[event.sessionId] == requestId) {
            foregroundRequestIds[event.sessionId] = runs.keys.first()
        }
        publishWorkingSessions()
        return true
    }

    private fun clearRuns(sessionId: String) {
        val runs = activeRuns.remove(sessionId).orEmpty()
        runs.values.forEach { run ->
            if (run.handle.ownsLease) container.wsClient.release(run.handle.connectionId)
        }
        foregroundRequestIds.remove(sessionId)
        publishWorkingSessions()
    }

    private fun finishBackgroundRun(sessionId: String) {
        if (sessionId != currentSessionId()) return
        resetAssistantStream()
        loadHistory(sessionId)
    }

    private fun notifyChatCompleted(sessionId: String, requestId: String?, content: String?, foreground: Boolean) {
        val settings = container.settingsRepository.snapshot()
        if (!settings.notifyOnChatCompleted) return
        val viewingCurrentChat = appInForeground && foreground && sessionId == currentSessionId() && _ui.value.destination == AppDestination.Chat
        if (!viewingCurrentChat) container.notifications.notifyCompleted(sessionId, requestId, content)
    }

    private fun noteTaskProgress(tasks: List<TaskSummary>) {
        val settings = container.settingsRepository.snapshot()
        val seen = tasks.map { it.id }.toSet()
        tasks.forEach { task ->
            val fingerprint = listOf(task.status, task.progress, task.runningChildren, task.completedChildren, task.failedChildren, task.lastActivityAt, task.error).joinToString("|")
            val previous = taskFingerprints.put(task.id, fingerprint)
            if (!progressWatchReady || previous == null || previous == fingerprint) return@forEach
            val subagent = !task.parentId.isNullOrBlank() || task.source.equals("tool", true) || task.mode in setOf("single", "parallel", "pipeline", "debate", "auto")
            val kind = if (subagent) RuntimeNoticeKind.Subagent else RuntimeNoticeKind.Background
            val enabled = if (subagent) settings.notifyOnSubagent else settings.notifyOnBackground
            val viewing = appInForeground && (
                (subagent && _ui.value.destination == AppDestination.Tasks && _ui.value.selectedTaskId == task.id) ||
                    (!subagent && _ui.value.destination == AppDestination.Background)
                )
            if (!enabled || viewing) return@forEach
            val terminal = task.status.isTerminalTaskStatus()
            val title = "LuckyAgent · ${if (subagent) "子代理" else "任务"} · ${task.description.ifBlank { task.id }}"
            val body = buildString {
                append(task.status)
                if (task.progress > 0) append(" · ${(task.progress * 100).toInt().coerceIn(0, 100)}%")
                if (task.childCount > 0) append(" · ${task.completedChildren}/${task.childCount}")
                task.error?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.take(80)) }
            }
            container.notifications.notifyTask(kind, task.id, title, body, task.metadata["session_id"].orEmpty(), terminal)
        }
        taskFingerprints.keys.retainAll(seen)
        if (tasks.isNotEmpty() || progressWatchReady) progressWatchReady = true
    }

    private fun noteBackgroundProgress(tasks: List<AutonomyTaskSummary>) {
        val settings = container.settingsRepository.snapshot()
        val seen = tasks.map { it.id }.toSet()
        val viewing = appInForeground && _ui.value.destination == AppDestination.Background
        tasks.forEach { task ->
            val fingerprint = listOf(task.state, task.attempts, task.continuations, task.lastActivityAt, task.error, task.resultPreview, task.verified).joinToString("|")
            val previous = backgroundFingerprints.put(task.id, fingerprint)
            if (!progressWatchReady || previous == null || previous == fingerprint || !settings.notifyOnBackground || viewing) return@forEach
            val terminal = task.state.isTerminalTaskStatus() || task.state.equals("done", true) || task.state.equals("succeeded", true)
            val title = "LuckyAgent · 后台任务 · ${task.title.ifBlank { task.id }}"
            val body = listOfNotNull(task.state.ifBlank { null }, task.resultPreview?.take(80), task.error?.take(80)).joinToString(" · ")
            container.notifications.notifyTask(RuntimeNoticeKind.Background, task.id, title, body, task.sessionId.orEmpty(), terminal)
        }
        backgroundFingerprints.keys.retainAll(seen)
    }

    private fun noteCronProgress(jobs: List<CronJob>) {
        val settings = container.settingsRepository.snapshot()
        val seen = jobs.map { it.id }.toSet()
        jobs.forEach { job ->
            val fingerprint = listOf(job.lastRun, job.runCount, job.errorCount, job.status, job.lastError).joinToString("|")
            val previous = cronFingerprints.put(job.id, fingerprint)
            if (!progressWatchReady || previous == null || previous == fingerprint || !settings.notifyOnCron) return@forEach
            val sessionId = job.metadata["session_id"].orEmpty()
            val viewing = appInForeground && _ui.value.destination == AppDestination.Chat && sessionId.isNotBlank() && sessionId == currentSessionId()
            if (viewing) return@forEach
            val body = job.lastError?.takeIf { it.isNotBlank() } ?: "已运行 ${job.runCount} 次"
            container.notifications.notifyCron(sessionId, "${job.id}:${job.lastRun}:${job.runCount}", "${job.name.ifBlank { job.id }} · $body")
        }
        cronFingerprints.keys.retainAll(seen)
    }

    private fun notifyCronMessage(sessionId: String, eventId: String?, content: String) {
        val settings = container.settingsRepository.snapshot()
        if (!settings.notifyOnCron) return
        val viewing = appInForeground && sessionId == currentSessionId() && _ui.value.destination == AppDestination.Chat
        if (!viewing) container.notifications.notifyCron(sessionId, eventId, content)
    }

    private fun handleTaskEvent(data: kotlinx.serialization.json.JsonElement?) {
        val taskId = extractField(data, "task_id").orEmpty()
        if (taskId.isBlank()) return
        val type = extractField(data, "type").orEmpty()
        val status = extractField(data, "status").orEmpty()
        val message = extractField(data, "message").orEmpty()
        val description = extractField(data, "description").orEmpty()
        val sessionId = extractField(data, "session_id").orEmpty()
        val parentId = extractField(data, "parent_id").orEmpty()
        val progress = extractField(data, "progress")?.toDoubleOrNull()
        val terminal = status.isTerminalTaskStatus() || type.endsWith("completed") || type.endsWith("failed") || type.endsWith("cancelled")
        val subagent = parentId.isNotBlank() || type.startsWith("task.child") || extractField(data, "mode").orEmpty() in setOf("single", "parallel", "pipeline", "debate", "auto")
        if (_ui.value.destination == AppDestination.Tasks || subagent) {
            viewModelScope.launch { refreshTasksNow() }
        }
        if (_ui.value.destination == AppDestination.Background) {
            viewModelScope.launch { refreshBackgroundNow() }
        }
        val settings = container.settingsRepository.snapshot()
        val kind = if (subagent) RuntimeNoticeKind.Subagent else RuntimeNoticeKind.Background
        val enabled = if (subagent) settings.notifyOnSubagent else settings.notifyOnBackground
        val viewing = appInForeground && (
            (subagent && _ui.value.destination == AppDestination.Tasks) ||
                (!subagent && _ui.value.destination == AppDestination.Background)
            )
        if (!enabled || viewing) {
            if (terminal) container.notifications.cancelTask(kind, taskId)
            return
        }
        val titleName = description.ifBlank { taskId }
        val title = "LuckyAgent · ${if (subagent) "子代理" else "后台任务"} · $titleName"
        val body = buildString {
            append(status.ifBlank { type.ifBlank { "progress" } })
            progress?.let { append(" · ${(it * 100).toInt().coerceIn(0, 100)}%") }
            if (message.isNotBlank()) append(" · ").append(message.take(80))
        }
        container.notifications.notifyTask(kind, taskId, title, body, sessionId, terminal)
    }

    private fun ensureReplayedRun(event: WsEvent) {
        val requestId = event.envelope.parentId ?: return
        if (event.envelope.runId.isNullOrBlank()) return
        if (activeRuns[event.sessionId]?.containsKey(requestId) == true) return
        registerRun(container.wsClient.replayHandle(event.sessionId, requestId, event.connectionId))
    }

    private fun prepareNextForeground(sessionId: String) {
        if (sessionId != currentSessionId() || activeRuns[sessionId].isNullOrEmpty()) return
        resetAssistantStream()
        currentTurnId = "turn-${System.currentTimeMillis()}"
        _ui.update { it.copy(isResponding = true, activityLine = "Next message queued · continuing") }
    }

    private fun handleToolCall(data: kotlinx.serialization.json.JsonElement?) {
        val name = extractToolName(data) ?: "tool"
        val stepId = extractField(data, "step_id").orEmpty()
        val args = extractToolArgs(data)?.let { capUiText(it, MAX_UI_TOOL_ARGS_CHARS) }
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
        val output = (extractField(data, "output")
            ?: extractField(data, "display")
            ?: extractText(data)
            ?: "").let { capUiText(it, MAX_UI_TOOL_OUTPUT_CHARS) }
        val success = when (val raw = (data as? JsonObject)?.get("success")) {
            is JsonPrimitive -> when {
                raw.isString -> raw.contentOrNull?.toBooleanStrictOrNull() ?: true
                else -> runCatching { raw.content.toBooleanStrict() }.getOrElse {
                    raw.contentOrNull?.toBooleanStrictOrNull() ?: true
                }
            }
            else -> true
        }
        val attachments = distinctMedia(extractAttachments(data) + extractArtifactAttachments(output))
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
            attachments = attachments,
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



    private fun handleCompactEvent(data: kotlinx.serialization.json.JsonElement?) {
        if (data == null) return
        val phase = extractField(data, "phase")?.lowercase().orEmpty()
        val message = extractField(data, "message")
            ?.takeIf { it.isNotBlank() }
            ?: when (phase) {
                "start" -> "Compressing conversation context…"
                "progress" -> "Compressing conversation context…"
                "done" -> "Context compressed"
                "degraded" -> "Context compressed (local fallback)"
                "failed" -> "Context compression failed"
                else -> "Compressing context…"
            }
        val pre = extractField(data, "pre_token_estimate")?.toIntOrNull()
        val post = extractField(data, "post_token_estimate")?.toIntOrNull()
        val detail = buildString {
            append(message)
            if (pre != null && post != null && pre > 0) {
                append(" · ")
                append(TokenFormat.compact(pre))
                append("→")
                append(TokenFormat.compact(post))
            }
        }
        when (phase) {
            "done", "degraded" -> {
                upsertProgress(ChatProgressStep("compact", detail, ProgressStatus.Complete))
                _ui.update { it.copy(activityLine = detail) }
            }
            "failed" -> {
                upsertProgress(ChatProgressStep("compact", detail, ProgressStatus.Failed))
                _ui.update { it.copy(activityLine = detail) }
            }
            else -> {
                upsertProgress(ChatProgressStep("compact", detail, ProgressStatus.Active))
                _ui.update { it.copy(isResponding = true, activityLine = detail) }
            }
        }
    }

    fun compactCurrentSession(forceLocal: Boolean = false) {
        compactSession(currentSessionId(), forceLocal = forceLocal)
    }

    fun compactSession(sessionId: String, forceLocal: Boolean = false) {
        val id = sessionId.trim().ifBlank { currentSessionId() }
        val title = _ui.value.sessions.firstOrNull { it.id == id }?.title?.takeIf { it.isNotBlank() } ?: id
        viewModelScope.launch {
            upsertProgress(ChatProgressStep("compact", "正在压缩「$title」", ProgressStatus.Active))
            if (id == currentSessionId()) {
                pushBubble(ChatBubble(id = "compact-$id", role = "compact", content = "正在压缩", streaming = true))
            }
            _ui.update { it.copy(activityLine = "正在压缩「$title」") }
            val result = container.api.compactSession(id, forceLocal = forceLocal)
            result.fold(
                onSuccess = { body ->
                    val line = compactResultLine(title, body)
                    upsertProgress(ChatProgressStep("compact", line, ProgressStatus.Complete))
                    replaceCompactBubble(id, line, streaming = false)
                    _ui.update { it.copy(activityLine = line, isResponding = false) }
                    if (id == currentSessionId()) {
                        loadHistory(id, reset = true)
                        refreshContextInspect(id)
                    }
                    refreshSessions()
                },
                onFailure = { err ->
                    val line = "压缩失败 · ${err.message ?: "请求失败"}"
                    upsertProgress(ChatProgressStep("compact", line, ProgressStatus.Failed))
                    replaceCompactBubble(id, line, streaming = false)
                    _ui.update { it.copy(activityLine = line, isResponding = false) }
                },
            )
        }
    }

    private fun replaceCompactBubble(sessionId: String, label: String, streaming: Boolean) {
        if (sessionId != currentSessionId()) return
        _ui.update { current ->
            val next = current.bubbles.toMutableList()
            val index = next.indexOfLast { it.role == "compact" && it.id == "compact-$sessionId" }
            val bubble = ChatBubble(id = "compact-$sessionId", role = "compact", content = label, streaming = streaming)
            if (index >= 0) next[index] = bubble else next += bubble
            current.copy(bubbles = next)
        }
    }

    private fun compactResultLine(title: String, body: com.luckyagent.android.data.api.CompactSessionResult): String {
        val pre = body.preTokenEstimate
        val post = body.postTokenEstimate
        val dropped = body.droppedMessages
        val source = body.summarySource?.takeIf { it.isNotBlank() }
        val parts = buildList {
            if (pre != null && post != null) add("${TokenFormat.compact(pre)} → ${TokenFormat.compact(post)}")
            if (dropped != null) add("去掉 $dropped 条")
            if (source != null) add(source)
        }
        val detail = body.display?.message?.takeIf { it.isNotBlank() }
            ?: parts.joinToString(" · ").ifBlank { body.display?.subtitle }
        return if (detail.isNullOrBlank()) "已压缩「$title」" else "已压缩「$title」 · $detail"
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
        attachments: List<ChatMedia> = emptyList(),
    ) {
        val safeArgs = args?.let { capUiText(it, MAX_UI_TOOL_ARGS_CHARS) }
        val safeOutput = output?.let { capUiText(it, MAX_UI_TOOL_OUTPUT_CHARS) }
        val safeAttachments = attachments.take(MAX_UI_ATTACHMENTS)
        _ui.update { st ->
            val list = st.bubbles.toMutableList()
            val idx = list.indexOfLast { it.id == id }
            if (idx >= 0) {
                val old = list[idx]
                list[idx] = old.copy(
                    role = "tool",
                    toolName = name,
                    toolArgs = safeArgs?.takeIf { it.isNotBlank() } ?: old.toolArgs,
                    toolOutput = safeOutput?.takeIf { it.isNotBlank() } ?: old.toolOutput,
                    toolDone = done || old.toolDone,
                    toolSuccess = success ?: old.toolSuccess,
                    attachments = if (safeAttachments.isNotEmpty()) safeAttachments else old.attachments,
                    content = buildToolContent(
                        name = name,
                        args = safeArgs?.takeIf { it.isNotBlank() } ?: old.toolArgs,
                        output = safeOutput?.takeIf { it.isNotBlank() } ?: old.toolOutput,
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
                        content = buildToolContent(name, safeArgs, safeOutput, done, success),
                        toolName = name,
                        toolArgs = safeArgs,
                        toolOutput = safeOutput,
                        toolDone = done,
                        toolSuccess = success,
                        attachments = safeAttachments,
                        stepId = id.removePrefix("tool-").takeIf { it != id },
                    ),
                )
            }
            st.copy(bubbles = list)
        }
    }

    private fun upsertReasoningBubble(round: Int?, text: String, hasContent: Boolean) {
        val id = "reasoning-$currentTurnId-${round ?: 0}"
        val safeText = capUiText(text, MAX_UI_REASONING_CHARS)
        _ui.update { state ->
            val bubbles = state.bubbles.toMutableList()
            val index = bubbles.indexOfLast { it.id == id }
            if (index >= 0) {
                val old = bubbles[index]
                if (!old.reasoningHasContent || hasContent) {
                    bubbles[index] = old.copy(content = safeText, reasoningHasContent = hasContent)
                }
            } else {
                bubbles.insertBeforeStreamingAnswer(
                    ChatBubble(
                        id = id,
                        role = "reasoning",
                        content = safeText,
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
            is JsonPrimitive -> data.textOrNull()
            is JsonObject -> {
                sequenceOf("content", "text", "delta", "message", "response", "full_response")
                    .mapNotNull { key -> data[key].textOrNull() }
                    .firstOrNull()
            }
            else -> data.toString()
        }
    }

    private fun extractFullResponse(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        return o["full_response"].textOrNull()
    }

    private fun extractField(data: kotlinx.serialization.json.JsonElement?, key: String): String? {
        val o = data as? JsonObject ?: return null
        return o[key].textOrNull()
    }

    private fun extractAttachments(data: kotlinx.serialization.json.JsonElement?): List<ChatMedia> {
        val obj = data as? JsonObject ?: return emptyList()
        val raw = obj["attachments"] ?: obj["files"] ?: obj["media"] ?: return emptyList()
        val items = raw as? JsonArray ?: return emptyList()
        return items.mapNotNull { item ->
            val attachment = item as? JsonObject ?: return@mapNotNull null
            val descriptor = MediaAttachment(
                type = attachment.stringValue("type")?.let(::normalizeMediaType) ?: "document",
                fileId = attachment.stringValue("file_id"),
                fileUrl = attachment.stringValue("file_url")
                    ?: attachment.stringValue("url")
                    ?: attachment.stringValue("download_url"),
                filePath = attachment.stringValue("file_path"),
                fileName = attachment.stringValue("file_name") ?: attachment.stringValue("name"),
                mimeType = attachment.stringValue("mime_type") ?: attachment.stringValue("content_type"),
                fileSize = attachment.longValue("file_size") ?: attachment.longValue("size"),
            )
            if (descriptor.fileUrl.isNullOrBlank() && descriptor.filePath.isNullOrBlank()) null
            else ChatMedia(descriptor)
        }
    }

    private fun extractArtifactAttachments(text: String): List<ChatMedia> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<ChatMedia>()
        artifactPathPattern.findAll(text).forEach { match ->
            artifactDescriptor(cleanArtifactPath(match.value))?.let { result += ChatMedia(it) }
        }
        return distinctMedia(result)
    }

    private fun artifactDescriptor(path: String): MediaAttachment? {
        val normalized = path.replace('\\', '/')
        val roots = listOf(
            "~/.luckyagent/workspace/" to "workspace/",
            "~/.luckyagent/uploads/" to "uploads/",
        )
        val configuredRoot = roots.firstOrNull { normalized.startsWith(it.first) }
        val relative = if (configuredRoot != null) {
            configuredRoot.second + normalized.removePrefix(configuredRoot.first)
        } else {
            val workspaceMarker = "/.luckyagent/workspace/"
            val uploadsMarker = "/.luckyagent/uploads/"
            when {
                normalized.contains(workspaceMarker) -> "workspace/" + normalized.substringAfter(workspaceMarker)
                normalized.contains(uploadsMarker) -> "uploads/" + normalized.substringAfter(uploadsMarker)
                else -> return null
            }
        }
        val fileName = relative.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            fileName.substringAfterLast('.', "").lowercase(),
        ) ?: "application/octet-stream"
        val base = container.settingsRepository.snapshot().apiBase.trimEnd('/')
        if (base.isBlank()) return null
        val encodedPath = URLEncoder.encode(relative, Charsets.UTF_8.name())
        return MediaAttachment(
            type = normalizeMediaType(mimeType),
            fileUrl = "$base/api/v1/artifacts?path=$encodedPath",
            fileName = fileName,
            mimeType = mimeType,
        )
    }

    private fun distinctMedia(items: List<ChatMedia>): List<ChatMedia> {
        val seen = mutableSetOf<String>()
        return items.filter { media ->
            val key = mediaKey(media).ifBlank { media.localUri.orEmpty() }
            if (key.isBlank()) return@filter false
            seen.add(key)
        }
    }

    private fun cleanArtifactPath(value: String): String =
        value.trim().trim('`', '"', '\'', ',', '.', ';', ':', ')', ']', '}')

    private fun JsonObject.stringValue(key: String): String? =
        this[key].textOrNull()?.takeIf { it.isNotBlank() }

    private fun JsonObject.longValue(key: String): Long? =
        this[key].longOrNullSafe()

    private fun normalizeMediaType(type: String): String = when {
        type.equals("image", ignoreCase = true) || type.startsWith("image/", ignoreCase = true) -> "image"
        type.equals("audio", ignoreCase = true) || type.startsWith("audio/", ignoreCase = true) -> "audio"
        type.equals("video", ignoreCase = true) || type.startsWith("video/", ignoreCase = true) -> "video"
        else -> "document"
    }

    private fun extractUsage(data: kotlinx.serialization.json.JsonElement?): TokenUsage? {
        val o = data as? JsonObject ?: return null
        val usage = o["usage"] as? JsonObject ?: return null
        fun int(key: String): Int = usage[key].intOrNullSafe() ?: 0
        val result = TokenUsage(
            inputTokens = int("input_tokens"),
            outputTokens = int("output_tokens"),
            totalTokens = int("total_tokens"),
            cachedInputTokens = int("cached_input_tokens"),
            model = usage["model"].textOrNull(),
        )
        return result.takeIf {
            it.totalTokens > 0 || it.inputTokens > 0 || it.outputTokens > 0 || it.cachedInputTokens > 0 || !it.model.isNullOrBlank()
        }
    }

    private fun nowIsoTimestamp(): String = java.time.Instant.now().toString()

    private fun extractToolName(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        return o["name"].textOrNull()
            ?: o["tool"].textOrNull()
    }

    private fun extractToolArgs(data: kotlinx.serialization.json.JsonElement?): String? {
        val o = data as? JsonObject ?: return null
        o["args"].textOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        o["display"].textOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
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
        // Retain one extra character so the flush can show a truncation marker.
        val remaining = (MAX_UI_MESSAGE_CHARS + 1 - assistantPending.length).coerceAtLeast(0)
        if (remaining == 0) return
        assistantPending.append(piece.take(remaining))
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
            val cachedIndex = assistantBufferIndex
                ?.takeIf { it in list.indices && list[it].id == id }
            val idx = cachedIndex ?: list.indexOfLast { it.id == id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    content = appendUiText(list[idx].content, piece, MAX_UI_MESSAGE_CHARS),
                    streaming = true,
                )
            } else {
                list += ChatBubble(id = id, role = "assistant", content = capUiText(piece, MAX_UI_MESSAGE_CHARS), streaming = true)
            }
            assistantBufferIndex = if (idx >= 0) idx else list.lastIndex
            st.copy(bubbles = list, isResponding = true)
        }
    }

    private fun finishAssistant(
        full: String?,
        createdAt: String? = null,
        usage: TokenUsage? = null,
        attachments: List<ChatMedia> = emptyList(),
    ) {
        val safeFull = full?.let { capUiText(it, MAX_UI_MESSAGE_CHARS) }
        val safeAttachments = attachments.take(MAX_UI_ATTACHMENTS)
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        val pending = assistantPending.toString()
        assistantPending.setLength(0)
        val id = assistantBufferId
        _ui.update { st ->
            val list = st.bubbles.toMutableList()
            val lastAssistantIndex = list.indexOfLast { bubble ->
                bubble.role == "assistant" && !bubble.streaming
            }
            val hasUserAfterLastAssistant = lastAssistantIndex >= 0 &&
                list.subList(lastAssistantIndex + 1, list.size).any { bubble -> bubble.role == "user" }
            val duplicateHistoryAnswer = id == null &&
                !hasUserAfterLastAssistant &&
                lastAssistantIndex >= 0 &&
                (safeFull.isNullOrBlank() || list[lastAssistantIndex].content == safeFull) &&
                (safeAttachments.isEmpty() || list[lastAssistantIndex].attachments.map(::mediaKey) == safeAttachments.map(::mediaKey))
            if (duplicateHistoryAnswer) {
                return@update st.copy(isResponding = false)
            }
            if (id != null) {
                val idx = list.indexOfLast { it.id == id }
                if (idx >= 0) {
                    val content = safeFull?.takeIf { it.isNotBlank() }
                        ?: appendUiText(list[idx].content, pending, MAX_UI_MESSAGE_CHARS)
                    val answer = list.removeAt(idx)
                    list += answer.copy(
                        content = content,
                        streaming = false,
                        createdAt = createdAt ?: answer.createdAt,
                        usage = usage ?: answer.usage,
                        attachments = if (safeAttachments.isNotEmpty()) safeAttachments else answer.attachments,
                    )
                } else if (!full.isNullOrBlank() || pending.isNotEmpty() || attachments.isNotEmpty()) {
                    list += ChatBubble(
                        id = id,
                        role = "assistant",
                        content = safeFull?.takeIf { it.isNotBlank() } ?: capUiText(pending, MAX_UI_MESSAGE_CHARS),
                        streaming = false,
                        createdAt = createdAt,
                        usage = usage,
                        attachments = safeAttachments,
                    )
                }
            } else if (!full.isNullOrBlank() || attachments.isNotEmpty()) {
                list += ChatBubble(
                    id = "a-${System.currentTimeMillis()}",
                    role = "assistant",
                    content = safeFull.orEmpty(),
                    createdAt = createdAt,
                    usage = usage,
                    attachments = safeAttachments,
                )
            }
            st.copy(bubbles = list, isResponding = false)
        }
        assistantBufferId = null
        assistantBufferIndex = null
    }

    private fun mediaKey(media: ChatMedia): String {
        val descriptor = media.descriptor
        return listOf(descriptor.fileUrl, descriptor.filePath, descriptor.fileId, descriptor.fileName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .map(::canonicalMediaKey)
            .firstOrNull()
            .orEmpty()
    }

    private fun canonicalMediaKey(value: String): String {
        val normalized = value.replace('\\', '/')
        val uri = runCatching { Uri.parse(normalized) }.getOrNull()
        uri?.getQueryParameter("path")?.takeIf { it.isNotBlank() }?.let {
            return "artifact:${it.trimStart('/')}"
        }
        listOf("/.luckyagent/workspace/", "/.luckyagent/uploads/").forEach { marker ->
            if (normalized.contains(marker)) {
                return "artifact:${normalized.substringAfter(marker)}".let {
                    if (marker.contains("uploads")) it.replaceFirst("artifact:", "artifact:uploads/")
                    else it.replaceFirst("artifact:", "artifact:workspace/")
                }
            }
        }
        return normalized
    }

    private fun resetAssistantStream() {
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        assistantPending.setLength(0)
        assistantBufferId = null
        assistantBufferIndex = null
    }

    private fun pushBubble(bubble: ChatBubble) {
        val safeBubble = bubble.copy(
            content = capUiText(bubble.content, MAX_UI_MESSAGE_CHARS),
            attachments = bubble.attachments.take(MAX_UI_ATTACHMENTS),
        )
        _ui.update { it.copy(bubbles = it.bubbles + safeBubble) }
    }

    fun navigate(dest: AppDestination) {
        val current = _ui.value.destination
        if (current != dest) {
            backStack.addLast(current)
            while (backStack.size > 12) backStack.removeFirst()
        }
        _ui.update { it.copy(destination = dest) }
        ensureRuntimeWatch()
        when (dest) {
            AppDestination.Tasks -> {
                startTaskPolling()
            }
            AppDestination.Background -> {
                startBackgroundPolling()
            }
            AppDestination.Cron -> refreshCron()
            AppDestination.Memory -> refreshMemory()
            AppDestination.Skills -> refreshSkills()
            AppDestination.Gateways -> refreshGateways()
            AppDestination.Trajectory -> refreshTrajectory()
            AppDestination.Chat -> Unit
            AppDestination.Settings -> Unit
        }
    }

    /** Closes a detail page, then walks back to the previous screen. */
    fun handleSystemBack(): Boolean {
        if (_ui.value.selectedTaskId != null) {
            clearSelectedTask()
            return true
        }
        if (_ui.value.selectedBackgroundTaskId != null) {
            clearSelectedBackgroundTask()
            return true
        }
        val previous = if (backStack.isEmpty()) null else backStack.removeLast()
        if (previous == null) return false
        _ui.update { it.copy(destination = previous) }
        ensureRuntimeWatch()
        return true
    }

    fun setAppForeground(value: Boolean) {
        appInForeground = value
        if (value) ensureRuntimeWatch() else stopRuntimeWatch()
    }

    fun checkForUpdates() {
        _ui.update { it.copy(update = it.update.copy(phase = UpdatePhase.Checking, error = null)) }
        viewModelScope.launch {
            container.updates.checkLatest(force = true).onSuccess { available ->
                _ui.update { state ->
                    state.copy(update = state.update.copy(
                        phase = if (available == null) UpdatePhase.UpToDate else UpdatePhase.Available,
                        latest = available,
                        downloadedPath = null,
                        downloadProgress = null,
                        installBlockReason = null,
                        error = null,
                    ))
                }
            }.onFailure { error ->
                _ui.update { it.copy(update = it.update.copy(phase = UpdatePhase.Error, error = error.message ?: "检查更新失败")) }
            }
        }
    }

    fun downloadUpdate() {
        val available = _ui.value.update.latest ?: return
        _ui.update { it.copy(update = it.update.copy(phase = UpdatePhase.Downloading, error = null)) }
        updateDownloadJob?.cancel()
        updateDownloadJob = viewModelScope.launch {
            container.updates.download(available) { progress ->
                _ui.update { it.copy(update = it.update.copy(downloadProgress = progress)) }
            }.onSuccess { file ->
                val blockReason = container.updates.installBlockReason(file).fold(
                    onSuccess = { it },
                    onFailure = { error -> error.message ?: "Unable to verify downloaded APK" },
                )
                _ui.update { it.copy(update = it.update.copy(phase = UpdatePhase.ReadyToInstall, downloadedPath = file.absolutePath, installBlockReason = blockReason, error = null)) }
            }.onFailure { error ->
                _ui.update { it.copy(update = it.update.copy(phase = UpdatePhase.Error, error = error.message ?: "下载更新失败")) }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _ui.update { it.copy(update = it.update.copy(phase = if (it.update.latest == null) UpdatePhase.Idle else UpdatePhase.Available, downloadProgress = null)) }
    }

    fun installUpdate() {
        val path = _ui.value.update.downloadedPath ?: return
        val update = _ui.value.update
        if (update.installBlockReason != null) {
            _ui.update { it.copy(update = it.update.copy(phase = UpdatePhase.Error, error = update.installBlockReason)) }
            return
        }
        container.updates.install(File(path)).onFailure { error ->
            _ui.update { it.copy(update = it.update.copy(error = error.message ?: "无法打开安装器")) }
        }
    }

    fun openReleasePage() {
        container.updates.openReleasePage(_ui.value.update.latest)
    }

    fun openSessionFromNotification(sessionId: String) {
        navigate(AppDestination.Chat)
        selectSession(sessionId)
    }

    fun openDestinationFromNotification(destination: String) {
        when (destination) {
            "tasks" -> navigate(AppDestination.Tasks)
            "background" -> navigate(AppDestination.Background)
            "cron" -> navigate(AppDestination.Cron)
            "chat" -> navigate(AppDestination.Chat)
        }
    }

    fun quoteMessage(bubble: ChatBubble) {
        val quote = quoteFromBubble(
            id = bubble.id,
            role = bubble.role,
            content = bubble.content,
            toolName = bubble.toolName,
            toolArgs = bubble.toolArgs,
            toolOutput = bubble.toolOutput,
        ) ?: return
        _ui.update { it.copy(pendingQuote = quote, snackbarMessage = null) }
    }

    fun clearQuote() {
        _ui.update { it.copy(pendingQuote = null) }
    }

    fun copyBubbleText(bubble: ChatBubble): String =
        bubbleCopyText(
            role = bubble.role,
            content = bubble.content,
            toolName = bubble.toolName,
            toolArgs = bubble.toolArgs,
            toolOutput = bubble.toolOutput,
        )

    fun notifyCopied() {
        _ui.update { it.copy(snackbarMessage = "已复制") }
    }

    fun consumeSnackbar() {
        _ui.update { it.copy(snackbarMessage = null) }
    }

    fun loadModels(force: Boolean = false) {
        val current = _ui.value
        if (current.modelsLoading || (!force && current.modelsLoaded)) return
        _ui.update { it.copy(modelsLoading = true, modelsError = null) }
        viewModelScope.launch {
            // Opening the sheet and tapping retry both ask the server to query
            // the current chat key. A plain list is the local catalog.
            container.api.listModels(refresh = true).onSuccess { payload ->
                val models = payload.models
                val active = models
                    .filter { it.current }
                    .associateBy { it.kind.trim().lowercase() }
                _ui.update {
                    it.copy(
                        models = models,
                        activeModels = active,
                        modelsLoaded = true,
                        modelsLoading = false,
                        modelsError = null,
                    )
                }
            }.onFailure { error ->
                _ui.update {
                    it.copy(
                        modelsLoaded = true,
                        modelsLoading = false,
                        modelsError = error.message ?: "加载模型列表失败",
                    )
                }
            }
        }
    }

    fun switchModel(model: ModelRef) {
        val kind = model.kind.trim().lowercase()
        val key = "$kind|${model.id}"
        if (kind.isBlank() || model.id.isBlank() || _ui.value.modelSwitchingKey != null) return
        if (_ui.value.activeModels[kind]?.id == model.id) return
        _ui.update { it.copy(modelSwitchingKey = key, modelsError = null) }
        viewModelScope.launch {
            container.api.switchModel(kind = kind, model = model.id, provider = model.provider)
                .onSuccess {
                    _ui.update { state ->
                        val nextModels = state.models.map { item ->
                            when {
                                item.kind.trim().lowercase() != kind -> item
                                item.id == model.id -> item.copy(current = true)
                                else -> item.copy(current = false)
                            }
                        }
                        state.copy(
                            models = nextModels,
                            activeModels = state.activeModels + (kind to model.copy(current = true)),
                            modelSwitchingKey = null,
                            snackbarMessage = "${modelKindLabel(kind)}模型已切换：${model.displayName ?: model.id}",
                        )
                    }
                    // Re-read server state so persisted selections and provider metadata stay authoritative.
                    loadModels(force = true)
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            modelSwitchingKey = null,
                            snackbarMessage = "切换${modelKindLabel(kind)}模型失败：${error.message ?: "请求失败"}",
                        )
                    }
                }
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
            val mime = resolver.getType(uri)
                ?: name.substringAfterLast('.', missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() && it != name }
                    ?.lowercase()
                    ?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }
                ?: "application/octet-stream"
            val item = PendingMedia(id, uri.toString(), name, mime)
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

    fun retryPendingMedia(resolver: ContentResolver, id: String) {
        val item = _ui.value.pendingMedia.firstOrNull { it.id == id } ?: return
        _ui.update { state ->
            state.copy(pendingMedia = state.pendingMedia.map {
                if (it.id == id) it.copy(descriptor = null, error = null) else it
            })
        }
        viewModelScope.launch {
            mediaUploadSemaphore.withPermit {
                container.api.uploadAttachment(resolver, Uri.parse(item.uri), item.fileName)
            }.onSuccess { descriptor ->
                _ui.update { state -> state.copy(pendingMedia = state.pendingMedia.map { if (it.id == id) it.copy(descriptor = descriptor) else it }) }
            }.onFailure { error ->
                _ui.update { state -> state.copy(pendingMedia = state.pendingMedia.map { if (it.id == id) it.copy(error = error.message ?: "上传失败") else it }) }
            }
        }
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

    fun reportPairingScanFailed(message: String = "这张图里没有二维码") {
        _ui.update { it.copy(activityLine = message) }
    }

    fun applyPairingQr(raw: String) {
        val endpoint = try {
            com.luckyagent.android.data.settings.PairingQr.parse(raw).toEndpoint()
        } catch (error: Exception) {
            _ui.update { it.copy(activityLine = error.message ?: "这不是 LuckyAgent 配对码") }
            return
        }
        updateSettings { settings ->
            val endpoints = settings.runtimeEndpoints + endpoint
            settings.copy(runtimeEndpoints = endpoints, activeRuntimeEndpointId = endpoint.id)
        }
        refreshSessions()
        _ui.update { it.copy(activityLine = "已写入 ${endpoint.apiBase}，临时 key 在 API 重启后失效") }
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

    fun setChatBackground(uri: android.net.Uri?) {
        importAppearance(
            uri = uri,
            fileName = com.luckyagent.android.data.settings.AppearanceStore.BACKGROUND_FILE,
            maxEdge = 1600,
            apply = { settings, savedName -> settings.copy(chatBackgroundFile = savedName) },
            cleared = "已恢复默认聊天背景",
            saved = "聊天背景已更换，回到对话页就能看到",
            failed = "这张图片没能换成背景，换一张 JPG 或 PNG 再试",
        )
    }

    fun setChatBackgroundDim(dim: Int) {
        updateSettings { it.copy(chatBackgroundDim = dim.coerceIn(0, 70)) }
    }

    fun setAvatar(uri: android.net.Uri?) {
        importAppearance(
            uri = uri,
            fileName = com.luckyagent.android.data.settings.AppearanceStore.AVATAR_FILE,
            maxEdge = 512,
            apply = { settings, savedName -> settings.copy(avatarFile = savedName) },
            cleared = "已恢复默认头像",
            saved = "头像已更换，对话页顶栏和助手消息旁会显示",
            failed = "这张图片没能换成头像，换一张 JPG 或 PNG 再试",
        )
    }

    private fun importAppearance(
        uri: android.net.Uri?,
        fileName: String,
        maxEdge: Int,
        apply: (com.luckyagent.android.data.settings.ClientSettings, String) -> com.luckyagent.android.data.settings.ClientSettings,
        cleared: String,
        saved: String,
        failed: String,
    ) {
        if (_ui.value.appearanceBusy) return
        val context = container.appContext
        if (uri == null) {
            com.luckyagent.android.data.settings.AppearanceStore.clear(context, fileName)
            updateSettings { apply(it, "") }
            _ui.update { it.copy(snackbarMessage = cleared) }
            return
        }
        _ui.update { it.copy(appearanceBusy = true, snackbarMessage = "正在处理图片…") }
        viewModelScope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.luckyagent.android.data.settings.AppearanceStore.importImage(context, uri, fileName, maxEdge)
            }
            if (ok) {
            updateSettings { current ->
                val applied = apply(current, fileName)
                if (fileName == com.luckyagent.android.data.settings.AppearanceStore.BACKGROUND_FILE &&
                    current.chatBackgroundFile.isBlank()
                ) {
                    applied.copy(chatBackgroundDim = 12)
                } else {
                    applied
                }
            }
        }
            _ui.update { it.copy(appearanceBusy = false, snackbarMessage = if (ok) saved else failed) }
        }
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
        if (prev.sessionId != next.sessionId) {
            _ui.update {
                it.copy(
                    luckyActive = false,
                    luckySegments = 0,
                    luckyAttachments = 0,
                    luckyPending = luckyPending.values.filter { segment -> segment.sessionId == next.sessionId },
                )
            }
        }
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

    private fun refreshPersona() {
        viewModelScope.launch {
            container.api.soul().onSuccess { info ->
                val name = info.name.trim().ifBlank { personaNameFromPrompt(info.systemPrompt) }
                if (name.isNotBlank()) _ui.update { it.copy(personaName = name) }
            }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            val connection = container.settingsRepository.snapshot()
            val apiBase = connection.apiBase
            val apiKey = connection.apiKey
            val cached = runCatching { dedupeSessions(container.sessionCache.listSessions(apiBase, apiKey)) }
                .getOrDefault(emptyList())
            if (!currentCacheConnectionMatches(apiBase, apiKey)) return@launch
            _ui.update { state ->
                state.copy(
                    sessionsLoading = true,
                    sessionsError = null,
                    sessions = if (cached.isNotEmpty() && state.sessionQuery.isBlank()) cached else state.sessions,
                )
            }
            val q = _ui.value.sessionQuery
            val result = container.api.listSessions(q)
            if (!currentCacheConnectionMatches(apiBase, apiKey)) return@launch
            if (result.isSuccess) {
                val remote = dedupeSessions(result.getOrDefault(emptyList()))
                if (q.isBlank()) {
                    runCatching { container.sessionCache.saveSessionList(apiBase, remote, apiKey = apiKey) }
                }
                if (!currentCacheConnectionMatches(apiBase, apiKey)) return@launch
                _ui.update {
                    it.copy(
                        sessionsLoading = false,
                        sessions = remote,
                        sessionsError = null,
                        showingOfflineCache = false,
                    )
                }
                if (q.isBlank()) reconcileOpenHistory(apiBase, apiKey, remote)
            } else if (cached.isNotEmpty() && q.isBlank()) {
                if (!currentCacheConnectionMatches(apiBase, apiKey)) return@launch
                _ui.update {
                    it.copy(
                        sessionsLoading = false,
                        sessions = cached,
                        sessionsError = null,
                        showingOfflineCache = true,
                        activityLine = SessionCachePolicy.OFFLINE_LABEL,
                    )
                }
            } else {
                if (!currentCacheConnectionMatches(apiBase, apiKey)) return@launch
                // Keep the previous list so errors don't look like an empty workspace.
                val keepCachedList = q.isBlank() && _ui.value.sessions.isNotEmpty()
                _ui.update {
                    it.copy(
                        sessionsLoading = false,
                        sessionsError = if (keepCachedList) null else result.exceptionOrNull()?.message ?: "Failed to load sessions",
                        showingOfflineCache = keepCachedList,
                        activityLine = if (keepCachedList) SessionCachePolicy.OFFLINE_LABEL else it.activityLine,
                    )
                }
            }
        }
    }

    fun selectSession(id: String) {
        val target = id.ifBlank { "android-main" }
        val connection = container.settingsRepository.snapshot()
        historyJob?.cancel()
        contextInspectJob?.cancel()
        historySessionId = target
        historyMessages = emptyList()
        historyStartIndex = 0
        viewModelScope.launch {
            runCatching { container.sessionCache.touchOpened(connection.apiBase, target, apiKey = connection.apiKey) }
        }
        container.settingsRepository.update { it.copy(sessionId = target) }
        resetAssistantStream()
        toolStepIndex.clear()
        _ui.update {
            it.copy(
                bubbles = emptyList(),
                historyLoading = true,
                historyLoadingMore = false,
                historyHasMore = false,
                isResponding = activeRuns[target]?.isNotEmpty() == true,
                outboundQueue = outboundChatQueue.items(target),
                progressSteps = emptyList(),
                pendingQuote = null,
                contextInspect = null,
                contextInspectError = null,
                contextInspectLoading = true,
                luckyActive = false,
                luckySegments = 0,
                luckyAttachments = 0,
                luckyPending = luckyPending.values.filter { it.sessionId == target },
                activityLine = "loading history…",
            )
        }
        container.wsClient.replaySession(target)
        dispatchPendingSessions()
        loadHistory(target)
        refreshContextInspect()
    }

    fun loadHistory(sessionId: String = currentSessionId(), reset: Boolean = true) {
        val target = sessionId.ifBlank { "android-main" }
        if (target != currentSessionId()) return
        if (!reset && (_ui.value.historyLoading || _ui.value.historyLoadingMore || !_ui.value.historyHasMore)) return

        if (reset) {
            historyJob?.cancel()
            historySessionId = target
        }
        val loadingMore = !reset
        _ui.update {
            it.copy(
                historyLoading = reset && it.bubbles.isEmpty(),
                historyLoadingMore = loadingMore,
                activityLine = if (reset) {
                    if (it.bubbles.isEmpty()) "loading history…" else it.activityLine
                } else {
                    "loading older history…"
                },
            )
        }

        historyJob = viewModelScope.launch {
            val connection = container.settingsRepository.snapshot()
            val apiBase = connection.apiBase
            val apiKey = connection.apiKey
            if (reset) {
                val painted = paintCachedHistory(apiBase, apiKey, target)
                if (!painted && currentSessionId() == target) {
                    historyMessages = emptyList()
                    historyStartIndex = 0
                    _ui.update { it.copy(bubbles = emptyList(), historyLoading = true, activityLine = "loading history…") }
                }
            }
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != target || historySessionId != target) return@launch
            if (reset) {
                syncLatestHistory(apiBase, apiKey, target)
            } else {
                syncOlderHistory(apiBase, apiKey, target)
            }
        }
    }

    fun loadMoreHistory() {
        loadHistory(currentSessionId(), reset = false)
    }

    fun refreshContextInspect(sessionId: String = currentSessionId()) {
        val target = sessionId.trim().ifBlank { currentSessionId() }
        contextInspectJob?.cancel()
        contextInspectJob = viewModelScope.launch {
            _ui.update { it.copy(contextInspectLoading = true, contextInspectError = null) }
            val result = container.api.inspectContext(target)
            if (currentSessionId() != target) return@launch
            _ui.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        contextInspect = result.getOrNull(),
                        contextInspectLoading = false,
                        contextInspectError = null,
                    )
                } else {
                    state.copy(
                        contextInspectLoading = false,
                        contextInspectError = result.exceptionOrNull()?.message ?: "无法读取上下文",
                    )
                }
            }
        }
    }

    private fun restoreCachedSession() {
        viewModelScope.launch {
            val settings = container.settingsRepository.snapshot()
            val target = settings.sessionId.ifBlank { return@launch }
            if (historyMessages.isNotEmpty() || _ui.value.bubbles.isNotEmpty()) return@launch
            paintCachedHistory(settings.apiBase, settings.apiKey, target)
        }
    }

    private suspend fun paintCachedHistory(apiBase: String, apiKey: String, sessionId: String): Boolean {
        val page = runCatching { container.sessionCache.loadPage(apiBase, sessionId, apiKey) }.getOrNull() ?: return false
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return false
        if (historyMessages.isNotEmpty()) return true
        applyHistoryPage(sessionId, page, offline = _ui.value.showingOfflineCache)
        runCatching { container.sessionCache.touchOpened(apiBase, sessionId, apiKey = apiKey) }
        return true
    }

    private suspend fun syncLatestHistory(apiBase: String, apiKey: String, sessionId: String) {
        if (!currentCacheConnectionMatches(apiBase, apiKey)) return
        val sessionsFresh = !_ui.value.sessionsLoading && _ui.value.sessionsError == null && !_ui.value.showingOfflineCache
        val remoteSession = _ui.value.sessions.firstOrNull { it.id == sessionId }
        val cachedMeta = runCatching { container.sessionCache.sessionMeta(apiBase, sessionId, apiKey) }.getOrNull()
        if (
            sessionsFresh &&
            remoteSession != null &&
            historySyncAction(cachedMeta, remoteSession) == HistorySync.Unchanged &&
            historyMessages.isNotEmpty()
        ) {
            publishHistory(sessionId, offline = false, activity = historyLoadedLine(sessionId))
            return
        }
        val result = container.api.sessionHistory(sessionId, limit = SessionCachePolicy.LATEST_PAGE, offset = 0)
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return
        result.onSuccess { history ->
            val remoteCount = history.messageCount ?: (historyMessages.size + history.messages.size)
            val cachedPage = currentHistoryPage().takeIf { historyMessages.isNotEmpty() }
            when (val merged = mergeLatestPage(cachedPage, history.messages, remoteCount)) {
                is LatestMerge.Invalidate -> replaceHistoryFromServer(apiBase, apiKey, sessionId, history.messages, remoteCount, history.title)
                is LatestMerge.Appended -> {
                    val page = merged.page.copy(
                        reachedOldest = merged.page.reachedOldest || history.hasMore != true && merged.page.startIndex == 0,
                    )
                    persistHistoryPage(apiBase, apiKey, sessionId, history.title, remoteUpdatedAt(sessionId), page)
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
                    applyHistoryPage(sessionId, page, offline = false)
                }
            }
        }.onFailure { error ->
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onFailure
            if (historyMessages.isEmpty()) {
                _ui.update {
                    it.copy(
                        historyLoading = false,
                        historyLoadingMore = false,
                        activityLine = "history: ${error.message}",
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        historyLoading = false,
                        historyLoadingMore = false,
                        showingOfflineCache = true,
                        activityLine = SessionCachePolicy.OFFLINE_LABEL,
                    )
                }
            }
        }
    }

    private suspend fun replaceHistoryFromServer(
        apiBase: String,
        apiKey: String,
        sessionId: String,
        messages: List<ProviderMessage>,
        remoteCount: Int,
        title: String?,
    ) {
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return
        runCatching { container.sessionCache.invalidate(apiBase, sessionId, apiKey) }
        val start = (remoteCount - messages.size).coerceAtLeast(0)
        val page = MessagePage(
            messages = messages,
            startIndex = start,
            messageCount = remoteCount,
            reachedOldest = start == 0,
        )
        persistHistoryPage(apiBase, apiKey, sessionId, title, remoteUpdatedAt(sessionId), page)
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return
        applyHistoryPage(sessionId, page, offline = false)
    }

    private suspend fun syncOlderHistory(apiBase: String, apiKey: String, sessionId: String) {
        if (!currentCacheConnectionMatches(apiBase, apiKey)) return
        if (historyMessages.isEmpty()) {
            syncLatestHistory(apiBase, apiKey, sessionId)
            return
        }
        val offset = olderHistoryOffset(historyMessages.size)
        if (offset <= 0) {
            _ui.update { it.copy(historyLoadingMore = false, historyHasMore = false) }
            return
        }
        val result = container.api.sessionHistory(sessionId, limit = SessionCachePolicy.LATEST_PAGE, offset = offset)
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return
        result.onSuccess { history ->
            val remoteCount = history.messageCount ?: historyStartIndex + historyMessages.size
            val cached = currentHistoryPage()
            when (val merged = prependOlderPage(cached, history.messages, remoteCount, history.hasMore == true, requestedOffset = offset)) {
                is LatestMerge.Invalidate -> {
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onSuccess
                    runCatching { container.sessionCache.invalidate(apiBase, sessionId, apiKey) }
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
                    historyMessages = emptyList()
                    historyStartIndex = 0
                    syncLatestHistory(apiBase, apiKey, sessionId)
                }
                is LatestMerge.Appended -> {
                    persistHistoryPage(apiBase, apiKey, sessionId, history.title, remoteUpdatedAt(sessionId), merged.page)
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
                    applyHistoryPage(sessionId, merged.page, offline = false)
                }
            }
        }.onFailure { error ->
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onFailure
            _ui.update {
                it.copy(
                    historyLoading = false,
                    historyLoadingMore = false,
                    activityLine = if (historyMessages.isNotEmpty()) {
                        SessionCachePolicy.OFFLINE_LABEL
                    } else {
                        "history: ${error.message}"
                    },
                    showingOfflineCache = historyMessages.isNotEmpty(),
                )
            }
        }
    }

    private suspend fun reconcileOpenHistory(apiBase: String, apiKey: String, remote: List<RuntimeSession>) {
        if (!currentCacheConnectionMatches(apiBase, apiKey)) return
        val sessionId = historySessionId ?: return
        if (currentSessionId() != sessionId || historyMessages.isEmpty()) return
        val match = remote.firstOrNull { it.id == sessionId } ?: return
        val meta = runCatching { container.sessionCache.sessionMeta(apiBase, sessionId, apiKey) }.getOrNull()
        if (historySyncAction(meta, match) == HistorySync.Unchanged) return
        if (historyJob?.isActive == true) return
        syncLatestHistory(apiBase, apiKey, sessionId)
    }

    private fun currentHistoryPage(): MessagePage = MessagePage(
        messages = historyMessages,
        startIndex = historyStartIndex,
        messageCount = historyStartIndex + historyMessages.size,
        reachedOldest = historyStartIndex == 0,
    )

    private fun applyHistoryPage(sessionId: String, page: MessagePage, offline: Boolean) {
        if (currentSessionId() != sessionId || historySessionId != sessionId) return
        resetAssistantStream()
        toolStepIndex.clear()
        historyMessages = page.messages
        historyStartIndex = page.startIndex
        publishHistory(
            sessionId,
            offline = offline,
            activity = if (offline) SessionCachePolicy.OFFLINE_LABEL else historyLoadedLine(sessionId),
        )
    }

    private fun publishHistory(sessionId: String, offline: Boolean, activity: String) {
        val bubbles = historyToBubbles(historyMessages, historyStartIndex)
        val running = activeRuns[sessionId]?.isNotEmpty() == true
        _ui.update {
            it.copy(
                bubbles = bubbles,
                activityLine = if (running && !offline) "Agent running · history loaded" else activity,
                isResponding = running,
                historyLoading = false,
                historyLoadingMore = false,
                historyHasMore = historyStartIndex > 0,
                showingOfflineCache = offline,
                progressSteps = emptyList(),
                pendingQuote = null,
            )
        }
    }

    private suspend fun persistHistoryPage(
        apiBase: String,
        apiKey: String,
        sessionId: String,
        title: String?,
        updatedAt: String?,
        page: MessagePage,
    ) {
        val known = _ui.value.sessions.firstOrNull { it.id == sessionId }
        runCatching {
            container.sessionCache.savePage(
                apiBase = apiBase,
                sessionId = sessionId,
                apiKey = apiKey,
                title = title ?: known?.title,
                updatedAt = updatedAt ?: known?.updatedAt,
                createdAt = known?.createdAt,
                page = page,
                opened = true,
            )
        }
    }

    private fun remoteUpdatedAt(sessionId: String): String? =
        _ui.value.sessions.firstOrNull { it.id == sessionId }?.updatedAt

    private fun historyLoadedLine(sessionId: String): String {
        val running = activeRuns[sessionId]?.isNotEmpty() == true
        return if (running) "Agent running · history loaded" else "loaded ${historyMessages.size} messages"
    }

    private fun historyToBubbles(messages: List<ProviderMessage>, startIndex: Int = 0): List<ChatBubble> {
        val bubbles = mutableListOf<ChatBubble>()
        messages.forEachIndexed { index, message ->
            message.toBubbles(startIndex + index).forEach { bubble ->
                val callIndex = if (message.role == "tool" && bubble.role == "tool" && !message.toolCallId.isNullOrBlank()) {
                    bubbles.indexOfLast { it.role == "tool" && it.stepId == message.toolCallId }
                } else -1
                if (callIndex >= 0) {
                    val call = bubbles[callIndex]
                    bubbles[callIndex] = call.copy(
                        content = bubble.content,
                        toolOutput = bubble.toolOutput,
                        attachments = if (bubble.attachments.isNotEmpty()) bubble.attachments else call.attachments,
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
        val base = capUiText(content.orEmpty(), MAX_UI_MESSAGE_CHARS)
        val messageAttachments = distinctMedia(attachments.map(::ChatMedia) + contentParts.mapNotNull { part ->
            val image = part.image ?: return@mapNotNull null
            if (image.url.isNullOrBlank() && image.filePath.isNullOrBlank()) return@mapNotNull null
            ChatMedia(
                MediaAttachment(
                    type = "image",
                    fileUrl = image.url,
                    filePath = image.filePath,
                    mimeType = image.mimeType,
                ),
            )
        } + extractArtifactAttachments(base)).take(MAX_UI_ATTACHMENTS)
        val result = mutableListOf<ChatBubble>()
        reasoningContent?.takeIf { it.isNotBlank() }?.let { reasoning ->
            result += ChatBubble(
                id = "h-$idx-reasoning",
                role = "reasoning",
                content = capUiText(reasoning, MAX_UI_REASONING_CHARS),
                reasoningHasContent = true,
            )
        }
        toolCalls.forEachIndexed { toolIndex, tool ->
            result += ChatBubble(
                id = "h-$idx-tool-$toolIndex",
                role = "tool",
                content = buildToolContent(
                    name = tool.name ?: "tool",
                    args = capUiText(tool.arguments.orEmpty(), MAX_UI_TOOL_ARGS_CHARS),
                    output = null,
                    done = true,
                    success = null,
                ),
                toolName = tool.name,
                toolArgs = capUiText(tool.arguments.orEmpty(), MAX_UI_TOOL_ARGS_CHARS).takeIf { tool.arguments != null },
                toolDone = true,
                stepId = tool.id,
                attachments = messageAttachments,
            )
        }
        if (role == "tool") {
            result += ChatBubble(
                id = "h-$idx-tool-result",
                role = "tool",
                content = capUiText(base, MAX_UI_TOOL_OUTPUT_CHARS),
                toolName = name ?: "tool",
                toolOutput = capUiText(base, MAX_UI_TOOL_OUTPUT_CHARS).takeIf { it.isNotBlank() },
                toolDone = true,
                attachments = messageAttachments,
            )
        } else if (role == "system" && looksLikeCompactTrace(base)) {
            result += ChatBubble(
                id = "h-$idx-compact",
                role = "compact",
                content = compactTraceLabel(base),
                createdAt = createdAt,
            )
        } else if (base.isNotBlank() || (result.isEmpty() && toolCalls.isEmpty())) {
            result += ChatBubble(
                id = "h-$idx-${role.hashCode()}",
                role = role,
                content = base,
                createdAt = createdAt,
                usage = usage,
                attachments = messageAttachments,
            )
        }
        return result
    }

    private fun looksLikeCompactTrace(content: String): Boolean {
        val trimmed = content.trim()
        return trimmed.startsWith("{") &&
            trimmed.contains("\"summary\"") &&
            (trimmed.contains("\"trigger\"") || trimmed.contains("compact-"))
    }

    private fun compactTraceLabel(content: String): String {
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(CompactTraceRecord.serializer(), content)
        }.getOrNull()
        val pre = parsed?.preTokenEstimate
        val post = parsed?.postTokenEstimate
        val dropped = parsed?.droppedMessages
        val source = parsed?.summarySource?.takeIf { it.isNotBlank() }
        val parts = buildList {
            if (pre != null && post != null) add("${TokenFormat.compact(pre)} → ${TokenFormat.compact(post)}")
            if (dropped != null && dropped > 0) add("去掉 $dropped 条")
            if (source != null) add(source)
        }
        return if (parts.isEmpty()) "已压缩这段会话" else "已压缩 · ${parts.joinToString(" · ")}"
    }

    fun connectSocket(force: Boolean = false) {
        settingsReconnectJob?.cancel()
        settingsReconnectJob = null
        container.wsClient.connect(currentSessionId(), force = force)
    }

    fun downloadAttachment(context: Context, media: ChatMedia) {
        val descriptor = media.descriptor
        val fileName = descriptor.fileName ?: "附件"
        val downloadUrl = descriptor.fileUrl?.takeIf { it.isNotBlank() }
            ?: descriptor.filePath?.let(::artifactDescriptor)?.fileUrl
        if (downloadUrl.isNullOrBlank()) {
            publishAttachmentNotice("附件没有可下载的地址", isError = true)
            return
        }
        val downloadDescriptor = if (descriptor.fileUrl == downloadUrl) descriptor else descriptor.copy(fileUrl = downloadUrl)
        publishAttachmentNotice("准备下载 · $fileName")
        container.api.enqueueAttachmentDownload(context, downloadDescriptor)
            .onSuccess { downloadId ->
                publishAttachmentNotice("下载中 · $fileName")
                viewModelScope.launch {
                    monitorAttachmentDownload(
                        context = context.applicationContext,
                        downloadId = downloadId,
                        fileName = fileName,
                        mimeType = descriptor.mimeType,
                    )
                }
            }
            .onFailure { error -> publishAttachmentNotice("下载失败 · ${error.message ?: "未知错误"}", isError = true) }
    }

    private fun publishAttachmentNotice(
        text: String,
        isError: Boolean = false,
        isComplete: Boolean = false,
        openUri: String? = null,
        mimeType: String? = null,
    ) {
        attachmentNoticeJob?.cancel()
        _ui.update {
            it.copy(
                attachmentNotice = AttachmentNotice(
                    text = text,
                    isError = isError,
                    isComplete = isComplete,
                    openUri = openUri,
                    mimeType = mimeType,
                ),
                activityLine = text,
            )
        }
        if (isComplete || isError) {
            val expected = text
            attachmentNoticeJob = viewModelScope.launch {
                delay(5000)
                _ui.update { state ->
                    if (state.attachmentNotice?.text == expected) state.copy(attachmentNotice = null) else state
                }
            }
        }
    }

    private suspend fun monitorAttachmentDownload(
        context: Context,
        downloadId: Long,
        fileName: String,
        mimeType: String?,
    ) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: run {
                publishAttachmentNotice("下载失败 · 系统下载服务不可用", isError = true)
                return
            }
        while (currentCoroutineContext().isActive) {
            var status = -1
            var reason = 0
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                }
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uri = runCatching { manager.getUriForDownloadedFile(downloadId) }
                        .getOrNull()
                        ?.toString()
                    publishAttachmentNotice(
                        text = "下载完成 · $fileName · ${attachmentDownloadLocation(context, fileName)}",
                        isComplete = true,
                        openUri = uri,
                        mimeType = mimeType,
                    )
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    publishAttachmentNotice("下载失败 · ${downloadFailureReason(reason)}", isError = true)
                    return
                }
                -1 -> {
                    publishAttachmentNotice("下载失败 · 找不到下载任务", isError = true)
                    return
                }
            }
            delay(500)
        }
    }

    private fun attachmentDownloadLocation(context: Context, fileName: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Downloads/$fileName"
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.let { File(it, fileName).absolutePath }
                ?: "应用文件/$fileName"
        }

    fun openDownloadedAttachment(context: Context, uriString: String, mimeType: String?) {
        runCatching {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure { error ->
            publishAttachmentNotice("打开失败 · ${error.message ?: "没有可用的查看应用"}", isError = true)
        }
    }

    private fun downloadFailureReason(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "无法继续下载"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储设备不可用"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
        DownloadManager.ERROR_FILE_ERROR -> "文件写入失败"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "网络数据错误"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向次数过多"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器返回异常"
        DownloadManager.ERROR_UNKNOWN -> "未知错误"
        else -> "系统错误 $reason"
    }

    fun scrollAnchor(sessionId: String): Pair<Int, Int>? = container.settingsRepository.scrollAnchor(sessionId)

    fun saveScrollAnchor(sessionId: String, index: Int, offset: Int) {
        container.settingsRepository.saveScrollAnchor(sessionId, index, offset)
    }

    fun sendComposer() {
        val text = _ui.value.composer.trim()
        val pending = _ui.value.pendingMedia
        val hasQuote = _ui.value.pendingQuote != null
        if (text.isEmpty() && pending.isEmpty() && !hasQuote) return
        val parsedCommand = parseRuntimeCommand(text)
        val luckyCommand = if (!hasQuote) parseLuckyCommand(text) else null
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
        if (luckyCommand != null && pending.isNotEmpty()) {
            _ui.update { it.copy(activityLine = "/lucky 命令不能附带附件，请先发送或移除附件") }
            return
        }
        if (luckyCommand != null) {
            handleLuckyCommand(text, luckyCommand)
            return
        }
        if (_ui.value.luckyActive) {
            // While /lucky is on, messages are collected by the runtime and sent
            // as one turn on /lucky off. They must not enter the per-message queue.
            sendLuckySegment(
                sessionId = sessionId,
                displayText = text.ifBlank { if (pending.isNotEmpty()) "请查看附件" else "" },
                message = buildOutboundMessage(
                    text.ifBlank { if (pending.isNotEmpty()) "请查看附件" else "" },
                    _ui.value.pendingQuote,
                ),
                descriptors = pending.mapNotNull { it.descriptor },
                media = pending.mapNotNull { item -> item.descriptor?.let { ChatMedia(it, item.uri) } },
                quote = _ui.value.pendingQuote,
            )
            return
        }
        if (runtimeCommand != null) {
            sendRuntimeCommand(text, runtimeCommand)
            return
        }
        val pendingQuote = _ui.value.pendingQuote
        val baseMessage = text.ifBlank {
            when {
                pending.isNotEmpty() -> "请查看附件"
                pendingQuote != null -> "" // quote block alone is enough for the model
                else -> ""
            }
        }
        val message = buildOutboundMessage(baseMessage, pendingQuote)
        val descriptors = pending.mapNotNull { it.descriptor }
        val media = pending.mapNotNull { item -> item.descriptor?.let { ChatMedia(it, item.uri) } }
        enqueueOutboundMessage(
            sessionId = sessionId,
            displayText = text,
            message = message,
            descriptors = descriptors,
            media = media,
            quote = pendingQuote,
        )
    }

    private fun enqueueOutboundMessage(
        sessionId: String,
        displayText: String,
        message: String,
        descriptors: List<MediaAttachment>,
        media: List<ChatMedia>,
        quote: MessageQuote? = null,
        showUserBubble: Boolean = true,
        clearComposer: Boolean = true,
    ) {
        val wasRunning = isCurrentSessionRunActive()
        val outboundId = queueItemId()
        val preview = displayText.ifBlank {
            when {
                descriptors.isNotEmpty() -> "请查看附件"
                quote != null -> "引用消息"
                else -> message
            }
        }.take(120)
        outboundChatQueue.add(
            OutboundQueueItem(
                id = outboundId,
                sessionId = sessionId,
                preview = preview,
                message = message,
                attachments = descriptors,
                // The dispatcher promotes exactly one queued item to Sending.
                status = OutboundQueueStatus.Queued,
            ),
        )
        if (!wasRunning) {
            currentTurnId = "turn-${System.currentTimeMillis()}"
            toolStepIndex.clear()
            resetAssistantStream()
        }
        if (showUserBubble) {
            pushBubble(
                ChatBubble(
                    id = "u-${System.currentTimeMillis()}",
                    role = "user",
                    content = displayText,
                    createdAt = nowIsoTimestamp(),
                    attachments = media,
                    quote = quote,
                ),
            )
        }
        _ui.update {
            it.copy(
                composer = if (clearComposer) "" else it.composer,
                pendingMedia = if (clearComposer) emptyList() else it.pendingMedia,
                pendingQuote = if (clearComposer) null else it.pendingQuote,
                isResponding = true,
                outboundQueue = outboundChatQueue.items(sessionId),
                activityLine = if (wasRunning) {
                    "消息已排队 · 当前队列 ${outboundChatQueue.items(sessionId).count { item -> item.status == OutboundQueueStatus.Sending || item.status == OutboundQueueStatus.Queued }} 条"
                } else {
                    it.activityLine
                },
                progressSteps = listOf(ChatProgressStep("phase-thinking", "Thinking through the request", ProgressStatus.Active)),
            )
        }
        // Only the dispatcher sends. Subsequent messages remain local Queue
        // entries until the preceding request reaches a terminal event.
        dispatchNextOutbound(sessionId)
    }

    private fun dispatchPendingSessions() {
        outboundChatQueue.sessionIds().forEach(::dispatchNextOutbound)
    }

    private fun luckyWireAction(action: LuckyAction): String = when (action) {
        LuckyAction.On -> "on"
        LuckyAction.Off -> "off"
        LuckyAction.Status -> "status"
        LuckyAction.Cancel -> "cancel"
        LuckyAction.Unknown -> ""
    }

    private fun applyLuckyStatus(message: String) {
        val count = Regex("(\\d+)\\s*段").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val attachments = Regex("附件\\s*(\\d+)").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val active = when {
            message.contains("未开启") || message.contains("没有正在") || message.contains("已取消") || message.contains("没有收集到") || message.contains("已提交") -> false
            message.contains("已开启") || message.contains("正在收集") || message.contains("已经在收集") || message.contains("已收集") -> true
            else -> _ui.value.luckyActive
        }
        _ui.update {
            it.copy(
                luckyActive = active,
                luckySegments = if (active) count ?: it.luckySegments else 0,
                luckyAttachments = if (active) attachments ?: it.luckyAttachments else 0,
                activityLine = message,
            )
        }
    }

    private fun sendLuckySegment(
        sessionId: String,
        displayText: String,
        message: String,
        descriptors: List<MediaAttachment>,
        media: List<ChatMedia>,
        quote: MessageQuote?,
    ) {
        val id = "lucky-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        val segment = LuckyPendingSegment(
            id = id,
            sessionId = sessionId,
            preview = displayText.ifBlank { "附件" }.take(80),
            message = message,
            attachments = descriptors,
        )
        luckyPending[id] = segment
        pushBubble(
            ChatBubble(
                id = "u-$id",
                role = "user",
                content = displayText,
                createdAt = nowIsoTimestamp(),
                attachments = media,
                quote = quote,
            ),
        )
        _ui.update {
            it.copy(
                composer = "",
                pendingMedia = emptyList(),
                pendingQuote = null,
                luckyPending = visibleLuckyPending(),
                activityLine = "正在送进 Lucky 收集",
            )
        }
        deliverLuckySegment(segment)
    }

    private fun deliverLuckySegment(segment: LuckyPendingSegment) {
        val handle = container.wsClient.sendChat(
            segment.message,
            attachments = segment.attachments,
            sessionId = segment.sessionId,
            trackLease = false,
            requestId = segment.id,
        )
        if (handle != null) return
        container.wsClient.connect(segment.sessionId)
        viewModelScope.launch {
            delay(450)
            val retry = container.wsClient.sendChat(
                segment.message,
                attachments = segment.attachments,
                sessionId = segment.sessionId,
                trackLease = false,
                requestId = segment.id,
            )
            if (retry == null) markLuckySegmentFailed(segment.id, "没有送进收集，点重试")
        }
    }

    private fun acknowledgeLuckySegment(id: String) {
        if (luckyPending.remove(id) == null) return
        publishLuckyPending()
    }

    private fun markLuckySegmentFailed(id: String, error: String) {
        val current = luckyPending[id] ?: return
        luckyPending[id] = current.copy(error = error)
        publishLuckyPending(error)
    }

    private fun publishLuckyPending(activity: String? = null) {
        val visible = visibleLuckyPending()
        _ui.update { state ->
            state.copy(
                luckyPending = visible,
                activityLine = activity ?: state.activityLine,
            )
        }
    }

    private fun visibleLuckyPending(): List<LuckyPendingSegment> =
        luckyPending.values.filter { it.sessionId == currentSessionId() }

    fun retryLuckySegment(id: String) {
        val segment = luckyPending[id] ?: return
        if (segment.sessionId != currentSessionId()) return
        luckyPending[id] = segment.copy(error = null)
        publishLuckyPending("正在重新送进 Lucky 收集")
        deliverLuckySegment(luckyPending[id] ?: return)
    }

    private fun handleLuckyCommand(rawText: String, command: LuckyCommand) {
        val now = System.currentTimeMillis()
        val sessionId = currentSessionId()
        pushBubble(ChatBubble(id = "u-$now", role = "user", content = rawText, createdAt = nowIsoTimestamp()))
        _ui.update { it.copy(composer = "", pendingMedia = emptyList(), pendingQuote = null) }
        val action = luckyWireAction(command.action)
        if (action == "off" && visibleLuckyPending().isNotEmpty()) {
            visibleLuckyPending().filter { it.error != null }.forEach { deliverLuckySegment(it.copy(error = null)) }
            val notice = "还有 ${visibleLuckyPending().size} 段没进收集，确认后再提交"
            pushBubble(ChatBubble(id = "lucky-wait-$now", role = "assistant", content = notice, createdAt = nowIsoTimestamp()))
            _ui.update { it.copy(activityLine = notice, luckyPending = visibleLuckyPending()) }
            return
        }
        if (action == "cancel") {
            luckyPending.entries.removeIf { it.value.sessionId == sessionId }
        }
        when (action) {
            "on" -> _ui.update { it.copy(luckyActive = true, activityLine = "Lucky 已开启，接下来的消息会先收集") }
            "cancel" -> _ui.update { it.copy(luckyActive = false, luckySegments = 0, luckyAttachments = 0, luckyPending = emptyList()) }
        }
        if (action.isEmpty()) {
            val notice = "用法：/lucky on | /lucky off | /lucky status | /lucky cancel"
            pushBubble(ChatBubble(id = "lucky-$now", role = "assistant", content = notice, createdAt = nowIsoTimestamp()))
            _ui.update { it.copy(activityLine = notice) }
            return
        }
        if (!container.wsClient.sendLucky(sessionId, action)) {
            container.wsClient.connect(sessionId)
            viewModelScope.launch {
                delay(450)
                if (!container.wsClient.sendLucky(sessionId, action)) {
                    val notice = "Lucky 命令没有发出，请检查连接"
                    if (currentSessionId() == sessionId) {
                        pushBubble(ChatBubble(id = "lucky-err-$now", role = "assistant", content = notice, createdAt = nowIsoTimestamp()))
                        _ui.update { it.copy(activityLine = notice) }
                    }
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
                    output = "未找到 Runtime 命令 /${command.name}。请检查命令名或当前服务支持的命令。",
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
        activeRuns[sessionId]?.values?.firstOrNull()?.let { run ->
            container.wsClient.cancel(run.handle)
        }
        outboundDispatchJobs.remove(sessionId)?.cancel()
        // The runtime cancels the active run and every queued run for the
        // session. Drop their local indicators together so no stale queued row
        // remains after pressing Stop.
        outboundChatQueue.removeSession(sessionId)
        publishOutboundQueue(sessionId)
        clearRuns(sessionId)
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
        noteTaskProgress(merged)
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

    private fun ensureRuntimeWatch() {
        if (!appInForeground) return
        startTaskPolling()
        startBackgroundPolling()
        startCronPolling()
    }

    private fun stopRuntimeWatch() {
        stopTaskPolling()
        stopBackgroundPolling()
        stopCronPolling()
    }

    private fun startTaskPolling() {
        if (taskPollingJob?.isActive == true) return
        taskPollingJob = viewModelScope.launch {
            _ui.update { it.copy(taskPolling = true) }
            while (isActive && appInForeground) {
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
                selectedTask = if (it.selectedTaskId == id) it.selectedTask else null,
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

    fun updateBackgroundQuery(value: String) {
        _ui.update { it.copy(backgroundQuery = value) }
    }

    fun setBackgroundFilter(filter: BackgroundFilter) {
        _ui.update { it.copy(backgroundFilter = filter) }
    }

    fun refreshBackground() {
        viewModelScope.launch { refreshBackgroundNow() }
    }

    fun refreshCron() {
        viewModelScope.launch { refreshCronNow() }
    }

    private suspend fun refreshCronNow() {
        _ui.update { it.copy(cronLoading = true, cronError = null) }
        val result = container.api.listCronJobs()
        val payload = result.getOrNull()
        val jobs = payload?.jobs ?: _ui.value.cronJobs
        noteCronProgress(jobs)
        _ui.update {
            it.copy(
                cronLoading = false,
                cronJobs = jobs,
                cronRunning = payload?.running ?: it.cronRunning,
                cronCount = payload?.count ?: it.cronCount,
                cronError = result.exceptionOrNull()?.message,
            )
        }
    }

    private fun startCronPolling() {
        if (cronPollingJob?.isActive == true) return
        cronPollingJob = viewModelScope.launch {
            while (isActive && appInForeground) {
                refreshCronNow()
                delay(BACKGROUND_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopCronPolling() {
        cronPollingJob?.cancel()
        cronPollingJob = null
    }

    private suspend fun refreshBackgroundNow() {
        _ui.update { it.copy(backgroundLoading = true, backgroundError = null) }
        val result = container.api.getAutonomyDashboard()
        val dashboard = result.getOrNull()
        _ui.update {
            it.copy(
                backgroundLoading = false,
                backgroundDashboard = dashboard ?: it.backgroundDashboard,
                backgroundTasks = dashboard?.tasks ?: it.backgroundTasks,
                backgroundError = result.exceptionOrNull()?.message,
            )
        }
        noteBackgroundProgress(dashboard?.tasks.orEmpty())
        val selectedId = _ui.value.selectedBackgroundTaskId
        if (selectedId != null && dashboard?.tasks?.any { task -> task.id == selectedId } == true) {
            loadBackgroundTaskNow(selectedId, showLoading = false)
        }
    }

    private fun startBackgroundPolling() {
        if (backgroundPollingJob?.isActive == true) return
        backgroundPollingJob = viewModelScope.launch {
            _ui.update { it.copy(backgroundPolling = true) }
            while (isActive && appInForeground) {
                refreshBackgroundNow()
                delay(BACKGROUND_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopBackgroundPolling() {
        backgroundPollingJob?.cancel()
        backgroundPollingJob = null
        _ui.update { it.copy(backgroundPolling = false) }
    }

    fun selectBackgroundTask(id: String) {
        if (id.isBlank()) return
        _ui.update {
            it.copy(
                selectedBackgroundTaskId = id,
                selectedBackgroundTask = if (it.selectedBackgroundTaskId == id) it.selectedBackgroundTask else null,
                backgroundDetailLoading = true,
                backgroundDetailError = null,
            )
        }
        viewModelScope.launch { loadBackgroundTaskNow(id, showLoading = false) }
    }

    fun clearSelectedBackgroundTask() {
        _ui.update {
            it.copy(
                selectedBackgroundTaskId = null,
                selectedBackgroundTask = null,
                backgroundDetailLoading = false,
                backgroundDetailError = null,
            )
        }
    }

    private suspend fun loadBackgroundTaskNow(id: String, showLoading: Boolean) {
        if (showLoading) _ui.update { it.copy(backgroundDetailLoading = true, backgroundDetailError = null) }
        val result = container.api.getAutonomyTask(id)
        _ui.update { current ->
            if (current.selectedBackgroundTaskId != id) current else current.copy(
                selectedBackgroundTask = result.getOrNull() ?: current.selectedBackgroundTask,
                backgroundDetailLoading = false,
                backgroundDetailError = result.exceptionOrNull()?.message,
            )
        }
    }

    fun updateSkillsQuery(value: String) {
        _ui.update { it.copy(skillsQuery = value) }
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
                historyJob?.cancel()
                contextInspectJob?.cancel()
                historySessionId = session.id
                historyMessages = emptyList()
                historyStartIndex = 0
                container.settingsRepository.update { it.copy(sessionId = session.id) }
                foregroundRequestIds.remove(session.id)
                resetAssistantStream()
                toolStepIndex.clear()
                _ui.update {
                    it.copy(
                        bubbles = emptyList(),
                        historyLoading = false,
                        historyLoadingMore = false,
                        historyHasMore = false,
                        isResponding = activeRuns[session.id]?.isNotEmpty() == true,
                        outboundQueue = outboundChatQueue.items(session.id),
                        activityLine = "new session · ${session.id}",
                        pendingQuote = null,
                        contextInspect = null,
                        contextInspectError = null,
                    )
                }
                connectSocket()
                refreshSessions()
                refreshContextInspect()
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

    fun setSessionPinned(id: String, pinned: Boolean) {
        if (id.isBlank()) return
        viewModelScope.launch {
            container.api.patchSession(id, SessionPatchRequest(pinned = pinned))
                .onSuccess {
                    _ui.update { state ->
                        state.copy(
                            sessions = state.sessions.map { session ->
                                if (session.id == id) session.copy(pinned = pinned) else session
                            },
                            activityLine = if (pinned) "pinned · $id" else "unpinned · $id",
                        )
                    }
                    refreshSessions()
                }
                .onFailure { e ->
                    _ui.update { it.copy(activityLine = "pin: ${e.message}") }
                }
        }
    }

    fun setSessionProject(id: String, project: String) {
        if (id.isBlank()) return
        val trimmed = project.trim()
        viewModelScope.launch {
            container.api.patchSession(id, SessionPatchRequest(project = trimmed))
                .onSuccess {
                    _ui.update { state ->
                        state.copy(
                            sessions = state.sessions.map { session ->
                                if (session.id == id) session.copy(project = trimmed.ifBlank { null }) else session
                            },
                            activityLine = if (trimmed.isEmpty()) "project cleared · $id" else "project · $trimmed",
                        )
                    }
                    refreshSessions()
                }
                .onFailure { e ->
                    _ui.update { it.copy(activityLine = "project: ${e.message}") }
                }
        }
    }

    fun deleteSession(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            container.api.deleteSession(id)
                .onSuccess {
                    val connection = container.settingsRepository.snapshot()
                    runCatching { container.sessionCache.removeSession(connection.apiBase, id, connection.apiKey) }
                    val remaining = _ui.value.sessions.filter { it.id != id }
                    val current = container.settingsRepository.snapshot().sessionId
                    if (current == id) {
                        val next = remaining.firstOrNull()?.id.orEmpty()
                        if (next.isNotEmpty()) {
                            selectSession(next)
                        } else {
                            historyJob?.cancel()
                            historySessionId = ""
                            historyMessages = emptyList()
                            container.settingsRepository.update { it.copy(sessionId = "") }
                            _ui.update {
                                it.copy(
                                    bubbles = emptyList(),
                                    historyLoading = false,
                                    historyHasMore = false,
                                    activityLine = "deleted · $id",
                                )
                            }
                        }
                    }
                    _ui.update {
                        it.copy(
                            sessions = remaining,
                            activityLine = "deleted · $id",
                        )
                    }
                    refreshSessions()
                }
                .onFailure { e ->
                    _ui.update { it.copy(activityLine = "delete: ${e.message}") }
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
            val result = container.api.sessionToolTrace(sessionId, limit = 100, offset = 0)
            _ui.update {
                if (result.isSuccess) {
                    val trace = result.getOrNull()
                    it.copy(
                        trajectoryLoading = false,
                        trajectory = trace,
                        trajectoryJson = null,
                        trajectoryError = null,
                        activityLine = buildString {
                            append("trajectory · ${trace?.tools?.size ?: 0}")
                            trace?.totalCalls?.takeIf { it > trace.tools.size }?.let { append("/$it") }
                            append(" tools")
                            if (trace?.hasMore == true) append(" · showing latest 100")
                        },
                    )
                } else {
                    it.copy(
                        trajectoryLoading = false,
                        trajectory = null,
                        trajectoryError = result.exceptionOrNull()?.message,
                        trajectoryJson = result.exceptionOrNull()?.message,
                    )
                }
            }
        }
    }

    private companion object {
        fun personaNameFromPrompt(prompt: String): String {
            val identity = Regex("(?m)^-\\s*Name:\\s*([^\\n（(]+)").find(prompt)?.groupValues?.getOrNull(1)?.trim()
            if (!identity.isNullOrBlank()) return identity
            return Regex("\\*\\*([^*\\n（(]+)").find(prompt)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }

        const val TASK_POLL_INTERVAL_MS = 3_000L
        const val BACKGROUND_POLL_INTERVAL_MS = 3_000L
        val artifactPathPattern = Regex(
            """(?i)(?:MEDIA:\s*)?(?:~[/\\]\.luckyagent[/\\](?:workspace|uploads)[/\\][^\s`"'<>]+|/[^\s`"'<>/]+(?:/[^\s`"'<>/]+)*/\.luckyagent/(?:workspace|uploads)/[^\s`"'<>]+)""",
        )
    }

    override fun onCleared() {
        stopTaskPolling()
        stopBackgroundPolling()
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
