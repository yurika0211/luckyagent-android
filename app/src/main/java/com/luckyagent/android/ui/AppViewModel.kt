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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.luckyagent.android.data.AppContainer
import com.luckyagent.android.data.notification.RuntimeNoticeKind
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.cache.CachedOutboundMessage
import com.luckyagent.android.data.cache.HistorySync
import com.luckyagent.android.data.cache.LatestMerge
import com.luckyagent.android.data.cache.MessagePage
import com.luckyagent.android.data.cache.SessionCachePolicy
import com.luckyagent.android.data.cache.latestPageAlreadyStored
import com.luckyagent.android.data.cache.historySyncAction
import com.luckyagent.android.data.cache.mergeLatestPage
import com.luckyagent.android.data.cache.olderHistoryOffset
import com.luckyagent.android.data.cache.prependOlderPage
import com.luckyagent.android.data.cache.withContentTruncation
import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ModelRef
import com.luckyagent.android.data.api.ModelEffortResponse
import com.luckyagent.android.data.api.RuntimeConfigSchema
import com.luckyagent.android.data.api.RuntimeConfigSnapshot
import com.luckyagent.android.data.api.RuntimeConfigPatchResponse
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
import com.luckyagent.android.data.api.executionSession as autonomyExecutionSession
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
import com.luckyagent.android.data.api.PluginChangeResponse
import com.luckyagent.android.data.api.PluginInstallRequest
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.data.api.TaskDetail
import com.luckyagent.android.data.api.TaskOrigin
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.executionSession
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.ui.screens.SessionCardKind
import com.luckyagent.android.ui.screens.dismissedWorkCardIds
import com.luckyagent.android.ui.screens.sessionCardKey
import com.luckyagent.android.ui.screens.keepFocusedWorkCard
import com.luckyagent.android.ui.screens.visibleWorkCard
import com.luckyagent.android.data.api.toTaskNode
import com.luckyagent.android.data.api.toTaskSummary
import com.luckyagent.android.data.api.WsChatHandle
import com.luckyagent.android.data.api.WsEvent
import com.luckyagent.android.data.settings.ClientSettings
import com.luckyagent.android.data.settings.RuntimeEndpoint
import com.luckyagent.android.ui.screens.ComputerLink
import com.luckyagent.android.ui.screens.connectionFailureMessage
import com.luckyagent.android.ui.screens.connectionHostLabel
import com.luckyagent.android.ui.screens.manualConnection
import com.luckyagent.android.data.standalone.StandaloneMessage
import com.luckyagent.android.data.standalone.StandaloneSession
import com.luckyagent.android.data.standalone.StandaloneSettings
import com.luckyagent.android.data.standalone.buildStandaloneMessages
import com.luckyagent.android.data.standalone.compactStandaloneSession
import com.luckyagent.android.data.standalone.standaloneContextTokens
import com.luckyagent.android.data.update.AvailableUpdate
import com.luckyagent.android.data.work.AgentTaskWork
import com.luckyagent.android.BuildConfig
import com.luckyagent.android.ui.util.MessageQuote
import com.luckyagent.android.ui.util.TokenFormat
import com.luckyagent.android.ui.util.bubbleCopyText
import com.luckyagent.android.ui.util.buildOutboundMessage
import com.luckyagent.android.ui.screens.connectionStatusLightsTurn
import com.luckyagent.android.ui.screens.historyPageEndsWithFinalAssistant
import com.luckyagent.android.ui.screens.historySettlesUnownedTurn
import com.luckyagent.android.ui.screens.replayRegistersLiveRun
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import com.luckyagent.android.ui.util.artifactDownloadUrl
import java.io.File
import java.util.UUID

enum class AppDestination {
    Chat, Tasks, Background, Cron, Trajectory, Gateways, Skills, Plugins, Settings, Memory, RuntimeConfig
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
    /** Assistant commentary from a tool/continuation round belongs inside the process fold. */
    val isIntermediate: Boolean = false,
    /** Server preview, not the stored body. The UI can request the full page and swap this row. */
    val contentTruncated: Boolean = false,
    /** Oldest-first server index of the source message. -1 for live bubbles. */
    val historyIndex: Int = -1,
    /** Non-null only while a locally persisted user message awaits server history. */
    val outboundId: String? = null,
    val outboundStatus: OutboundQueueStatus? = null,
    val outboundError: String? = null,
    /**
     * Session-wide oldest-first index of the server message this bubble renders.
     * Null for rows that exist only in memory. This is the identity a refresh
     * reconciles against; a page offset moves, this does not.
     */
    val serverOrdinal: Int? = null,
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
    val standaloneNotice: String? = null,
    val standaloneTokens: Int = 0,
    val sessions: List<RuntimeSession> = emptyList(),
    val sessionRecap: SessionRecapPreview? = null,
    val sessionsLoading: Boolean = false,
    val sessionsError: String? = null,
    /** True when the visible sessions or history came from disk after a failed refresh. */
    val showingOfflineCache: Boolean = false,
    val sessionQuery: String = "",
    /** Session IDs currently running an agent turn (foreground or background). */
    val workingSessionIds: Set<String> = emptySet(),
    val bubbles: List<ChatBubble> = emptyList(),
    val historyLoading: Boolean = false,
    /** True while a pull-to-refresh is in flight, including when bubbles are already shown. */
    val historyRefreshing: Boolean = false,
    val historyLoadingMore: Boolean = false,
    val historyHasMore: Boolean = false,
    /** Server index whose full body is being fetched. -1 when idle. */
    val expandingHistoryIndex: Int = -1,
    val composer: String = "",
    val pendingMedia: List<PendingMedia> = emptyList(),
    /** Locally persisted outbound messages, including frames waiting for delivery or acknowledgement. */
    val outboundQueue: List<OutboundQueueItem> = emptyList(),
    val socketState: SocketState = SocketState.Idle,
    val socketError: String? = null,
    val reconnectInfo: String? = null,
    val healthText: String? = null,
    val healthOk: Boolean? = null,
    /** Shown by the first-connection step. Null when that step has nothing new to say. */
    val connectionNotice: String? = null,
    /** Ready only after an authenticated request succeeds in this session. */
    val computerLink: ComputerLink = ComputerLink.Unknown,
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
    /** Gateway name currently being started or stopped from the phone. */
    val gatewayAction: String? = null,
    val gatewayActionError: String? = null,
    val skills: List<SkillSummary> = emptyList(),
    val skillsDir: String? = null,
    val skillsLoading: Boolean = false,
    val skillsError: String? = null,
    val skillsQuery: String = "",
    val pluginManager: PluginManagerUiState = PluginManagerUiState(),
    val commands: List<RuntimeCommand> = emptyList(),
    val commandsError: String? = null,
    val commandExecuting: Boolean = false,
    val commandExecution: CommandExecution? = null,
    val activityLine: String? = null,
    /** Latest process summary shown on the chat fold until the answer lands. */
    val liveSummary: String? = null,
    /** Terminal fold label after cancel or failure. Null while a turn is running. */
    val processNotice: String? = null,
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
    val focusedWorkCardId: String? = null,
    val focusedWorkCardBackground: Boolean = false,
    val dismissedWorkCardIds: Set<String> = emptySet(),
    /** Session cards hidden on this phone. The computer still has the records. */
    val hiddenSessionCards: Set<String> = emptySet(),
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
    val modelEffort: ModelEffortResponse? = null,
    val modelEffortLoading: Boolean = false,
    val modelEffortSaving: Boolean = false,
    val modelEffortError: String? = null,
    val runtimeConfig: RuntimeConfigSnapshot? = null,
    val runtimeConfigSchema: RuntimeConfigSchema? = null,
    val runtimeConfigDraft: JsonObject? = null,
    val runtimeConfigLoading: Boolean = false,
    val runtimeConfigSaving: Boolean = false,
    val runtimeConfigError: String? = null,
    val runtimeConfigPreview: RuntimeConfigPatchResponse? = null,
    val runtimeConfigEtag: String? = null,
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
    private val _ui = MutableStateFlow(
        AppUiState(
            settings = container.settingsRepository.snapshot(),
            dismissedWorkCardIds = container.settingsRepository.dismissedWorkCardIds(),
            hiddenSessionCards = container.settingsRepository.hiddenSessionCards(),
        ),
    )
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    private var eventsJob: Job? = null
    private var settingsReconnectJob: Job? = null
    private var connectionVerifyJob: Job? = null
    private var taskPollingJob: Job? = null
    private var backgroundPollingJob: Job? = null
    private var cronPollingJob: Job? = null
    private val taskFingerprints = mutableMapOf<String, String>()
    private val backgroundFingerprints = mutableMapOf<String, String>()
    private val cronFingerprints = mutableMapOf<String, String>()
    private var progressWatchReady = false
    private var approvalPollingJob: Job? = null
    private var historyJob: Job? = null
    /** Identifies the latest pull-to-refresh, so an older job cannot clear its indicator. */
    private var historyRefreshTicket = 0
    private var contextInspectJob: Job? = null
    private var historySessionId: String? = null
    private var historyMessages: List<ProviderMessage> = emptyList()
    private var historyStartIndex = 0
    private var historyMessageCount = 0
    /**
     * True only after a history refresh for [historySessionId] finished with a
     * real count. An empty in-memory page is not that: a failed or still-running
     * load also leaves the count at 0, and that 0 must not anchor a live turn.
     */
    private var historyCountConfirmed = false
    /** Server index whose full body is currently being fetched. -1 when idle. */
    private var expandingHistoryIndex = -1
    private var assistantBufferId: String? = null
    private var assistantBufferIndex: Int? = null
    private val assistantPending = StringBuilder()
    private var assistantFlushJob: Job? = null
    private var currentTurnId = "turn-0"
    /**
     * Messages the runtime had stored when the current turn was sent. The first
     * stored row past it is that turn's user message.
     */
    private var turnSendBaseline: Int? = null
    /** Server ordinal of that user message, resolved once history catches up. */
    private var turnUserOrdinal: Int? = null
    /** In-progress turn for a session that is not on screen. Keyed by session id. */
    private val parkedTurns = mutableMapOf<String, ParkedTurn>()
    private val toolStepIndex = mutableMapOf<String, String>()
    private val mediaUploadSemaphore = Semaphore(1)
    private val outboundChatQueue = OutboundChatQueue()
    private var outboundRestored = false
    private val outboundPersistenceJobs = mutableMapOf<String, Job>()
    /** One local dispatcher per session prevents connection retries from racing. */
    private val outboundDispatchJobs = mutableMapOf<String, Job>()
    private val luckyPending = LinkedHashMap<String, LuckyPendingSegment>()
    private data class ActiveRun(
        val handle: WsChatHandle,
    )

    private data class ParkedTurn(
        val turnId: String,
        val sendBaseline: Int?,
        val userOrdinal: Int?,
        val bufferId: String?,
        val pending: String,
        val bubbles: List<ChatBubble>,
        val progressSteps: List<ChatProgressStep>,
        val liveSummary: String?,
        val processNotice: String?,
    )

    private val activeRuns = mutableMapOf<String, LinkedHashMap<String, ActiveRun>>()
    private val foregroundRequestIds = mutableMapOf<String, String>()
    private var appInForeground = false
    private var updateDownloadJob: Job? = null
    private var attachmentNoticeJob: Job? = null
    private var pluginsLoadJob: Job? = null
    private var pluginsMutationJob: Job? = null

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
                        computerLink = if (endpointChanged) ComputerLink.Unknown else current.computerLink,
                        pendingQuote = if (current.settings.sessionId != s.sessionId) null else current.pendingQuote,
                        models = if (endpointChanged) emptyList() else current.models,
                        activeModels = if (endpointChanged) emptyMap() else current.activeModels,
                        modelsLoaded = if (endpointChanged) false else current.modelsLoaded,
                        modelsLoading = if (endpointChanged) false else current.modelsLoading,
                        modelsError = if (endpointChanged) null else current.modelsError,
                        modelEffort = if (endpointChanged) null else current.modelEffort,
                        modelEffortLoading = if (endpointChanged) false else current.modelEffortLoading,
                        modelEffortSaving = if (endpointChanged) false else current.modelEffortSaving,
                        modelEffortError = if (endpointChanged) null else current.modelEffortError,
                        runtimeConfig = if (endpointChanged) null else current.runtimeConfig,
                        runtimeConfigSchema = if (endpointChanged) null else current.runtimeConfigSchema,
                        runtimeConfigDraft = if (endpointChanged) null else current.runtimeConfigDraft,
                        runtimeConfigEtag = if (endpointChanged) null else current.runtimeConfigEtag,
                        runtimeConfigLoading = if (endpointChanged) false else current.runtimeConfigLoading,
                        runtimeConfigSaving = if (endpointChanged) false else current.runtimeConfigSaving,
                        runtimeConfigError = if (endpointChanged) null else current.runtimeConfigError,
                    )
                }
                if (endpointChanged) resetForEndpointChange(s)
                val shouldVerify = s.connectionMode != "standalone" &&
                    s.apiBase.isNotBlank() &&
                    s.apiKey.isNotBlank() &&
                    (endpointChanged || _ui.value.computerLink == ComputerLink.Unknown)
                if (shouldVerify) verifyComputerConnection()
            }
        }
        viewModelScope.launch {
            container.wsClient.state.collect { st ->
                _ui.update { it.copy(socketState = st) }
            }
        }
        viewModelScope.launch {
            container.wsClient.lastError.collect { err ->
                val rejected = err?.contains("认证失败") == true
                _ui.update {
                    it.copy(
                        socketError = err,
                        computerLink = if (rejected) ComputerLink.Failed else it.computerLink,
                        connectionNotice = if (rejected) {
                            "密钥没有通过这台电脑的验证。可以重试、改地址或重新扫码。"
                        } else {
                            it.connectionNotice
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            container.wsClient.reconnectInfo.collect { info ->
                _ui.update { it.copy(reconnectInfo = info) }
            }
        }
        observeWs()
        ensureRuntimeWatch()
        if (container.settingsRepository.snapshot().connectionMode == "standalone") {
            showStandaloneSession()
        }
        historySessionId = currentSessionId()
        restoreOutboundQueue()
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
        pluginsLoadJob?.cancel()
        pluginsMutationJob?.cancel()
        _ui.update { it.copy(pluginManager = PluginManagerUiState()) }
        if (_ui.value.destination == AppDestination.Plugins) refreshPlugins()
        val target = settings.sessionId.ifBlank { "android-main" }
        historyJob?.cancel()
        contextInspectJob?.cancel()
        historySessionId = target
        historyMessages = emptyList()
        historyStartIndex = 0
        historyMessageCount = 0
        historyCountConfirmed = false
        outboundRestored = false
        outboundChatQueue.clear()
        turnSendBaseline = null
        turnUserOrdinal = null
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
                liveSummary = null,
                processNotice = null,
                activityLine = "正在读取这轮对话",
            )
        }
        restoreOutboundQueue()
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
                    markOutboundAccepted(event)
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
                                        activityLine = "已取消。可以重新发送这一条。",
                                        liveSummary = null,
                                        processNotice = "已取消",
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
                            val msg = errorText(env)
                            val matched = completeRun(event)
                            val visibleStuck = !matched &&
                                event.sessionId == currentSessionId() &&
                                (
                                    isCurrentSessionRunActive() ||
                                        _ui.value.isResponding ||
                                        _ui.value.bubbles.any { it.streaming }
                                    )
                            if (visibleStuck) {
                                // The frame may omit the request id after a journal
                                // gap. The visible session still has to leave 「生成中」.
                                // Anything visibly working is proof enough; an id alone
                                // is not, or a replayed old error would invent a failure.
                                clearRuns(event.sessionId)
                            } else if (!matched) {
                                // Nothing on screen to fix, but a send may still be
                                // queued as Sending with no id to close it by lookup.
                                // Leaving it open would block every later message in
                                // this session, since firstQueued() skips Sending.
                                if (requestId == null) failInFlightOutbound(event.sessionId, msg)
                                return@collect
                            }
                            finishOutboundRequest(event.sessionId, requestId, success = false, error = msg)
                            if (foreground || visibleStuck) {
                                finishAssistant(null)
                                clearStreamingBubbles()
                                pushBubble(
                                    ChatBubble(
                                        id = "err-${System.currentTimeMillis()}",
                                        role = "error",
                                        content = msg,
                                    ),
                                )
                                _ui.update {
                                    it.copy(
                                        activityLine = "请求失败 · ${failureNextStep(msg)}",
                                        isResponding = false,
                                        liveSummary = null,
                                        processNotice = "请求失败",
                                        progressSteps = it.progressSteps.map { step ->
                                            if (step.status == ProgressStatus.Active) step.copy(label = "请求失败", status = ProgressStatus.Failed) else step
                                        },
                                    )
                                }
                                prepareNextForeground(event.sessionId)
                            } else {
                                finishBackgroundRun(event.sessionId)
                            }
                        }
                        // Compaction runs over HTTP and registers no ActiveRun, so
                        // there is no foreground id to match. Gate on the session
                        // instead, the same way the lucky branch does.
                        "compact" -> if (event.sessionId == currentSessionId()) handleCompactEvent(env.data)
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
                                                activityLine = "已回复",
                                                liveSummary = null,
                                                processNotice = null,
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
                                else -> if (foreground) {
                                    val summary = extractField(env.data, "summary")?.trim().orEmpty()
                                    val line = summary.ifBlank { message?.trim().orEmpty() }
                                    if (line.isNotEmpty()) updatePhase("status", line)
                                    else _ui.update { it.copy(activityLine = "正在回复") }
                                }
                            }
                        }
                        "reasoning" -> {
                            if (!foreground) return@collect
                            val stage = extractField(env.data, "stage").orEmpty()
                            val round = extractField(env.data, "round")?.toIntOrNull()
                            val summary = extractField(env.data, "summary")?.trim().orEmpty()
                            val content = extractField(env.data, "content")?.trim().orEmpty()
                            if (stage == "continue") foldAssistantRound()
                            val label = when {
                                summary.isNotEmpty() -> summary
                                stage == "continue" -> "Reviewing tool results"
                                else -> "Analyzing the request"
                            }
                            if (stage == "content") {
                                if (content.isNotEmpty()) upsertReasoningBubble(round, content, true)
                                // Content stays in the sheet. Only a real summary replaces the fold.
                                if (summary.isNotEmpty()) updatePhase("reasoning-${round ?: 0}", summary)
                            } else {
                                upsertReasoningBubble(round, label, false)
                                updatePhase("reasoning-${round ?: 0}", label)
                            }
                        }
                        else -> {
                            if (foreground && env.error != null) {
                                _ui.update { it.copy(activityLine = "回复失败。回到聊天后重试这一条。") }
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
                        it.copy(activityLine = "没能读到这条状态。重新连接后再试。")
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

    private fun restoreOutboundQueue() {
        viewModelScope.launch {
            val settings = container.settingsRepository.snapshot()
            val restored = runCatching {
                container.sessionCache.listOutbound(settings.apiBase, settings.apiKey)
            }.getOrDefault(emptyList())
            if (!currentCacheConnectionMatches(settings.apiBase, settings.apiKey)) return@launch
            val restoredItems = restored.map { cached ->
                OutboundQueueItem(
                    id = cached.clientMessageId,
                    sessionId = cached.sessionId,
                    preview = cached.preview,
                    message = cached.message,
                    attachments = cached.attachments,
                    // A process restart cannot know whether a previous frame
                    // reached the computer. Replay the same client id. This is
                    // safe only while the runtime dedupes on client_message_id,
                    // including after the idempotency window expires. Accepted,
                    // ServerQueued, and Running are replayed too; there is no
                    // local proof they were stored.
                    requestId = null,
                    status = if (cached.status == OutboundQueueStatus.Failed.name) {
                        OutboundQueueStatus.Failed
                    } else {
                        OutboundQueueStatus.Queued
                    },
                    error = cached.error,
                    createdAt = cached.createdAt,
                )
            }
            val inMemoryItems = outboundChatQueue.sessionIds().flatMap(outboundChatQueue::items)
            outboundChatQueue.replace(
                (restoredItems + inMemoryItems)
                    .associateBy(OutboundQueueItem::id)
                    .values
                    .sortedBy(OutboundQueueItem::createdAt),
            )
            outboundRestored = true
            val current = currentSessionId()
            val cachedPage = runCatching {
                container.sessionCache.loadPage(settings.apiBase, current, settings.apiKey)
            }.getOrNull()
            if (cachedPage != null) {
                confirmedOutbound(current, cachedPage.messages).forEach { id ->
                    outboundChatQueue.remove(id)?.let(::removePersistedOutbound)
                }
            }
            publishOutboundQueue(currentSessionId())
            dispatchPendingSessions()
        }
    }

    private fun persistOutbound(item: OutboundQueueItem): Job {
        val settings = container.settingsRepository.snapshot()
        return enqueueOutboundPersistence(item.id) {
            runCatching {
                container.sessionCache.saveOutbound(
                    apiBase = settings.apiBase,
                    apiKey = settings.apiKey,
                    item = CachedOutboundMessage(
                        sessionId = item.sessionId,
                        clientMessageId = item.id,
                        preview = item.preview,
                        message = item.message,
                        attachments = item.attachments,
                        status = item.status.name,
                        requestId = item.requestId,
                        error = item.error,
                        createdAt = item.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun removePersistedOutbound(item: OutboundQueueItem): Job {
        val settings = container.settingsRepository.snapshot()
        return enqueueOutboundPersistence(item.id) {
            runCatching {
                container.sessionCache.removeOutbound(
                    apiBase = settings.apiBase,
                    apiKey = settings.apiKey,
                    sessionId = item.sessionId,
                    clientMessageId = item.id,
                )
            }
        }
    }

    private fun enqueueOutboundPersistence(
        clientMessageId: String,
        operation: suspend () -> Unit,
    ): Job {
        lateinit var next: Job
        synchronized(outboundPersistenceJobs) {
            val previous = outboundPersistenceJobs[clientMessageId]
            next = viewModelScope.launch(Dispatchers.IO) {
                previous?.join()
                operation()
            }
            outboundPersistenceJobs[clientMessageId] = next
        }
        next.invokeOnCompletion {
            synchronized(outboundPersistenceJobs) {
                if (outboundPersistenceJobs[clientMessageId] === next) {
                    outboundPersistenceJobs.remove(clientMessageId)
                }
            }
        }
        return next
    }

    private fun publishOutboundQueue(sessionId: String = currentSessionId()) {
        val items = outboundChatQueue.items(sessionId)
        _ui.update { current ->
            if (currentSessionId() != sessionId) {
                current
            } else {
                val byId = items.associateBy(OutboundQueueItem::id)
                current.copy(
                    outboundQueue = items,
                    bubbles = current.bubbles.map { bubble ->
                        val outboundId = bubble.outboundId ?: return@map bubble
                        val item = byId[outboundId]
                        if (item == null) {
                            bubble.copy(outboundStatus = null, outboundError = null)
                        } else {
                            bubble.copy(
                                outboundStatus = item.status,
                                outboundError = item.error,
                            )
                        }
                    },
                )
            }
        }
    }

    private fun queueItemId(): String = "outbound-${System.currentTimeMillis()}-${UUID.randomUUID()}"

    private fun dispatchNextOutbound(sessionId: String) {
        if (!outboundRestored) return
        if (outboundDispatchJobs[sessionId]?.isActive == true) return
        val item = outboundChatQueue.firstQueued(sessionId) ?: return
        val sending = outboundChatQueue.update(item.id, status = OutboundQueueStatus.Sending, clearError = true)
        if (sending != null) persistOutbound(sending)
        publishOutboundQueue(sessionId)
        val handle = container.wsClient.sendChat(
            item.message,
            attachments = item.attachments,
            sessionId = sessionId,
            requestId = item.id,
        )
        if (handle != null) {
            markOutboundSent(item.id, handle)
            registerRun(handle)
            if (activeRuns[sessionId]?.size == 1) anchorForegroundTurn()
            dispatchNextOutbound(sessionId)
            return
        }

        // Wait for the HTTP upgrade. Later submits share this session's dispatch
        // job instead of racing the handshake with a fixed-delay retry.
        val waiting = outboundChatQueue.update(item.id, status = OutboundQueueStatus.Queued, error = "正在连接")
        if (waiting != null) persistOutbound(waiting)
        publishOutboundQueue(sessionId)
        outboundDispatchJobs[sessionId] = viewModelScope.launch {
            val retryHandle = if (container.wsClient.awaitConnected(sessionId)) {
                container.wsClient.sendChat(
                    item.message,
                    attachments = item.attachments,
                    sessionId = sessionId,
                    requestId = item.id,
                )
            } else null
            if (retryHandle == null) {
                markOutboundFailed(item.id, sessionId, container.wsClient.lastError.value ?: "WebSocket 连接超时，请检查网络后重试")
            } else {
                markOutboundSent(item.id, retryHandle)
                registerRun(retryHandle)
                if (activeRuns[sessionId]?.size == 1) anchorForegroundTurn()
            }
            outboundDispatchJobs.remove(sessionId)
            dispatchNextOutbound(sessionId)
        }
    }

    private fun markOutboundSent(itemId: String, handle: WsChatHandle) {
        val updated = outboundChatQueue.update(
            id = itemId,
            status = OutboundQueueStatus.Sending,
            requestId = handle.requestId,
            clearError = true,
        )
        if (updated != null) persistOutbound(updated)
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
            val accepted = outboundChatQueue.update(
                item.id,
                status = OutboundQueueStatus.Accepted,
                clearError = true,
            )
            if (accepted != null) persistOutbound(accepted)
        } else {
            val failed = outboundChatQueue.update(
                item.id,
                status = OutboundQueueStatus.Failed,
                error = error ?: "消息发送失败",
            )
            if (failed != null) persistOutbound(failed)
        }
        publishOutboundQueue(sessionId)
        if (success && sessionId == currentSessionId()) {
            // The server stored this turn, so pull the tail and retire the
            // outbound row. Merging keeps this to the newest page.
            loadHistory(sessionId, reset = true, forceSync = true)
        }
        dispatchNextOutbound(sessionId)
    }

    private fun markOutboundFailed(itemId: String, sessionId: String, error: String) {
        val failed = outboundChatQueue.update(itemId, status = OutboundQueueStatus.Failed, error = error)
        if (failed != null) persistOutbound(failed)
        publishOutboundQueue(sessionId)
    }

    fun retryOutboundMessage(itemId: String) {
        val item = outboundChatQueue.items(currentSessionId()).firstOrNull { it.id == itemId }
            ?: return
        if (item.status != OutboundQueueStatus.Failed) return
        // A user retry may recover a connection stopped after a 401/403.
        container.wsClient.connect(item.sessionId, force = true)
        val retried = outboundChatQueue.update(item.id, status = OutboundQueueStatus.Queued, clearError = true)
        if (retried != null) persistOutbound(retried)
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

    private fun isForegroundEvent(event: WsEvent): Boolean {
        if (event.sessionId != currentSessionId()) return false
        val requestId = eventRequestId(event)
        // A reconnect notice has no request id. Comparing it with a missing
        // foreground id used to treat "connected" as the live turn.
        if (connectionStatusLightsTurn(event.envelope.type, extractField(event.envelope.data, "state"), requestId)) {
            return false
        }
        if (requestId == null) return false
        return requestId == foregroundRequestIds[event.sessionId]
    }

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
            container.notifications.notifyTask(kind, task.id, title, body, task.executionSession().orEmpty(), terminal, background = false)
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
            container.notifications.notifyTask(RuntimeNoticeKind.Background, task.id, title, body, task.autonomyExecutionSession().orEmpty(), terminal)
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
        val sessionId = extractTaskEventSessionId(data)
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
        container.notifications.notifyTask(kind, taskId, title, body, sessionId, terminal, background = false)
    }

    /** Reads both explicit and legacy metadata session links from task events. */
    private fun extractTaskEventSessionId(data: kotlinx.serialization.json.JsonElement?): String {
        val obj = data as? JsonObject ?: return ""
        val metadata = obj["metadata"] as? JsonObject
        return sequenceOf(
            extractField(data, "execution_session_id"),
            extractField(data, "session_id"),
            extractField(data, "owner_session_id"),
            (metadata?.get("execution_session_id") as? JsonPrimitive)?.contentOrNull,
            (metadata?.get("session_id") as? JsonPrimitive)?.contentOrNull,
            (metadata?.get("owner_session_id") as? JsonPrimitive)?.contentOrNull,
        ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.firstOrNull().orEmpty()
    }

    private fun ensureReplayedRun(event: WsEvent) {
        val requestId = event.envelope.parentId ?: return
        if (event.envelope.runId.isNullOrBlank()) return
        if (activeRuns[event.sessionId]?.containsKey(requestId) == true) return
        // Replay updates records. Only an explicit still-executing mark may
        // light 「生成中」. A historical status or reasoning frame must not.
        if (!replayRegistersLiveRun(event.envelope)) return
        registerRun(container.wsClient.replayHandle(event.sessionId, requestId, event.connectionId))
    }

    private fun errorText(env: com.luckyagent.android.data.api.WsEnvelope): String =
        env.error
            ?: extractField(env.data, "message")
            ?: extractText(env.data)
            ?: "Unknown error"

    /** Shown after a failed turn so 「生成中」 is replaced by a next step. */
    private fun failureNextStep(message: String): String = when {
        message.contains("journal sequence gap", ignoreCase = true) -> "这次回复没有送达，请再发一次"
        message.contains("too large", ignoreCase = true) -> "消息过大，缩短后再发"
        else -> "请再发一次"
    }

    private fun clearStreamingBubbles() {
        _ui.update { current ->
            if (current.bubbles.none { it.streaming }) return@update current
            current.copy(
                bubbles = current.bubbles.map { bubble ->
                    if (bubble.streaming) bubble.copy(streaming = false) else bubble
                },
                isResponding = false,
            )
        }
    }

    private fun ownsOutboundLease(sessionId: String): Boolean =
        activeRuns[sessionId]?.values?.any { it.handle.ownsLease } == true

    /**
     * An error frame without a request id cannot close its queue item by lookup,
     * so the message the user just sent would stay on 「发送中」 while the composer
     * already says the request failed. Fail whatever is still awaiting a receipt
     * by id, using the same persisted row so a retry keeps its client id.
     */
    private fun failInFlightOutbound(sessionId: String, message: String) {
        val stuck = outboundChatQueue.items(sessionId).filter { item ->
            item.status == OutboundQueueStatus.Sending || item.status == OutboundQueueStatus.Queued
        }
        if (stuck.isEmpty()) return
        stuck.forEach { item ->
            val failed = outboundChatQueue.update(item.id, status = OutboundQueueStatus.Failed, error = message)
            if (failed != null) persistOutbound(failed)
        }
        publishOutboundQueue(sessionId)
    }

    private fun markOutboundAccepted(event: WsEvent) {
        val requestId = eventRequestId(event) ?: return
        val item = outboundChatQueue.findByRequest(event.sessionId, requestId)
            ?: outboundChatQueue.items(event.sessionId).firstOrNull { it.id == requestId }
            ?: return
        val state = extractField(event.envelope.data, "state")?.lowercase().orEmpty()
        val message = listOfNotNull(
            extractField(event.envelope.data, "message"),
            extractField(event.envelope.data, "summary"),
            extractText(event.envelope.data),
        ).firstOrNull().orEmpty().lowercase()
        val type = event.envelope.type.lowercase()
        val next = when {
            type == "error" -> return
            state in setOf("queued", "pending", "waiting", "enqueued") ||
                message.contains("message queued") ||
                message.contains("queued") ||
                type in setOf("queued", "message_queued", "run_queued") -> OutboundQueueStatus.ServerQueued
            state in setOf("thinking", "executing", "running", "processing") ||
                type in setOf(
                    "running",
                    "stream_chunk",
                    "assistant_delta",
                    "delta",
                    "chunk",
                    "tool_call",
                    "tool",
                    "tool_result",
                    "reasoning",
                ) -> OutboundQueueStatus.Running
            type in setOf("ack", "accepted", "run_accepted", "chat_accepted", "message_accepted") ||
                event.envelope.parentId != null -> OutboundQueueStatus.Accepted
            else -> return
        }
        if (item.status == next) return
        val updated = outboundChatQueue.update(
            item.id,
            status = next,
            requestId = requestId,
            clearError = true,
        ) ?: return
        persistOutbound(updated)
        publishOutboundQueue(event.sessionId)
    }

    private fun prepareNextForeground(sessionId: String) {
        if (sessionId != currentSessionId() || activeRuns[sessionId].isNullOrEmpty()) return
        resetAssistantStream()
        currentTurnId = "turn-${System.currentTimeMillis()}"
        anchorForegroundTurn()
        _ui.update { it.copy(isResponding = true, activityLine = "正在回复") }
    }

    /**
     * Message count at the moment a queued item becomes the foreground turn.
     * The count taken when it was only queued is stale once the previous turn
     * has been stored.
     */
    private fun anchorForegroundTurn() {
        turnSendBaseline = confirmedTurnBaseline()
        turnUserOrdinal = null
        toolStepIndex.clear()
    }

    /**
     * Count of messages already stored when this turn starts, or null while that
     * count is still unknown. Zero is valid only after a successful load of an
     * empty session. A failed or unfinished load also reports zero in memory.
     */
    private fun confirmedTurnBaseline(): Int? {
        if (!historyCountConfirmed || historySessionId != currentSessionId()) return null
        return historyMessageCount.coerceAtLeast(historyStartIndex + historyMessages.size)
    }

    private fun handleToolCall(data: kotlinx.serialization.json.JsonElement?) {
        foldAssistantRound()
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
        _ui.update { it.copy(activityLine = "正在使用$name") }
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
            it.copy(activityLine = if (success) "已完成$name" else "${name}失败。回到聊天后重试这一条。")
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
            current.copy(
                isResponding = true,
                activityLine = label,
                liveSummary = label.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(160),
                processNotice = null,
                progressSteps = next,
            )
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
        val downloadUrl = artifactDownloadUrl(path, container.settingsRepository.snapshot().apiBase) ?: return null
        val fileName = path.replace('\\', '/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            fileName.substringAfterLast('.', "").lowercase(),
        ) ?: "application/octet-stream"
        return MediaAttachment(
            type = normalizeMediaType(mimeType),
            fileUrl = downloadUrl,
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
        if (assistantBufferId == null) assistantBufferId = "a-${UUID.randomUUID()}"
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
            val cachedIndex = assistantBufferIndex
                ?.takeIf { it in st.bubbles.indices && st.bubbles[it].id == id }
            val idx = cachedIndex ?: st.bubbles.indexOfLast { it.id == id }
            val list: List<ChatBubble>
            if (idx >= 0) {
                val bubble = st.bubbles[idx]
                list = st.bubbles.replaceBubbleAt(
                    idx,
                    bubble.copy(
                        content = appendUiText(bubble.content, piece, MAX_UI_MESSAGE_CHARS),
                        streaming = true,
                    ),
                )
            } else {
                list = st.bubbles + ChatBubble(
                    id = id,
                    role = "assistant",
                    content = capUiText(piece, MAX_UI_MESSAGE_CHARS),
                    streaming = true,
                )
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
                bubble.role == "assistant" && !bubble.streaming && !bubble.isIntermediate
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
            st.copy(
                bubbles = list.filterNot(::isEmptyTurnStatusPlaceholder),
                isResponding = false,
                liveSummary = null,
                processNotice = null,
            )
        }
        assistantBufferId = null
        assistantBufferIndex = null
        // The turn is over, so its anchor no longer applies to the next one.
        turnSendBaseline = null
        turnUserOrdinal = null
    }

    /** The status box opened before any reasoning arrived. It is not a stored step. */
    private fun isEmptyTurnStatusPlaceholder(bubble: ChatBubble): Boolean =
        com.luckyagent.android.ui.screens.isTurnStatusPlaceholder(bubble)

    private fun mediaKey(media: ChatMedia): String {
        val descriptor = media.descriptor
        return listOf(descriptor.fileUrl, descriptor.filePath, descriptor.fileId, descriptor.fileName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .map(::canonicalMediaKey)
            .firstOrNull()
            .orEmpty()
    }

    private fun resetAssistantStream() {
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        assistantPending.setLength(0)
        assistantBufferId = null
        assistantBufferIndex = null
    }

    private fun foldAssistantRound() {
        val id = assistantBufferId ?: return
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        // Tool events can arrive before the 40 ms stream flush. Preserve that text
        // before closing this round and starting the next assistant buffer.
        flushAssistantPending()
        _ui.update { state ->
            state.copy(bubbles = state.bubbles.map { bubble ->
                if (bubble.id == id) bubble.copy(streaming = false, isIntermediate = true) else bubble
            })
        }
        resetAssistantStream()
    }

    /**
     * A new turn gets its own status fold before the first reasoning or tool
     * event, so its status never lands in the previous turn's fold. The id is the
     * one [upsertReasoningBubble] uses for round 0, so real reasoning replaces
     * this box instead of opening a second one.
     */
    private fun openTurnStatusBox() {
        val id = "reasoning-$currentTurnId-0"
        _ui.update { state ->
            if (state.bubbles.any { it.id == id }) return@update state
            state.copy(
                bubbles = state.bubbles + ChatBubble(
                    id = id,
                    role = "reasoning",
                    content = "",
                    reasoningRound = 0,
                    reasoningHasContent = false,
                ),
            )
        }
    }

    private fun pushBubble(bubble: ChatBubble) {
        val safeBubble = bubble.copy(
            content = capUiText(bubble.content, MAX_UI_MESSAGE_CHARS),
            attachments = bubble.attachments.take(MAX_UI_ATTACHMENTS),
        )
        _ui.update { it.copy(bubbles = it.bubbles + safeBubble) }
    }

    fun navigate(dest: AppDestination) {
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
            AppDestination.Plugins -> refreshPlugins()
            AppDestination.Gateways -> refreshGateways()
            AppDestination.Trajectory -> refreshTrajectory()
            AppDestination.Chat -> Unit
            AppDestination.Settings -> Unit
            AppDestination.RuntimeConfig -> loadRuntimeConfig()
        }
    }

    /**
     * Closes a detail page first; otherwise any secondary page returns to Chat.
     * Pages are siblings reached from the drawer, so there is no deeper history to walk.
     */
    fun handleSystemBack(): Boolean {
        if (_ui.value.selectedTaskId != null) {
            clearSelectedTask()
            return true
        }
        if (_ui.value.selectedBackgroundTaskId != null) {
            clearSelectedBackgroundTask()
            return true
        }
        if (_ui.value.destination == AppDestination.RuntimeConfig) {
            navigate(AppDestination.Settings)
            return true
        }
        if (_ui.value.destination == AppDestination.Chat) return false
        navigate(AppDestination.Chat)
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
        openSession(sessionId, keepWorkCard = focusedCardBelongsTo(sessionId))
    }

    /**
     * Opens a runtime conversation from any task or notification deep link.
     * Task detail state is cleared first so Android back returns to the normal
     * chat screen instead of a stale task detail overlay.
     */
    fun openSession(sessionId: String?, keepWorkCard: Boolean = false) {
        val target = sessionId?.trim().orEmpty()
        if (target.isBlank()) {
            _ui.update { it.copy(snackbarMessage = "这个任务还没有关联执行会话") }
            return
        }
        clearSelectedTask()
        clearSelectedBackgroundTask()
        navigate(AppDestination.Chat)
        selectSession(target, keepWorkCard = keepWorkCard && focusedCardBelongsTo(target))
    }

    private fun focusedCardBelongsTo(sessionId: String): Boolean {
        val state = _ui.value
        return keepFocusedWorkCard(
            state.focusedWorkCardId,
            state.focusedWorkCardBackground,
            sessionId,
            state.tasks,
            state.backgroundTasks,
        )
    }

    /** Opens the execution conversation associated with a unified or legacy task. */
    fun openTaskSession(task: TaskSummary) {
        openSession(task.executionSession())
    }

    /** Opens the conversation used by an autonomy queue task, when present. */
    fun openBackgroundSession(sessionId: String?) {
        openSession(sessionId)
    }

    /**
     * Opens the one card a notification is about. The module list stays
     * available from navigation; this path does not land there.
     */
    fun openWorkCardFromNotification(taskId: String, background: Boolean) {
        val id = taskId.trim()
        if (id.isBlank()) return
        focusWorkCard(id, background)
        navigate(AppDestination.Chat)
        viewModelScope.launch {
            refreshTasksNow()
            refreshBackgroundNow()
        }
    }

    fun focusWorkCard(taskId: String, background: Boolean) {
        val id = taskId.trim()
        if (id.isBlank()) return
        _ui.update {
            it.copy(
                focusedWorkCardId = id,
                focusedWorkCardBackground = background,
            )
        }
    }

    private enum class CancelSurface { Card, Detail }

    fun clearFocusedWorkCard() {
        _ui.update { it.copy(focusedWorkCardId = null, focusedWorkCardBackground = false) }
    }

    /** Hides this card in chat. The task remains in the task list and on the computer. */
    fun dismissWorkCard(taskId: String) {
        val id = taskId.trim()
        if (id.isBlank()) return
        val state = _ui.value
        val placeholder = state.focusedWorkCardId == id && visibleWorkCard(
            id,
            state.focusedWorkCardBackground,
            state.tasks,
            state.backgroundTasks,
            state.settings.sessionId,
            state.dismissedWorkCardIds,
        )?.loading == true
        if (placeholder) {
            clearFocusedWorkCard()
            return
        }
        container.settingsRepository.dismissWorkCard(id)
        _ui.update {
            it.copy(
                dismissedWorkCardIds = dismissedWorkCardIds(it.dismissedWorkCardIds, id),
                focusedWorkCardId = if (it.focusedWorkCardId == id) null else it.focusedWorkCardId,
                focusedWorkCardBackground = if (it.focusedWorkCardId == id) false else it.focusedWorkCardBackground,
            )
        }
    }

    /** Hides one session card on this phone. The computer's record stays. */
    fun hideSessionCard(sessionId: String, kind: SessionCardKind, id: String) {
        val key = sessionCardKey(sessionId, kind, id)
        if (key.isEmpty()) return
        container.settingsRepository.hideSessionCard(key)
        _ui.update { it.copy(hiddenSessionCards = it.hiddenSessionCards + key) }
    }

    fun openDestinationFromNotification(destination: String) {
        when (destination) {
            "tasks" -> navigate(AppDestination.Tasks)
            "background" -> navigate(AppDestination.Background)
            "cron" -> navigate(AppDestination.Cron)
            "plugins" -> navigate(AppDestination.Plugins)
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

    /** Removes a message from the current local transcript after confirmation. */
    fun deleteBubble(id: String) {
        if (id.isBlank()) return
        _ui.update { state -> state.copy(bubbles = state.bubbles.filterNot { it.id == id }) }
    }

    /** Re-sends the latest user turn so the last assistant answer can be regenerated. */
    fun retryLastResponse() {
        val text = _ui.value.bubbles
            .lastOrNull { it.role.equals("user", ignoreCase = true) }
            ?.content
            ?.trim()
            .orEmpty()
        if (text.isBlank()) return
        _ui.update { it.copy(composer = text, pendingQuote = null, snackbarMessage = null) }
        sendComposer()
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
                loadModelEffort(force = true)
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

    fun loadModelEffort(force: Boolean = false) {
        val current = _ui.value
        if (current.modelEffortLoading || (!force && current.modelEffort != null)) return
        val endpointSnapshot = container.settingsRepository.snapshot()
        _ui.update { it.copy(modelEffortLoading = true, modelEffortError = null) }
        viewModelScope.launch {
            container.api.getModelEffort().onSuccess { effort ->
                val active = container.settingsRepository.snapshot()
                if (active.apiBase == endpointSnapshot.apiBase && active.apiKey == endpointSnapshot.apiKey && active.useBearer == endpointSnapshot.useBearer) {
                    _ui.update { it.copy(modelEffort = effort, modelEffortLoading = false, modelEffortError = null) }
                }
            }.onFailure { error ->
                val active = container.settingsRepository.snapshot()
                if (active.apiBase == endpointSnapshot.apiBase && active.apiKey == endpointSnapshot.apiKey && active.useBearer == endpointSnapshot.useBearer) {
                    _ui.update { it.copy(modelEffortLoading = false, modelEffortError = error.message ?: "读取思维强度失败") }
                }
            }
        }
    }

    fun setModelEffort(value: String) {
        val effort = value.trim().lowercase()
        if (effort.isBlank() || _ui.value.modelEffortSaving) return
        val current = _ui.value.modelEffort
        if (current != null && !current.options.contains(effort) && effort != "auto") return
        val endpointSnapshot = container.settingsRepository.snapshot()
        _ui.update { it.copy(modelEffortSaving = true, modelEffortError = null) }
        viewModelScope.launch {
            container.api.setModelEffort(effort).onSuccess { next ->
                val active = container.settingsRepository.snapshot()
                if (active.apiBase == endpointSnapshot.apiBase && active.apiKey == endpointSnapshot.apiKey && active.useBearer == endpointSnapshot.useBearer) {
                    _ui.update { it.copy(modelEffort = next, modelEffortSaving = false, modelEffortError = null, snackbarMessage = "思维强度已更新") }
                }
            }.onFailure { error ->
                val active = container.settingsRepository.snapshot()
                if (active.apiBase == endpointSnapshot.apiBase && active.apiKey == endpointSnapshot.apiKey && active.useBearer == endpointSnapshot.useBearer) {
                    _ui.update { it.copy(modelEffortSaving = false, modelEffortError = error.message ?: "保存思维强度失败") }
                }
            }
        }
    }

    fun loadRuntimeConfig(force: Boolean = false) {
        val current = _ui.value
        if (current.runtimeConfigLoading || (!force && current.runtimeConfig != null && current.runtimeConfigSchema != null)) return
        val endpointSnapshot = container.settingsRepository.snapshot()
        _ui.update { it.copy(runtimeConfigLoading = true, runtimeConfigError = null) }
        viewModelScope.launch {
            coroutineScope {
                val config = async { container.api.getRuntimeConfig() }
                val schema = async { container.api.getRuntimeConfigSchema() }
                val c = config.await(); val s = schema.await()
                val activeEndpoint = container.settingsRepository.snapshot()
                if (activeEndpoint.apiBase != endpointSnapshot.apiBase || activeEndpoint.apiKey != endpointSnapshot.apiKey || activeEndpoint.useBearer != endpointSnapshot.useBearer) return@coroutineScope
                if (c.isSuccess && s.isSuccess) {
                    val snapshot = c.getOrThrow()
                    _ui.update { it.copy(runtimeConfig = snapshot, runtimeConfigEtag = snapshot.etag, runtimeConfigSchema = s.getOrThrow(), runtimeConfigDraft = snapshot.config, runtimeConfigLoading = false, runtimeConfigError = null) }
                } else {
                    _ui.update { it.copy(runtimeConfigLoading = false, runtimeConfigError = c.exceptionOrNull()?.message ?: s.exceptionOrNull()?.message ?: "加载配置失败") }
                }
            }
        }
    }

    fun updateRuntimeConfigDraft(path: String, value: JsonElement?) {
        val root = (_ui.value.runtimeConfigDraft ?: JsonObject(emptyMap())).toMutableMap()
        val parts = path.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var obj = root
        parts.dropLast(1).forEach { part ->
            val next = (obj[part] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            obj[part] = JsonObject(next); obj = next
        }
        if (value == null) obj.remove(parts.last()) else obj[parts.last()] = value
        _ui.update { it.copy(runtimeConfigDraft = JsonObject(root), runtimeConfigPreview = null) }
    }

    fun previewRuntimeConfig() = submitRuntimeConfig(dryRun = true)

    fun saveRuntimeConfig(clearSecrets: List<String> = emptyList()) = submitRuntimeConfig(dryRun = false, clearSecrets = clearSecrets)

    private fun submitRuntimeConfig(dryRun: Boolean, clearSecrets: List<String> = emptyList()) {
        val patch = _ui.value.runtimeConfigDraft ?: return
        val endpointSnapshot = container.settingsRepository.snapshot()
        val etagSnapshot = _ui.value.runtimeConfigEtag
        _ui.update { it.copy(runtimeConfigSaving = true, runtimeConfigError = null) }
        viewModelScope.launch {
            container.api.patchRuntimeConfig(patch, clearSecrets, etagSnapshot, dryRun).onSuccess { result ->
                val activeEndpoint = container.settingsRepository.snapshot()
                if (activeEndpoint.apiBase != endpointSnapshot.apiBase || activeEndpoint.apiKey != endpointSnapshot.apiKey || activeEndpoint.useBearer != endpointSnapshot.useBearer) return@onSuccess
                _ui.update { state ->
                    if (dryRun) state.copy(runtimeConfigPreview = result, runtimeConfigSaving = false)
                    else state.copy(runtimeConfig = result.config?.let { RuntimeConfigSnapshot(it, result.version) } ?: state.runtimeConfig, runtimeConfigEtag = result.version.ifBlank { state.runtimeConfigEtag }, runtimeConfigDraft = result.config ?: state.runtimeConfigDraft, runtimeConfigPreview = result, runtimeConfigSaving = false, runtimeConfigError = null, snackbarMessage = "配置已保存")
                }
            }.onFailure { error ->
                val activeEndpoint = container.settingsRepository.snapshot()
                if (activeEndpoint.apiBase == endpointSnapshot.apiBase && activeEndpoint.apiKey == endpointSnapshot.apiKey && activeEndpoint.useBearer == endpointSnapshot.useBearer) {
                    _ui.update { it.copy(runtimeConfigSaving = false, runtimeConfigError = error.message ?: "保存配置失败（可能已被其他客户端修改）") }
                }
            }
        }
    }

    fun reloadRuntimeConfig() {
        viewModelScope.launch {
            container.api.reloadRuntimeConfig().onSuccess { loadRuntimeConfig(force = true) }
                .onFailure { error -> _ui.update { it.copy(runtimeConfigError = error.message ?: "重载配置失败") } }
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
        _ui.update { it.copy(activityLine = message, connectionNotice = message, healthOk = false) }
    }

    fun applyPairingQr(raw: String) {
        val endpoint = try {
            com.luckyagent.android.data.settings.PairingQr.parse(raw).toEndpoint()
        } catch (error: Exception) {
            _ui.update {
                it.copy(
                    activityLine = error.message ?: "这不是 LuckyAgent 配对码",
                    connectionNotice = error.message ?: "这不是 LuckyAgent 配对码。可以重新扫码，或改成手动填写。",
                    healthOk = false,
                )
            }
            return
        }
        updateSettings { settings ->
            val endpoints = settings.runtimeEndpoints + endpoint
            settings.copy(runtimeEndpoints = endpoints, activeRuntimeEndpointId = endpoint.id)
        }
        refreshSessions()
        verifyComputerConnection(endpoint.name)
    }

    fun submitFirstConnection(address: String, key: String) {
        val parsed = manualConnection(address, key)
        if (parsed == null) {
            _ui.update {
                it.copy(
                    healthOk = false,
                    connectionNotice = "地址需要以 http:// 或 https:// 开头，并填写密钥。",
                )
            }
            return
        }
        val label = connectionHostLabel(parsed.apiBase)
        updateSettings { settings ->
            val currentId = settings.activeRuntimeEndpointId.ifBlank { "endpoint-default" }
            val endpoint = RuntimeEndpoint(
                id = currentId,
                name = label,
                apiBase = parsed.apiBase,
                apiKey = parsed.apiKey,
                useBearer = parsed.useBearer,
            )
            val endpoints = if (settings.runtimeEndpoints.any { it.id == currentId }) {
                settings.runtimeEndpoints.map { if (it.id == currentId) endpoint else it }
            } else {
                settings.runtimeEndpoints + endpoint
            }
            settings.copy(runtimeEndpoints = endpoints, activeRuntimeEndpointId = currentId)
        }
        refreshSessions()
        verifyComputerConnection(label)
    }

    fun saveRuntimeEndpoint(endpoint: RuntimeEndpoint) {
        val normalized = endpoint.copy(
            name = endpoint.name.trim().ifBlank { "Runtime" },
            apiBase = endpoint.apiBase.trim().trimEnd('/'),
            apiKey = endpoint.apiKey.trim(),
            wsUrl = endpoint.wsUrl.trim(),
        )
        if (normalized.apiBase.isBlank()) {
            _ui.update { it.copy(activityLine = "未连接。打开设置填写服务地址。") }
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
            _ui.update { it.copy(activityLine = "至少保留一台电脑。") }
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
                    healthText = result.getOrElse { error -> error.message ?: "没能连上这台电脑" },
                    activityLine = if (result.isSuccess) "已连接" else "未连接。重新连接后再试。",
                )
            }
        }
    }

    /**
     * Confirms the saved address and key with an authenticated request.
     * /health/live does not check the key, so it cannot open the chat gate.
     */
    fun verifyComputerConnection(connectedName: String? = null) {
        val settings = container.settingsRepository.snapshot()
        if (settings.connectionMode == "standalone") return
        if (settings.apiBase.isBlank() || settings.apiKey.isBlank()) {
            _ui.update {
                it.copy(
                    computerLink = ComputerLink.Failed,
                    connectionNotice = "先填写电脑地址和密钥。",
                )
            }
            return
        }
        val name = connectionHostLabel(settings.apiBase, connectedName)
        connectionVerifyJob?.cancel()
        _ui.update { it.copy(computerLink = ComputerLink.Checking, connectionNotice = "正在连接…") }
        connectionVerifyJob = viewModelScope.launch {
            val result = container.api.listSessions()
            if (!currentCacheConnectionMatches(settings.apiBase, settings.apiKey)) return@launch
            val connected = "已连接 $name"
            val failed = connectionFailureMessage(result.exceptionOrNull())
            _ui.update {
                it.copy(
                    computerLink = if (result.isSuccess) ComputerLink.Ready else ComputerLink.Failed,
                    connectionNotice = if (result.isSuccess) connected else failed,
                    snackbarMessage = if (result.isSuccess) connected else it.snackbarMessage,
                    activityLine = if (result.isSuccess) connected else failed,
                    healthText = result.exceptionOrNull()?.message ?: it.healthText,
                )
            }
            if (result.isSuccess) connectSocket(force = true)
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
                        sessionRecap = remote.firstOrNull { session -> session.id == it.settings.sessionId }
                            ?.let { session -> SessionRecapPreview(session.id, apiBase, session.recap) }
                            ?: it.sessionRecap,
                        sessionsError = null,
                        showingOfflineCache = false,
                    )
                }
                if (q.isBlank()) reconcileOpenHistory(apiBase, apiKey, remote)
                // Search/pagination can omit the open conversation. Refresh its
                // preview independently without scanning or replacing history.
                val selected = currentSessionId()
                if (remote.none { it.id == selected }) {
                    val history = container.api.sessionHistory(selected, limit = 1).getOrNull()?.history
                    if (history != null && currentCacheConnectionMatches(apiBase, apiKey) && currentSessionId() == selected) {
                        _ui.update { it.copy(sessionRecap = SessionRecapPreview(selected, apiBase, history.recap)) }
                    }
                }
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

    fun selectSession(id: String, keepWorkCard: Boolean = false) {
        val target = id.ifBlank { "android-main" }
        val leaving = currentSessionId()
        if (leaving != target) parkCurrentTurn(leaving)
        val connection = container.settingsRepository.snapshot()
        historyJob?.cancel()
        contextInspectJob?.cancel()
        historySessionId = target
        historyMessages = emptyList()
        historyStartIndex = 0
        historyMessageCount = 0
        historyCountConfirmed = false
        viewModelScope.launch {
            runCatching { container.sessionCache.touchOpened(connection.apiBase, target, apiKey = connection.apiKey) }
        }
        container.settingsRepository.update { it.copy(sessionId = target) }
        if (leaving != target && !keepWorkCard) clearFocusedWorkCard()
        resetAssistantStream()
        toolStepIndex.clear()
        val parked = parkedTurns.remove(target)
        if (parked != null && activeRuns[target]?.isNotEmpty() == true) {
            currentTurnId = parked.turnId
            turnSendBaseline = parked.sendBaseline
            turnUserOrdinal = parked.userOrdinal
            assistantBufferId = parked.bufferId
            assistantPending.append(parked.pending)
        } else {
            turnSendBaseline = null
            turnUserOrdinal = null
        }
        _ui.update {
            it.copy(
                bubbles = if (parked != null && activeRuns[target]?.isNotEmpty() == true) {
                    parked.bubbles
                } else {
                    pendingOutboundBubbles(target)
                },
                historyLoading = true,
                historyLoadingMore = false,
                historyHasMore = false,
                isResponding = activeRuns[target]?.isNotEmpty() == true,
                outboundQueue = outboundChatQueue.items(target),
                progressSteps = if (parked != null && activeRuns[target]?.isNotEmpty() == true) parked.progressSteps else emptyList(),
                liveSummary = if (parked != null && activeRuns[target]?.isNotEmpty() == true) parked.liveSummary else null,
                processNotice = if (parked != null && activeRuns[target]?.isNotEmpty() == true) parked.processNotice else null,
                pendingQuote = null,
                contextInspect = null,

                sessionRecap = it.sessions.firstOrNull { session -> session.id == target }
                    ?.let { session -> SessionRecapPreview(target, connection.apiBase, session.recap) },
                contextInspectError = null,
                contextInspectLoading = true,
                luckyActive = false,
                luckySegments = 0,
                luckyAttachments = 0,
                luckyPending = luckyPending.values.filter { it.sessionId == target },
                activityLine = "正在读取这轮对话",
            )
        }
        container.wsClient.replaySession(target)
        dispatchPendingSessions()
        loadHistory(target)
        refreshContextInspect()
    }

    fun loadHistory(
        sessionId: String = currentSessionId(),
        reset: Boolean = true,
        forceSync: Boolean = false,
    ) {
        val target = sessionId.ifBlank { "android-main" }
        if (target != currentSessionId()) return
        if (!reset && (_ui.value.historyLoading || _ui.value.historyLoadingMore || !_ui.value.historyHasMore)) return

        if (reset) {
            historyJob?.cancel()
            historySessionId = target
        }
        // A newer reset owns the refresh indicator; only the job holding this
        // ticket may clear it.
        val refreshTicket = if (reset) ++historyRefreshTicket else historyRefreshTicket
        val loadingMore = !reset
        val refreshing = reset && _ui.value.bubbles.isNotEmpty()
        _ui.update {
            it.copy(
                historyLoading = reset && it.bubbles.isEmpty(),
                historyRefreshing = refreshing,
                historyLoadingMore = loadingMore,
                activityLine = if (reset) {
                    if (it.bubbles.isEmpty()) "正在读取这轮对话" else "正在刷新"
                } else {
                    "正在读取更早的对话"
                },
            )
        }

        historyJob = viewModelScope.launch {
            val connection = container.settingsRepository.snapshot()
            val apiBase = connection.apiBase
            val apiKey = connection.apiKey
            try {
                if (reset) {
                    val painted = paintCachedHistory(apiBase, apiKey, target)
                    if (!painted && currentSessionId() == target) {
                        historyMessages = emptyList()
                        historyStartIndex = 0
                        historyMessageCount = 0
                        historyCountConfirmed = false
                        _ui.update {
                            it.copy(
                                bubbles = pendingOutboundBubbles(target),
                                historyLoading = true,
                                activityLine = "正在读取这轮对话",
                            )
                        }
                    }
                }
                if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != target || historySessionId != target) {
                    stopHistoryRefresh(target, refreshTicket)
                    return@launch
                }
                if (reset) {
                    syncLatestHistory(apiBase, apiKey, target, forceSync = forceSync)
                } else {
                    syncOlderHistory(apiBase, apiKey, target)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentSessionId() == target && historySessionId == target) {
                    failHistoryRefresh(target, error.message)
                }
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

    private suspend fun syncLatestHistory(
        apiBase: String,
        apiKey: String,
        sessionId: String,
        forceSync: Boolean = false,
    ) {
        if (!currentCacheConnectionMatches(apiBase, apiKey)) return
        val cached = runCatching { container.sessionCache.sessionMeta(apiBase, sessionId, apiKey) }.getOrNull()
        val remote = _ui.value.sessions.firstOrNull { it.id == sessionId }
        val action = when {
            historyMessages.isEmpty() && (remote?.messageCount ?: cached?.messageCount ?: 0) > 0 ->
                HistorySync.Invalidate
            else -> historySyncAction(cached, remote ?: RuntimeSession(id = sessionId))
        }
        // A turn that just finished, or an explicit pull, can land before the
        // session list reflects it. Do not trust an "unchanged" verdict then.
        if (action == HistorySync.Unchanged && !forceSync &&
            (historyMessages.isNotEmpty() || cached?.messageCount == 0)
        ) {
            finishHistoryRefresh(sessionId, offline = false, activity = historyLoadedLine(sessionId))
            return
        }
        if (action == HistorySync.Invalidate) {
            syncFullHistory(apiBase, apiKey, sessionId)
            return
        }

        // The in-memory session list can lag behind messages pushed over the
        // WebSocket, but a contiguous cache only needs the latest full-content
        // page when its metadata says the count grew.
        val result = container.api.sessionHistory(
            sessionId,
            limit = SessionCachePolicy.LATEST_PAGE,
            offset = 0,
            includeFull = true,
        )
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return
        result.onSuccess { fetch ->
            if (fetch.notModified) {
                finishHistoryRefresh(sessionId, offline = false, activity = historyLoadedLine(sessionId))
                return@onSuccess
            }
            val history = fetch.history ?: return@onSuccess failHistoryRefresh(sessionId, "empty history")
            _ui.update { it.copy(sessionRecap = SessionRecapPreview(sessionId, apiBase, history.recap)) }
            val remoteCount = history.messageCount ?: (historyMessages.size + history.messages.size)
            val cachedPage = currentHistoryPage().takeIf { historyMessages.isNotEmpty() }
            val stored = runCatching { container.sessionCache.loadPage(apiBase, sessionId, apiKey) }.getOrNull()
            if (stored != null && latestPageAlreadyStored(stored, history.messages, remoteCount)) {
                finishHistoryRefresh(sessionId, offline = false, activity = historyLoadedLine(sessionId))
                return@onSuccess
            }
            when (val merged = mergeLatestPage(cachedPage, history.messages, remoteCount)) {
                is LatestMerge.Invalidate -> syncFullHistory(apiBase, apiKey, sessionId)
                is LatestMerge.Appended -> {
                    val page = merged.page.copy(
                        reachedOldest = merged.page.reachedOldest ||
                            (history.hasMore != true && merged.page.startIndex == 0),
                    ).withContentTruncation(history.contentTruncated == true)
                    persistHistoryPage(
                        apiBase,
                        apiKey,
                        sessionId,
                        history.title,
                        history.updatedAt,
                        page,
                        stored = stored,
                    )
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
                    applyHistoryPage(sessionId, page, offline = false)
                }
            }
        }.onFailure { error ->
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onFailure
            failHistoryRefresh(sessionId, error.message)
        }
    }

    private suspend fun syncFullHistory(apiBase: String, apiKey: String, sessionId: String) {
        if (!currentCacheConnectionMatches(apiBase, apiKey)) return
        val result = container.api.sessionHistory(
            sessionId,
            limit = null,
            offset = 0,
            includeFull = true,
        )
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return
        result.onSuccess { fetch ->
            if (fetch.notModified) {
                finishHistoryRefresh(sessionId, offline = false, activity = historyLoadedLine(sessionId))
                return@onSuccess
            }
            val history = fetch.history ?: return@onSuccess failHistoryRefresh(sessionId, "empty history")
            _ui.update { it.copy(sessionRecap = SessionRecapPreview(sessionId, apiBase, history.recap)) }
            val remoteCount = history.messageCount ?: history.messages.size
            val start = (remoteCount - history.messages.size).coerceAtLeast(0)
            val page = MessagePage(
                messages = history.messages,
                startIndex = start,
                messageCount = remoteCount,
                reachedOldest = start == 0 && history.hasMore != true && history.contentTruncated != true,
            ).withContentTruncation(history.contentTruncated == true)
            persistHistoryPage(
                apiBase,
                apiKey,
                sessionId,
                history.title,
                history.updatedAt,
                page,
            )
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
            applyHistoryPage(sessionId, page, offline = false)
        }.onFailure { error ->
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onFailure
            failHistoryRefresh(sessionId, error.message)
        }
    }

    private fun failHistoryRefresh(sessionId: String, message: String?) {
        if (currentSessionId() != sessionId || historySessionId != sessionId) return
        if (historyMessages.isEmpty()) {
            _ui.update {
                it.copy(
                    historyLoading = false,
                    historyRefreshing = false,
                    historyLoadingMore = false,
                    activityLine = "没能读到这轮对话。回到聊天后重试这一条。",
                )
            }
        } else {
            _ui.update {
                it.copy(
                    historyLoading = false,
                    historyRefreshing = false,
                    historyLoadingMore = false,
                    showingOfflineCache = true,
                    activityLine = SessionCachePolicy.REFRESH_FAILED_LABEL,
                )
            }
        }
    }

    private fun finishHistoryRefresh(sessionId: String, offline: Boolean, activity: String) {
        if (currentSessionId() != sessionId || historySessionId != sessionId) return
        _ui.update {
            it.copy(
                historyLoading = false,
                historyRefreshing = false,
                historyLoadingMore = false,
                showingOfflineCache = offline,
                activityLine = activity,
            )
        }
    }

    /**
     * Drop this refresh's indicator when it cannot finish normally, for example
     * when the connection changed under it. A newer refresh keeps its own.
     */
    private fun stopHistoryRefresh(sessionId: String, ticket: Int) {
        if (ticket != historyRefreshTicket || currentSessionId() != sessionId) return
        _ui.update {
            it.copy(
                historyLoading = false,
                historyRefreshing = false,
                historyLoadingMore = false,
            )
        }
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
        val result = container.api.sessionHistory(
            sessionId,
            limit = SessionCachePolicy.LATEST_PAGE,
            offset = offset,
            includeFull = true,
        )
        if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return
        result.onSuccess { fetch ->
            if (fetch.notModified) {
                finishHistoryRefresh(sessionId, offline = false, activity = historyLoadedLine(sessionId))
                return@onSuccess
            }
            val history = fetch.history ?: return@onSuccess failHistoryRefresh(sessionId, "empty history")
            val remoteCount = history.messageCount ?: historyStartIndex + historyMessages.size
            val cached = currentHistoryPage()
            when (val merged = prependOlderPage(cached, history.messages, remoteCount, history.hasMore == true, requestedOffset = offset).let { result ->
                if (result is LatestMerge.Appended) {
                    LatestMerge.Appended(result.page.withContentTruncation(history.contentTruncated == true))
                } else {
                    result
                }
            }) {
                is LatestMerge.Invalidate -> {
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onSuccess
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
                    historyMessages = emptyList()
                    historyStartIndex = 0
                    historyMessageCount = 0
                    historyCountConfirmed = false
                    syncFullHistory(apiBase, apiKey, sessionId)
                }
                is LatestMerge.Appended -> {
                    persistHistoryPage(apiBase, apiKey, sessionId, history.title, history.updatedAt, merged.page)
                    if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId || historySessionId != sessionId) return@onSuccess
                    applyHistoryPage(sessionId, merged.page, offline = false)
                }
            }
        }.onFailure { error ->
            if (!currentCacheConnectionMatches(apiBase, apiKey) || currentSessionId() != sessionId) return@onFailure
            _ui.update {
                it.copy(
                    historyLoading = false,
                    historyRefreshing = false,
                    historyLoadingMore = false,
                    activityLine = if (historyMessages.isNotEmpty()) {
                        SessionCachePolicy.REFRESH_FAILED_LABEL
                    } else {
                        "没能读到这轮对话。回到聊天后重试这一条。"
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
        messageCount = historyMessageCount.coerceAtLeast(historyStartIndex + historyMessages.size),
        reachedOldest = historyStartIndex == 0,
    )

    private fun parkCurrentTurn(sessionId: String) {
        if (activeRuns[sessionId].isNullOrEmpty()) {
            parkedTurns.remove(sessionId)
            return
        }
        flushAssistantPending()
        val state = _ui.value
        parkedTurns[sessionId] = ParkedTurn(
            turnId = currentTurnId,
            sendBaseline = turnSendBaseline,
            userOrdinal = turnUserOrdinal,
            bufferId = assistantBufferId,
            pending = assistantPending.toString(),
            bubbles = state.bubbles,
            progressSteps = state.progressSteps,
            liveSummary = state.liveSummary,
            processNotice = state.processNotice,
        )
    }

    private suspend fun applyHistoryPage(sessionId: String, page: MessagePage, offline: Boolean) {
        if (currentSessionId() != sessionId || historySessionId != sessionId) return
        val keepLiveTurn = !offline && activeRuns[sessionId]?.isNotEmpty() == true
        if (!keepLiveTurn) {
            resetAssistantStream()
            toolStepIndex.clear()
            parkedTurns.remove(sessionId)
        }
        val historyBubbles = withContext(Dispatchers.Default) {
            historyToBubbles(page.messages, page.startIndex)
        }
        if (currentSessionId() != sessionId || historySessionId != sessionId) return
        historyMessages = page.messages
        historyStartIndex = page.startIndex
        historyMessageCount = page.messageCount
        // A cache paint can happen before the server answers. It is a real local
        // count, so a turn may anchor on it; a later server page replaces it.
        historyCountConfirmed = true
        val settleUnowned = historyPageEndsWithFinalAssistant(historyMessages)
        val activity = if (offline) SessionCachePolicy.OFFLINE_LABEL else historyLoadedLine(sessionId)
        // History projection runs off the main thread. Bubble ids are only touched on main.
        withContext(Dispatchers.Main.immediate) {
            if (currentSessionId() != sessionId || historySessionId != sessionId) return@withContext
            if (
                historySettlesUnownedTurn(
                    settleUnowned,
                    ownsOutboundLease(sessionId),
                )
            ) {
                // The page already has the final reply and this phone did not
                // send the turn. Drop the replayed run so history cannot keep 「生成中」.
                clearRuns(sessionId)
                resetAssistantStream()
            }
            val stillOwned = ownsOutboundLease(sessionId) && activeRuns[sessionId]?.isNotEmpty() == true
            if (keepLiveTurn && stillOwned) {
                turnUserOrdinal = resolveTurnOrdinal(page) ?: turnUserOrdinal
            } else {
                turnSendBaseline = null
                turnUserOrdinal = null
            }
            val shownActivity = if (offline) {
                activity
            } else if (activeRuns[sessionId]?.isNotEmpty() == true) {
                "正在回复"
            } else {
                "loaded ${historyMessages.size} messages"
            }
            publishHistory(
                sessionId = sessionId,
                offline = offline,
                activity = shownActivity,
                historyBubbles = historyBubbles,
                preserveLiveTurn = keepLiveTurn && stillOwned,
            )
        }
    }

    /**
     * The stored user message that opened the current turn, or null while the
     * runtime has not written it yet.
     *
     * [turnSendBaseline] is the message count when the turn was sent, so the row
     * at that ordinal is the candidate. It must still be a user row carrying this
     * turn's text: a stale count or an older identical message must not make the
     * live turn look stored.
     */
    private fun resolveTurnOrdinal(page: MessagePage): Int? {
        val baseline = turnSendBaseline ?: return null
        // The count is only an anchor after history includes the rows that existed
        // when this turn started. An older count can land on a previous user row.
        val covered = page.messageCount.coerceAtLeast(page.startIndex + page.messages.size)
        if (covered < baseline) return null
        val offset = baseline - page.startIndex
        if (offset < 0 || offset >= page.messages.size) return null
        val candidate = page.messages[offset]
        if (!candidate.role.equals("user", ignoreCase = true)) return null
        val outboundId = foregroundOutboundId()
        val sameRequest = !candidate.clientMessageId.isNullOrBlank() &&
            candidate.clientMessageId == outboundId
        if (sameRequest) return baseline
        // A command or continuation has no user bubble. Text equality cannot
        // prove it is this request, so leave the live rows in place.
        if (outboundId != null && _ui.value.bubbles.none { it.outboundId == outboundId && it.role.equals("user", ignoreCase = true) }) {
            return null
        }
        val live = com.luckyagent.android.ui.screens.liveTurnBubbles(
            _ui.value.bubbles,
            currentTurnId,
            assistantBufferId,
        ).firstOrNull { it.role.equals("user", ignoreCase = true) } ?: return null
        val storedText = candidate.content?.trim().orEmpty()
        if (storedText != live.content.trim()) return null
        return baseline
    }

    /** Client id of the outbound item this phone is currently sending. */
    private fun foregroundOutboundId(): String? =
        outboundChatQueue.items(currentSessionId()).firstOrNull { item ->
            item.status == OutboundQueueStatus.Sending || item.status == OutboundQueueStatus.Running
        }?.id
            ?: outboundChatQueue.items(currentSessionId()).lastOrNull { item ->
                item.status == OutboundQueueStatus.Accepted || item.status == OutboundQueueStatus.ServerQueued
            }?.id

    private fun publishHistory(
        sessionId: String,
        offline: Boolean,
        activity: String,
        historyBubbles: List<ChatBubble>,
        preserveLiveTurn: Boolean = false,
    ) {
        val bubbles = historyBubbles
        val confirmedIds = confirmedOutbound(sessionId, historyMessages)
        confirmedIds.forEach { id ->
            outboundChatQueue.remove(id)?.let(::removePersistedOutbound)
        }
        val pendingItems = outboundChatQueue.items(sessionId)
            .filter { item -> item.id !in confirmedIds }
        val pendingBubbles = pendingOutboundBubbles(sessionId, confirmedIds)
        val running = activeRuns[sessionId]?.isNotEmpty() == true
        _ui.update { current ->
            val live = if (preserveLiveTurn && running) {
                com.luckyagent.android.ui.screens.dedupeLiveTurn(
                    bubbles,
                    com.luckyagent.android.ui.screens.liveTurnBubbles(current.bubbles, currentTurnId, assistantBufferId),
                    turnUserOrdinal,
                )
            } else {
                emptyList()
            }
            // A pending outbox bubble and the live user bubble are one message.
            val shown = (bubbles + live + pendingBubbles).distinctBy { it.id }
            current.copy(
                bubbles = shown,
                outboundQueue = pendingItems,
                activityLine = if (running && !offline) "正在回复" else activity,
                isResponding = running,
                historyLoading = false,
                historyRefreshing = false,
                historyLoadingMore = false,
                historyHasMore = historyStartIndex > 0,
                showingOfflineCache = offline,
                progressSteps = if (preserveLiveTurn && running) current.progressSteps else emptyList(),
                liveSummary = if (preserveLiveTurn && running) current.liveSummary else null,
                processNotice = if (preserveLiveTurn && running) current.processNotice else null,
                pendingQuote = null,
            )
        }
    }

    /**
     * Outbox rows the server already stored, by request id or a later identical text.
     */
    private fun confirmedOutbound(sessionId: String, messages: List<ProviderMessage>): Set<String> =
        confirmedOutboundIds(outboundChatQueue.items(sessionId), messages)

    private fun pendingOutboundBubbles(
        sessionId: String,
        excludedIds: Set<String> = emptySet(),
    ): List<ChatBubble> = outboundChatQueue.items(sessionId)
        .filter { item -> item.id !in excludedIds }
        .map { item ->
            ChatBubble(
                id = item.id,
                role = "user",
                content = item.preview.ifBlank { item.message },
                createdAt = java.time.Instant.ofEpochMilli(item.createdAt).toString(),
                attachments = item.attachments.map(::ChatMedia),
                outboundId = item.id,
                outboundStatus = item.status,
                outboundError = item.error,
            )
        }

    private suspend fun persistHistoryPage(
        apiBase: String,
        apiKey: String,
        sessionId: String,
        title: String?,
        updatedAt: String?,
        page: MessagePage,
        stored: MessagePage? = null,
    ) {
        val known = _ui.value.sessions.firstOrNull { it.id == sessionId }
        runCatching {
            container.sessionCache.savePage(
                apiBase = apiBase,
                sessionId = sessionId,
                apiKey = apiKey,
                title = title ?: known?.title,
                updatedAt = updatedAt,
                createdAt = known?.createdAt,
                page = page,
                opened = true,
                stored = stored,
            )
        }
    }

    /**
     * The history API only serves full bodies for a whole page. Fetch that page
     * and swap the one truncated row. Other preview rows stay preview, and only
     * the complete body is written back into the cache.
     */
    fun expandTruncatedMessage(bubble: ChatBubble) {
        val index = bubble.historyIndex
        if (!bubble.contentTruncated || index < 0 || expandingHistoryIndex == index) return
        val local = index - historyStartIndex
        if (local !in historyMessages.indices || !historyMessages[local].contentTruncated) return
        val expected = historyMessages[local]
        val sessionId = historySessionId ?: return
        val total = currentHistoryPage().messageCount
        // History offset skips the newest N messages. Walk back from the session
        // end in page-sized steps until this row is inside the fetched page.
        val pageEnd = total - ((total - 1 - index) / SessionCachePolicy.LATEST_PAGE) * SessionCachePolicy.LATEST_PAGE
        val offset = (total - pageEnd).coerceAtLeast(0)
        val limit = (pageEnd - (pageEnd - SessionCachePolicy.LATEST_PAGE).coerceAtLeast(0)).coerceIn(1, SessionCachePolicy.LATEST_PAGE)
        expandingHistoryIndex = index
        _ui.update { it.copy(expandingHistoryIndex = index, activityLine = "正在加载全文") }
        viewModelScope.launch {
            try {
                val connection = container.settingsRepository.snapshot()
                val fetch = container.api.sessionHistory(
                    sessionId,
                    limit = limit,
                    offset = offset,
                    includeFull = true,
                ).getOrElse { error ->
                    _ui.update { it.copy(activityLine = "全文加载失败 · ${error.message}") }
                    return@launch
                }
                val history = fetch.history ?: run {
                    _ui.update { it.copy(activityLine = "服务端未返回完整正文") }
                    return@launch
                }
                if (history.contentTruncated == true) {
                    _ui.update { it.copy(activityLine = "服务端未返回完整正文") }
                    return@launch
                }
                if (currentSessionId() != sessionId || historySessionId != sessionId) return@launch
                val remoteCount = history.messageCount ?: currentHistoryPage().messageCount
                if (remoteCount != total) {
                    _ui.update { it.copy(activityLine = "会话已更新，请重新展开") }
                    return@launch
                }
                val pageStart = remoteCount - offset - history.messages.size
                val full = history.messages.getOrNull(index - pageStart) ?: run {
                    _ui.update { it.copy(activityLine = "服务端未返回完整正文") }
                    return@launch
                }
                if (!sameExpandedRow(expected, full)) {
                    _ui.update { it.copy(activityLine = "会话已更新，请重新展开") }
                    return@launch
                }
                val slot = index - historyStartIndex
                if (slot !in historyMessages.indices || historyMessages[slot] != expected) return@launch
                val updated = historyMessages.toMutableList()
                val clientCapped = full.content.orEmpty().length > MAX_UI_MESSAGE_CHARS
                updated[slot] = full.copy(contentTruncated = clientCapped)
                val page = currentHistoryPage().copy(
                    messages = updated,
                    messageCount = remoteCount,
                    contentTruncated = updated.any { it.contentTruncated },
                )
                historyMessages = page.messages
                val stored = runCatching {
                    container.sessionCache.loadPage(connection.apiBase, sessionId, connection.apiKey)
                }.getOrNull()
                persistHistoryPage(
                    connection.apiBase,
                    connection.apiKey,
                    sessionId,
                    null,
                    // Expanding one row does not validate the rest of the
                    // snapshot against a newer session-list timestamp.
                    null,
                    page,
                    stored = stored,
                )
                if (currentSessionId() != sessionId || historySessionId != sessionId) return@launch
                applyHistoryPage(sessionId, page, offline = false)
                _ui.update { it.copy(activityLine = "已展开全文") }
            } finally {
                if (expandingHistoryIndex == index) {
                    expandingHistoryIndex = -1
                    _ui.update { state ->
                        if (state.expandingHistoryIndex == index) state.copy(expandingHistoryIndex = -1) else state
                    }
                }
            }
        }
    }

    /** The fetched row is the one the user tapped, not a neighbor shifted by a new message. */
    private fun sameExpandedRow(expected: ProviderMessage, fetched: ProviderMessage): Boolean {
        if (expected.role != fetched.role) return false
        if (expected.createdAt != fetched.createdAt) return false
        val expectedTool = expected.toolCallId?.trim().orEmpty()
        val fetchedTool = fetched.toolCallId?.trim().orEmpty()
        if (expectedTool.isNotEmpty() || fetchedTool.isNotEmpty()) return expectedTool == fetchedTool
        val preview = expected.content.orEmpty()
        val full = fetched.content.orEmpty()
        val marker = "\n...[truncated]"
        if (!preview.endsWith(marker)) return preview == full
        return full.startsWith(preview.removeSuffix(marker))
    }

    private fun historyLoadedLine(sessionId: String): String {
        val running = activeRuns[sessionId]?.isNotEmpty() == true
        return if (running) "正在回复" else "已读到这轮对话"
    }

    private fun historyToBubbles(messages: List<ProviderMessage>, startIndex: Int = 0): List<ChatBubble> =
        projectHistoryBubbles(messages, startIndex) { message, index ->
            message.toBubbles(index)
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
        return toHistoryBubbles(
            idx = idx,
            messageAttachments = messageAttachments,
            compactLabel = if (role == "system" && looksLikeCompactTrace(base)) compactTraceLabel(base) else null,
        )
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
        if (_ui.value.settings.connectionMode == "standalone") {
            sendStandalone()
            return
        }
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

    fun saveStandaloneSettings(apiBase: String, apiKey: String, model: String) {
        updateSettings {
            it.copy(
                connectionMode = "standalone",
                standaloneApiBase = apiBase,
                standaloneApiKey = apiKey,
                standaloneModel = model,
            )
        }
        checkStandalone()
    }

    fun useRuntimeConnection() {
        updateSettings { it.copy(connectionMode = "runtime") }
        refreshSessions()
    }

    fun useStandaloneConnection() {
        updateSettings { it.copy(connectionMode = "standalone") }
        showStandaloneSession()
    }

    fun compactStandalone() {
        val settings = container.settingsRepository.snapshot()
        val session = container.standaloneStore.load(settings.standaloneSessionId)
        val compacted = compactStandaloneSession(session, keepRecentTokens = 800)
        if (compacted == null) {
            _ui.update { it.copy(standaloneNotice = "还没有足够的旧消息可以压缩") }
            return
        }
        container.standaloneStore.save(compacted)
        showStandaloneSession(compacted)
        _ui.update { it.copy(standaloneNotice = "已压缩更早的对话，最近几条还在") }
    }

    private fun checkStandalone() {
        val settings = standaloneSettings()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            container.standalone.check(settings).onSuccess {
                _ui.update { it.copy(standaloneNotice = "模型已连通", healthOk = true) }
            }.onFailure { error ->
                _ui.update { it.copy(standaloneNotice = error.message ?: "模型没有连通", healthOk = false) }
            }
        }
    }

    private fun sendStandalone() {
        val text = _ui.value.composer.trim()
        if (text.isEmpty()) return
        val settings = standaloneSettings()
        if (settings.apiBase.isBlank() || settings.apiKey.isBlank()) {
            _ui.update { it.copy(standaloneNotice = "先在设置里填写模型 Key 和 URL") }
            return
        }
        val sessionId = settings.sessionId
        val stored = container.standaloneStore.load(sessionId)
        val searchQuery = text.removePrefix("/search").trim().takeIf { text.startsWith("/search") && it.isNotBlank() }
        val userBubble = ChatBubble(
            id = "su-${System.currentTimeMillis()}",
            role = "user",
            content = text,
            createdAt = nowIsoTimestamp(),
        )
        pushBubble(userBubble)
        _ui.update { it.copy(composer = "", isResponding = true, standaloneNotice = if (searchQuery != null) "正在搜索" else "正在回复") }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var session = stored.copy(messages = stored.messages + StandaloneMessage("user", text))
            val search = searchQuery?.let { query ->
                container.standalone.search(query).fold(
                    onSuccess = { result ->
                        session = session.copy(messages = session.messages + StandaloneMessage("assistant", result, kind = "search"))
                        result
                    },
                    onFailure = { error ->
                        val notice = "这次没有拿到外部结果：${error.message ?: "搜索失败"}"
                        session = session.copy(messages = session.messages + StandaloneMessage("assistant", notice, kind = "search"))
                        notice
                    },
                )
            }
            val prompt = buildStandaloneMessages(session, if (search != null) "请结合这次搜索结果回答：\n$search\n\n用户问题：$text" else text)
            val reply = container.standalone.complete(settings, prompt).fold(
                onSuccess = { it },
                onFailure = { error ->
                    container.standaloneStore.save(session)
                    showStandaloneSession(session)
                    _ui.update { it.copy(isResponding = false, standaloneNotice = error.message ?: "模型请求失败，已有消息还在") }
                    return@launch
                },
            )
            session = session.copy(
                title = session.title.ifBlank { text.take(24) },
                messages = session.messages + StandaloneMessage("assistant", reply),
            )
            val compacted = if (standaloneContextTokens(session) > 3000) compactStandaloneSession(session) else null
            val saved = compacted ?: session
            container.standaloneStore.save(saved)
            showStandaloneSession(saved)
            _ui.update {
                it.copy(
                    isResponding = false,
                    standaloneNotice = if (compacted != null) "上下文已自动压缩" else null,
                )
            }
        }
    }

    private fun showStandaloneSession(session: StandaloneSession? = null) {
        val settings = container.settingsRepository.snapshot()
        val loaded = session ?: container.standaloneStore.load(settings.standaloneSessionId)
        val bubbles = loaded.messages.mapIndexed { index, message ->
            ChatBubble(
                id = "standalone-$index-${message.content.hashCode()}",
                role = if (message.kind == "search") "search" else message.role,
                content = message.content,
            )
        }
        _ui.update { it.copy(bubbles = bubbles, standaloneTokens = standaloneContextTokens(loaded), historyLoading = false) }
    }

    private fun standaloneSettings(): StandaloneSettings {
        val settings = container.settingsRepository.snapshot()
        return StandaloneSettings(
            apiBase = settings.standaloneApiBase,
            apiKey = settings.standaloneApiKey,
            model = settings.standaloneModel,
            sessionId = settings.standaloneSessionId,
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
        val item = OutboundQueueItem(
            id = outboundId,
            sessionId = sessionId,
            preview = preview,
            message = message,
            attachments = descriptors,
            // The dispatcher promotes exactly one queued item to Sending.
            status = OutboundQueueStatus.Queued,
        )
        outboundChatQueue.add(item)
        val persisted = persistOutbound(item)
        if (!wasRunning) {
            currentTurnId = "turn-${System.currentTimeMillis()}"
            turnSendBaseline = confirmedTurnBaseline()
            turnUserOrdinal = null
            toolStepIndex.clear()
            resetAssistantStream()
        }
        if (showUserBubble) {
            pushBubble(
                ChatBubble(
                    id = outboundId,
                    role = "user",
                    content = displayText.ifBlank { if (descriptors.isNotEmpty()) "请查看附件" else message },
                    createdAt = java.time.Instant.ofEpochMilli(item.createdAt).toString(),
                    attachments = media,
                    quote = quote,
                    outboundId = outboundId,
                    outboundStatus = item.status,
                ),
            )
            // The new turn gets its own status box now. Without it the status of
            // this turn paints into the previous turn's fold.
            if (!wasRunning) openTurnStatusBox()
        }
        _ui.update {
            it.copy(
                composer = if (clearComposer) "" else it.composer,
                pendingMedia = if (clearComposer) emptyList() else it.pendingMedia,
                pendingQuote = if (clearComposer) null else it.pendingQuote,
                isResponding = true,
                liveSummary = null,
                processNotice = null,
                outboundQueue = outboundChatQueue.items(sessionId),
                activityLine = if (wasRunning) {
                    "消息已排队 · 当前队列 ${outboundChatQueue.items(sessionId).count { item -> item.status == OutboundQueueStatus.Sending || item.status == OutboundQueueStatus.Queued }} 条"
                } else {
                    it.activityLine
                },
                progressSteps = listOf(ChatProgressStep("phase-thinking", "Thinking through the request", ProgressStatus.Active)),
            )
        }
        // Only the dispatcher sends, and only after this row is durable. A
        // process death before that replays the same id instead of losing it.
        persisted.invokeOnCompletion { cause ->
            if (cause == null) dispatchNextOutbound(sessionId)
        }
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
        viewModelScope.launch {
            if (!container.wsClient.awaitConnected(segment.sessionId)) {
                markLuckySegmentFailed(segment.id, container.wsClient.lastError.value ?: "连接超时，没有送进收集，点重试")
                return@launch
            }
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
        container.wsClient.connect(segment.sessionId, force = true)
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
            viewModelScope.launch {
                if (!container.wsClient.awaitConnected(sessionId) || !container.wsClient.sendLucky(sessionId, action)) {
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
            _ui.update { it.copy(activityLine = "正在回复。等这一轮结束，或发送 /stop。") }
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
                activityLine = "正在执行 /${command.name}",
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
                activityLine = if (success) "已完成 /$command" else "/$command 失败。重试这一条。",
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
                activityLine = "已取消。可以重新发送这一条。",
                isResponding = false,
                liveSummary = null,
                processNotice = "已取消",
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
        if (hasData) {
            merged.filter { it.origin == TaskOrigin.Unified }.forEach { task ->
                if (task.status.lowercase() in setOf("pending", "queued", "running", "in_progress", "in-progress", "blocked")) {
                    AgentTaskWork.enqueue(container.appContext, task.id, task.executionSession())
                }
                else AgentTaskWork.cancel(container.appContext, task.id)
            }
        }
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
        viewModelScope.launch {
            // Notification taps can cold-start the process before the task
            // polling loop has populated the list. Refresh once so the deep
            // link is actionable instead of immediately reporting “not found”.
            if (_ui.value.tasks.none { it.id == id }) {
                refreshTasksNow()
            }
            loadTaskDetailNow(id, showLoading = false)
        }
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
        // Historical tool references can outlive the recent-task polling window.
        val summary = _ui.value.tasks.firstOrNull { it.id == id }
            ?: container.api.getTaskRecord(id).getOrNull()?.toTaskSummary()
        if (summary == null) {
            _ui.update { if (it.selectedTaskId != id) it else it.copy(taskDetailLoading = false, taskDetailError = "没找到这个任务。回到聊天后重试这一条。") }
            return
        }
        val detailResult = if (summary.origin == TaskOrigin.Legacy) {
            container.api.getLegacyTask(id).map { legacy ->
                TaskDetail(
                    root = legacy.toTaskNode(),
                    result = legacy.result,
                    messages = container.api.listAgentMessages(id).getOrDefault(emptyList()),
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
                val blackboard = container.api.listBlackboard(id).getOrDefault(emptyList())
                val messages = container.api.listAgentMessages(id).getOrDefault(emptyList())
                val plannerTrace = container.api.getTaskTrace(id).getOrNull()
                tree.map { node ->
                    TaskDetail(
                        root = node.toTaskNode(result),
                        events = events,
                        result = result,
                        blackboard = blackboard,
                        messages = messages,
                        plannerTrace = plannerTrace,
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

    /** Cancels the task behind the pinned chat card. Detail pages stay closed. */
    fun cancelWorkCard(taskId: String) {
        val task = _ui.value.tasks.firstOrNull { it.id == taskId }
        if (task == null) {
            _ui.update { it.copy(snackbarMessage = "列表里已经没有这条，没法从卡片取消。") }
            return
        }
        cancelTask(task, surface = CancelSurface.Card)
    }

    fun cancelTask(task: TaskSummary) {
        cancelTask(task, surface = CancelSurface.Detail)
    }

    private fun cancelTask(task: TaskSummary, surface: CancelSurface) {
        if (task.status.isTerminalTaskStatus()) return
        viewModelScope.launch {
            _ui.update { it.copy(taskDetailError = null) }
            val result = container.api.cancelTask(task.id, task.origin)
            if (result.isSuccess) {
                refreshTasksNow()
                if (surface == CancelSurface.Card) {
                    _ui.update { it.copy(snackbarMessage = "已取消") }
                }
            } else {
                val message = if (surface == CancelSurface.Card) "没能取消。重试这一条。" else result.exceptionOrNull()?.message ?: "没能取消。重试这一条。"
                _ui.update {
                    if (surface == CancelSurface.Card) it.copy(snackbarMessage = message)
                    else it.copy(taskDetailError = message)
                }
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
        if (result.isSuccess && dashboard != null) {
            dashboard.tasks.forEach { task ->
                when (task.state.lowercase()) {
                    "ready", "in_progress", "blocked" -> AgentTaskWork.enqueue(
                        container.appContext,
                        task.id,
                        task.executionSession(),
                        AgentTaskWork.ORIGIN_AUTONOMY,
                    )
                    else -> AgentTaskWork.cancel(
                        container.appContext,
                        task.id,
                        AgentTaskWork.ORIGIN_AUTONOMY,
                    )
                }
            }
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
                historyMessageCount = 0
                // The server just created this session, so zero is a real count.
                historyCountConfirmed = true
                container.settingsRepository.update { it.copy(sessionId = session.id) }
                foregroundRequestIds.remove(session.id)
                resetAssistantStream()
                toolStepIndex.clear()
                _ui.update {
                    it.copy(
                        bubbles = emptyList(),
                        historyLoading = false,
                        historyRefreshing = false,
                        historyLoadingMore = false,
                        historyHasMore = false,
                        isResponding = activeRuns[session.id]?.isNotEmpty() == true,
                        outboundQueue = outboundChatQueue.items(session.id),
                        activityLine = "已新建会话",
                        pendingQuote = null,
                        contextInspect = null,
                        contextInspectError = null,
                    )
                }
                connectSocket()
                refreshSessions()
                refreshContextInspect()
            }.onFailure { e ->
                _ui.update { it.copy(activityLine = "没能新建会话。重试这一条。") }
            }
        }
    }

    fun renameSession(id: String, title: String) {
        val trimmed = title.trim()
        if (id.isBlank() || trimmed.isEmpty()) return
        viewModelScope.launch {
            val result = container.api.renameSession(id, trimmed)
            result.onSuccess {
                _ui.update { it.copy(activityLine = "已重命名") }
                refreshSessions()
            }.onFailure { e ->
                _ui.update { it.copy(activityLine = "没能重命名。重试这一条。") }
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
                            activityLine = if (pinned) "已置顶" else "已取消置顶",
                        )
                    }
                    refreshSessions()
                }
                .onFailure { e ->
                    _ui.update { it.copy(activityLine = "没能改置顶。重试这一条。") }
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
                            activityLine = if (trimmed.isEmpty()) "已清除项目分类" else "已保存项目分类",
                        )
                    }
                    refreshSessions()
                }
                .onFailure { e ->
                    _ui.update { it.copy(activityLine = "没能保存项目分类。重试这一条。") }
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
                            historyStartIndex = 0
                            historyMessageCount = 0
                            historyCountConfirmed = false
                            container.settingsRepository.update { it.copy(sessionId = "") }
                            _ui.update {
                                it.copy(
                                    bubbles = emptyList(),
                                    historyLoading = false,
                                    historyRefreshing = false,
                                    historyHasMore = false,
                                    activityLine = "已删除会话",
                                )
                            }
                        }
                    }
                    _ui.update {
                        it.copy(
                            sessions = remaining,
                            activityLine = "已删除会话",
                        )
                    }
                    refreshSessions()
                }
                .onFailure { e ->
                    _ui.update { it.copy(activityLine = "没能删除会话。重试这一条。") }
                }
        }
    }

    private fun pluginConnectionMatches(connection: ClientSettings): Boolean {
        val current = container.settingsRepository.snapshot()
        return current.apiBase == connection.apiBase && current.apiKey == connection.apiKey && current.useBearer == connection.useBearer
    }

    fun refreshPlugins() {
        if (_ui.value.pluginManager.operation != null) return
        pluginsLoadJob?.cancel()
        val connection = container.settingsRepository.snapshot()
        _ui.update { it.copy(pluginManager = it.pluginManager.copy(loading = true, loadError = null)) }
        pluginsLoadJob = viewModelScope.launch {
            val result = container.api.listPlugins()
            if (!pluginConnectionMatches(connection)) return@launch
            _ui.update { current ->
                current.copy(pluginManager = result.fold(
                    onSuccess = { current.pluginManager.copy(plugins = it.plugins, loaded = true, loading = false, loadError = null) },
                    onFailure = { current.pluginManager.copy(loaded = true, loading = false, loadError = it.message ?: "读取插件失败") },
                ))
            }
        }
    }

    fun installPlugin(source: String, ref: String, subdir: String, trust: Boolean) {
        runPluginOperation("install") {
            container.api.installPlugin(PluginInstallRequest(source.trim(), ref.trim(), subdir.trim(), trust))
        }
    }

    fun setPluginEnabled(name: String, enabled: Boolean) {
        runPluginOperation(if (enabled) "enable:$name" else "disable:$name") { container.api.setPluginEnabled(name, enabled) }
    }

    fun updatePlugin(name: String, trust: Boolean) {
        runPluginOperation("update:$name") { container.api.updatePlugin(name, trust) }
    }

    fun removePlugin(name: String) {
        runPluginOperation("remove:$name") { container.api.removePlugin(name) }
    }

    fun clearPluginOperationError() {
        _ui.update { it.copy(pluginManager = it.pluginManager.copy(operationError = null)) }
    }

    private fun runPluginOperation(action: String, operation: suspend () -> Result<PluginChangeResponse>) {
        if (_ui.value.pluginManager.operation != null) return
        pluginsLoadJob?.cancel()
        val connection = container.settingsRepository.snapshot()
        _ui.update { it.copy(pluginManager = it.pluginManager.copy(operation = action, operationError = null, loading = false, message = null)) }
        pluginsMutationJob = viewModelScope.launch {
            val result = try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (!pluginConnectionMatches(connection)) return@launch
            result.onSuccess { changed ->
                _ui.update { current ->
                    val previous = current.pluginManager
                    val plugins = previous.plugins.filterNot { it.name == changed.name }.toMutableList()
                    changed.plugin?.let(plugins::add)
                    val label = when (changed.action) {
                        "install" -> "已安装"
                        "update" -> "已更新"
                        "enable" -> "已设为启用"
                        "disable" -> "已设为停用"
                        "remove" -> "已卸载"
                        else -> "已保存"
                    }
                    current.copy(pluginManager = previous.copy(
                        plugins = plugins.sortedBy { it.name }, operation = null,
                        message = "$label ${changed.name}。${changed.message}",
                        restartRequired = previous.restartRequired || changed.runtimeRestartRequired,
                        changeCount = previous.changeCount + 1,
                    ))
                }
                refreshPlugins()
            }.onFailure { error ->
                _ui.update { it.copy(pluginManager = it.pluginManager.copy(operation = null, operationError = error.message ?: "插件操作失败")) }
                // A lost response can arrive after the server saved the change.
                // Refresh state without automatically repeating a mutation.
                refreshPlugins()
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
                        activityLine = "已读到技能",
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
                        activityLine = "已读到渠道",
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

    fun startGateway(platform: String, token: String? = null) {
        val name = platform.trim().lowercase()
        if (name.isBlank() || _ui.value.gatewayAction != null) return
        _ui.update { it.copy(gatewayAction = name, gatewayActionError = null, gatewaysError = null) }
        viewModelScope.launch {
            val result = container.api.startGateway(name, token)
            if (result.isSuccess) {
                _ui.update {
                    it.copy(
                        gatewayAction = null,
                        gatewayActionError = null,
                        activityLine = "渠道已启动",
                        snackbarMessage = "$name 已启动",
                    )
                }
                refreshGateways()
            } else {
                _ui.update {
                    it.copy(
                        gatewayAction = null,
                        gatewayActionError = result.exceptionOrNull()?.message ?: "没能启动 $name。重试这一条。",
                        activityLine = "渠道没能启动。重试这一条。",
                    )
                }
            }
        }
    }

    fun stopGateway(platform: String) {
        val name = platform.trim().lowercase()
        if (name.isBlank() || _ui.value.gatewayAction != null) return
        _ui.update { it.copy(gatewayAction = name, gatewayActionError = null, gatewaysError = null) }
        viewModelScope.launch {
            val result = container.api.stopGateway(name)
            if (result.isSuccess) {
                _ui.update {
                    it.copy(
                        gatewayAction = null,
                        gatewayActionError = null,
                        activityLine = "渠道已停止",
                        snackbarMessage = "$name 已停止",
                    )
                }
                refreshGateways()
            } else {
                _ui.update {
                    it.copy(
                        gatewayAction = null,
                        gatewayActionError = result.exceptionOrNull()?.message ?: "没能停止 $name。重试这一条。",
                        activityLine = "渠道没能停止。重试这一条。",
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
                        trajectoryError = "先回聊天打开一个会话。",
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
                        activityLine = "已读到本轮轨迹",
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
