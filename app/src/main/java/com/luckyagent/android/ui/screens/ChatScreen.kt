@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.luckyagent.android.ui.screens

import android.content.Context
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.luckyagent.android.ui.MinMainContentWidth
import com.luckyagent.android.ui.AppNavRailWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.PendingApproval
import com.luckyagent.android.data.api.FunctionalModelKinds
import com.luckyagent.android.data.api.ModelRef
import com.luckyagent.android.data.api.modelKindLabel
import com.luckyagent.android.data.api.modelsByKind
import com.luckyagent.android.data.api.ContextBucketUsage
import com.luckyagent.android.data.api.ContextInspectResponse
import com.luckyagent.android.data.api.ContextSection
import com.luckyagent.android.data.api.TokenUsage
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.OutboundQueueItem
import com.luckyagent.android.ui.OutboundQueueStatus
import com.luckyagent.android.ui.util.TokenFormat
import com.luckyagent.android.ui.ChatMedia
import com.luckyagent.android.ui.PendingMedia
import com.luckyagent.android.ui.components.MarkdownText
import com.luckyagent.android.ui.components.LocalOpenNavigationDrawer
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import com.luckyagent.android.ui.theme.CloverUserBubble
import com.luckyagent.android.ui.theme.CloverWarning
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.outlined.Mic
import androidx.core.content.ContextCompat
import com.luckyagent.android.data.media.CaptureMediaStore
import com.luckyagent.android.data.media.CaptureTarget
import com.luckyagent.android.data.media.VoiceRecorder
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import com.luckyagent.android.ui.util.MessageQuote
import com.luckyagent.android.ui.util.quotePreview
import com.luckyagent.android.ui.util.quoteRoleLabel

private sealed interface ChatTimelineItem {
    val key: String

    data class Message(val bubble: ChatBubble) : ChatTimelineItem {
        override val key = bubble.id
    }

    data class Process(val steps: List<ChatBubble>) : ChatTimelineItem {
        override val key = steps.first().id
    }
}

private fun buildTimeline(bubbles: List<ChatBubble>): List<ChatTimelineItem> {
    val result = mutableListOf<ChatTimelineItem>()
    val steps = mutableListOf<ChatBubble>()
    fun flushSteps() {
        if (steps.isNotEmpty()) {
            result += ChatTimelineItem.Process(steps.toList())
            steps.clear()
        }
    }

    bubbles.forEach { bubble ->
        if (bubble.role == "reasoning" || bubble.role == "tool" || bubble.toolName != null) {
            steps += bubble
        } else {
            flushSteps()
            result += ChatTimelineItem.Message(bubble)
        }
    }
    flushSteps()
    return result
}

@Composable
fun ChatScreen(
    state: AppUiState,
    vm: AppViewModel,
    railOccupied: Boolean = false,
) {
    val context = LocalContext.current
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var pendingCameraTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var pendingVoiceTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedSec by remember { mutableStateOf(0) }
    var captureHint by remember { mutableStateOf<String?>(null) }
    val voiceRecorder = remember { VoiceRecorder() }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val visualPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.addPickedMedia(context.contentResolver, uris)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.addPickedMedia(context.contentResolver, uris)
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = pendingCameraTarget
        pendingCameraTarget = null
        if (target == null) return@rememberLauncherForActivityResult
        if (success && target.file.exists() && target.file.length() > 0L) {
            vm.addPickedMedia(context.contentResolver, listOf(target.uri))
            captureHint = null
        } else {
            target.file.delete()
            captureHint = "拍照取消或失败"
        }
    }

    fun launchCameraCapture() {
        if (isRecordingVoice) {
            captureHint = "请先结束录音"
            return
        }
        val target = CaptureMediaStore.newPhotoTarget(context)
        pendingCameraTarget = target
        runCatching { takePictureLauncher.launch(target.uri) }.onFailure {
            pendingCameraTarget = null
            target.file.delete()
            captureHint = it.message ?: "无法打开相机"
        }
    }

    fun startVoiceCapture() {
        if (isRecordingVoice) return
        val target = CaptureMediaStore.newVoiceTarget(context)
        runCatching {
            voiceRecorder.start(context, target.file)
            pendingVoiceTarget = target
            isRecordingVoice = true
            recordingStartedAtMs = System.currentTimeMillis()
            recordingElapsedSec = 0
            captureHint = null
        }.onFailure {
            target.file.delete()
            pendingVoiceTarget = null
            isRecordingVoice = false
            recordingStartedAtMs = null
            captureHint = it.message ?: "无法开始录音"
        }
    }

    fun stopVoiceCapture(cancel: Boolean) {
        if (!isRecordingVoice && pendingVoiceTarget == null) return
        val target = pendingVoiceTarget
        pendingVoiceTarget = null
        isRecordingVoice = false
        recordingStartedAtMs = null
        recordingElapsedSec = 0
        if (cancel) {
            voiceRecorder.cancel()
            target?.file?.delete()
            captureHint = "已取消录音"
            return
        }
        val file = voiceRecorder.stop()
        if (file != null && target != null) {
            vm.addPickedMedia(context.contentResolver, listOf(target.uri))
            captureHint = null
        } else {
            target?.file?.delete()
            captureHint = "录音太短或失败"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture() else captureHint = "需要相机权限才能拍照"
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceCapture() else captureHint = "需要麦克风权限才能录音"
    }

    fun requestCameraCapture() {
        if (hasPermission(Manifest.permission.CAMERA)) launchCameraCapture()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun requestVoiceCapture() {
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) startVoiceCapture()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var sessionPaneExpanded by rememberSaveable { mutableStateOf(true) }
    val sessionPaneWidth = 304.dp
    val sessionDrawerWidth = 320.dp
    var renameTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(isRecordingVoice, recordingStartedAtMs) {
        val started = recordingStartedAtMs ?: return@LaunchedEffect
        if (!isRecordingVoice) return@LaunchedEffect
        while (true) {
            recordingElapsedSec = ((System.currentTimeMillis() - started) / 1000L).toInt().coerceAtLeast(0)
            kotlinx.coroutines.delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (voiceRecorder.isRecording) {
                voiceRecorder.cancel()
            }
        }
    }

    LaunchedEffect(captureHint) {
        val hint = captureHint ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        if (captureHint == hint) captureHint = null
    }
    LaunchedEffect(state.settings.sessionId) {
        if (state.contextInspect == null && !state.contextInspectLoading && state.contextInspectError == null) {
            vm.refreshContextInspect()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(CloverBg)) {
        // Fixed session pane only when the remaining chat area stays usable.
        val usePermanentSessionPane =
            maxWidth >= 900.dp &&
                (maxWidth - sessionPaneWidth - (if (railOccupied) AppNavRailWidth else 0.dp)) >= MinMainContentWidth
        val drawerMaxWidth = minOf(sessionDrawerWidth, maxWidth * 0.86f)

        val sessionDrawer: @Composable (Boolean) -> Unit = { showClose ->
            SessionDrawer(
                state = state,
                onClose = {
                    if (usePermanentSessionPane) sessionPaneExpanded = false
                    else scope.launch { drawerState.close() }
                },
                onSelect = { id ->
                    vm.selectSession(id)
                    if (!usePermanentSessionPane) scope.launch { drawerState.close() }
                },
                onCreate = {
                    vm.createSession()
                    if (!usePermanentSessionPane) scope.launch { drawerState.close() }
                },
                onQueryChange = vm::updateSessionQuery,
                onRename = { session ->
                    renameTarget = session
                    renameText = session.title?.takeIf { it.isNotBlank() } ?: session.id
                },
                onRefresh = vm::refreshSessions,
                onCompact = { session -> vm.compactSession(session.id) },
                showClose = showClose,
            )
        }

        if (usePermanentSessionPane) {
            Row(Modifier.fillMaxSize()) {
                if (sessionPaneExpanded) {
                    Column(
                        Modifier
                            .width(sessionPaneWidth)
                            .widthIn(max = sessionPaneWidth)
                            .fillMaxHeight()
                            .background(CloverBgSide)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        sessionDrawer(true)
                    }
                }
                ChatConversation(
                    state = state,
                    vm = vm,
                    showSessionMenu = !sessionPaneExpanded,
                    onOpenSessions = { sessionPaneExpanded = true },
                    showAttachmentOptions = showAttachmentOptions,
                    onToggleAttachment = { showAttachmentOptions = !showAttachmentOptions },
                    onCameraCapture = {
                        showAttachmentOptions = false
                        requestCameraCapture()
                    },
                    onVoiceCapture = {
                        showAttachmentOptions = false
                        requestVoiceCapture()
                    },
                    onVisualPick = {
                        showAttachmentOptions = false
                        visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onFilePick = {
                        showAttachmentOptions = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    isRecordingVoice = isRecordingVoice,
                    recordingElapsedSec = recordingElapsedSec,
                    captureHint = captureHint,
                    onStopVoice = { stopVoiceCapture(cancel = false) },
                    onCancelVoice = { stopVoiceCapture(cancel = true) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = CloverBgSide,
                        modifier = Modifier
                            .widthIn(max = drawerMaxWidth)
                            .fillMaxHeight(),
                    ) {
                        Column(
                            Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        ) {
                            sessionDrawer(true)
                        }
                    }
                },
            ) {
                ChatConversation(
                    state = state,
                    vm = vm,
                    showSessionMenu = true,
                    onOpenSessions = { scope.launch { drawerState.open() } },
                    showAttachmentOptions = showAttachmentOptions,
                    onToggleAttachment = { showAttachmentOptions = !showAttachmentOptions },
                    onCameraCapture = {
                        showAttachmentOptions = false
                        requestCameraCapture()
                    },
                    onVoiceCapture = {
                        showAttachmentOptions = false
                        requestVoiceCapture()
                    },
                    onVisualPick = {
                        showAttachmentOptions = false
                        visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onFilePick = {
                        showAttachmentOptions = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    isRecordingVoice = isRecordingVoice,
                    recordingElapsedSec = recordingElapsedSec,
                    captureHint = captureHint,
                    onStopVoice = { stopVoiceCapture(cancel = false) },
                    onCancelVoice = { stopVoiceCapture(cancel = true) },
                )
            }
        }
    }

    renameTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameSession(session.id, renameText)
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChatConversation(
    state: AppUiState,
    vm: AppViewModel,
    showSessionMenu: Boolean,
    onOpenSessions: () -> Unit,
    showAttachmentOptions: Boolean,
    onToggleAttachment: () -> Unit,
    onCameraCapture: () -> Unit,
    onVoiceCapture: () -> Unit,
    onVisualPick: () -> Unit,
    onFilePick: () -> Unit,
    isRecordingVoice: Boolean,
    recordingElapsedSec: Int,
    captureHint: String?,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeSnackbar()
    }
    LaunchedEffect(state.attachmentNotice?.text, state.attachmentNotice?.openUri) {
        val notice = state.attachmentNotice ?: return@LaunchedEffect
        if (!notice.isComplete && !notice.isError) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = notice.text,
            actionLabel = notice.openUri?.let { "打开" },
        )
        if (result == SnackbarResult.ActionPerformed && notice.openUri != null) {
            vm.openDownloadedAttachment(context, notice.openUri, notice.mimeType)
        }
    }
    val imageHeaders = remember(state.settings.apiKey, state.settings.useBearer) {
        if (state.settings.apiKey.isBlank()) emptyMap()
        else if (state.settings.useBearer) mapOf("Authorization" to "Bearer ${state.settings.apiKey}")
        else mapOf("X-API-Key" to state.settings.apiKey)
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier
            .fillMaxSize()
            .background(CloverBg)
            .imePadding(),
    ) {
        ChatTopBar(
            state = state,
            onMenu = onOpenSessions,
            showMenu = showSessionMenu,
            onReconnect = vm::connectSocket,
        )

        val listState = rememberLazyListState()
        val scrollScope = rememberCoroutineScope()
        val timeline = remember(state.bubbles) { buildTimeline(state.bubbles) }
        val atBottom by remember {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisible == null ||
                    (lastVisible.index >= listState.layoutInfo.totalItemsCount - 1 &&
                        lastVisible.offset + lastVisible.size <= listState.layoutInfo.viewportEndOffset + 48)
            }
        }
        val unreadCount by remember(timeline) {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                (timeline.size - lastVisible - 1).coerceAtLeast(0)
            }
        }
        val latestHistoryState by rememberUpdatedState(state.historyHasMore to state.historyLoadingMore)
        var lastAutoScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var restoredSession by remember { mutableStateOf("") }
        LaunchedEffect(state.settings.sessionId) {
            var wasNearTop = false
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisible ->
                    val nearTop = firstVisible <= 2
                    val (hasMore, loadingMore) = latestHistoryState
                    if (nearTop && !wasNearTop && hasMore && !loadingMore) {
                        vm.loadMoreHistory()
                    }
                    wasNearTop = nearTop
                }
        }
        LaunchedEffect(state.settings.sessionId) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (restoredSession == state.settings.sessionId) {
                        vm.saveScrollAnchor(state.settings.sessionId, index, offset)
                    }
                }
        }
        LaunchedEffect(state.settings.sessionId, timeline.size) {
            if (timeline.isEmpty() || restoredSession == state.settings.sessionId) return@LaunchedEffect
            val anchor = vm.scrollAnchor(state.settings.sessionId)
            restoredSession = state.settings.sessionId
            if (anchor == null) {
                listState.scrollToItem(0)
                return@LaunchedEffect
            }
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            listState.scrollToItem(anchor.first.coerceIn(0, timeline.lastIndex), anchor.second)
        }
        LaunchedEffect(timeline.size, timeline.lastOrNull()) {
            if (timeline.isEmpty() || restoredSession != state.settings.sessionId || listState.isScrollInProgress) return@LaunchedEffect
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            val atBottom = lastVisible == null ||
                (lastVisible.index == layout.totalItemsCount - 1 && lastVisible.offset + lastVisible.size <= layout.viewportEndOffset + 48)
            val position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            if (atBottom || position == lastAutoScrollPosition) {
                listState.scrollToItem(timeline.size)
                lastAutoScrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = timeline,
                    key = { it.key },
                    contentType = { item ->
                        when (item) {
                            is ChatTimelineItem.Message -> "message"
                            is ChatTimelineItem.Process -> "process"
                        }
                    },
                ) { item ->
                    when (item) {
                        is ChatTimelineItem.Message -> BubbleRow(
                            bubble = item.bubble,
                            imageHeaders = imageHeaders,
                            imageBaseUrl = state.settings.apiBase,
                            onDownload = { media -> vm.downloadAttachment(context, media) },
                            onQuote = { vm.quoteMessage(it) },
                            onCopy = { bubble ->
                                val copied = vm.copyBubbleText(bubble)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("luckyagent-message", copied))
                                vm.notifyCopied()
                            },
                        )
                        is ChatTimelineItem.Process -> ProcessTimeline(
                            steps = item.steps,
                            isResponding = state.isResponding,
                            imageHeaders = imageHeaders,
                            imageBaseUrl = state.settings.apiBase,
                            onDownload = { media -> vm.downloadAttachment(context, media) },
                            onQuote = { vm.quoteMessage(it) },
                            onCopy = { bubble ->
                                val copied = vm.copyBubbleText(bubble)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("luckyagent-message", copied))
                                vm.notifyCopied()
                            },
                        )
                    }
                }
                if (state.pendingApprovals.isNotEmpty()) {
                    item(key = "pending-approvals") {
                        ApprovalCards(
                            approvals = state.pendingApprovals,
                            onDecision = vm::resolveApproval,
                        )
                    }
                }
                state.approvalsError?.takeIf { state.pendingApprovals.isEmpty() }?.let { error ->
                    item(key = "approvals-error") {
                        Text(error, color = CloverError, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item(key = "chat-tail") { Spacer(Modifier.height(1.dp)) }
            }
            if (state.historyLoading || state.historyLoadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            if (!atBottom && timeline.size > 1) {
                // Only show the return-to-bottom affordance after the user leaves the tail.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CloverSurface.copy(alpha = .96f))
                        .border(0.5.dp, CloverLine.copy(alpha = .45f), RoundedCornerShape(999.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 14.dp)
                            .clickable { scrollScope.launch { listState.animateScrollToItem(timeline.size) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "回到底部",
                            modifier = Modifier.size(16.dp),
                            tint = CloverText3,
                        )
                        if (unreadCount > 0) {
                            Text(
                                unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = CloverAccent,
                                modifier = Modifier.padding(start = 18.dp),
                            )
                        }
                    }
                }
            }
        }

        ComposerBar(
            state = state,
            vm = vm,
            onChange = vm::updateComposer,
            onSend = vm::sendComposer,
            onStop = vm::cancelRun,
            showAttachmentOptions = showAttachmentOptions,
            onToggleAttachment = onToggleAttachment,
            onCameraCapture = onCameraCapture,
            onVoiceCapture = onVoiceCapture,
            onVisualPick = onVisualPick,
            onFilePick = onFilePick,
            onRemoveMedia = vm::removePendingMedia,
            onRetryMedia = { id -> vm.retryPendingMedia(context.contentResolver, id) },
            onClearQuote = vm::clearQuote,
            isRecordingVoice = isRecordingVoice,
            recordingElapsedSec = recordingElapsedSec,
            captureHint = captureHint,
            onStopVoice = onStopVoice,
            onCancelVoice = onCancelVoice,
        )
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }
}

@Composable
private fun ChatTopBar(state: AppUiState, onMenu: () -> Unit, showMenu: Boolean, onReconnect: () -> Unit = {}) {
    val openNavigation = LocalOpenNavigationDrawer.current
    val live = state.socketState == SocketState.Connected || state.socketState == SocketState.Running
    val connectionLabel = if (live) "live" else state.socketState.name.lowercase()
    val connectionColor = if (live) CloverLeaf else CloverError
    val currentSession = state.sessions.firstOrNull { it.id == state.settings.sessionId }
    val sessionTitle = currentSession?.title?.takeIf { it.isNotBlank() } ?: "未命名会话"
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverBg),
    ) {
        HorizontalDivider(color = CloverLine.copy(alpha = .55f))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Sessions", tint = CloverText2)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    sessionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sid = state.settings.sessionId
                Text(
                    buildString {
                        if (sid.isBlank()) append("未选择会话")
                        else {
                            append(sid.take(18))
                            if (sid.length > 18) append("…")
                            (currentSession?.updatedAt ?: currentSession?.createdAt)?.let {
                                append(" · 活跃 ")
                                append(formatMessageTime(it))
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = if (!live) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onReconnect)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                },
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(connectionColor))
                Text(
                    if (!live) "点击重连" else connectionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!live) CloverError else CloverText3,
                )
            }
            if (openNavigation != null) {
                IconButton(onClick = openNavigation) {
                    Icon(Icons.Outlined.AccountCircle, contentDescription = "Profile and navigation", tint = CloverText2)
                }
            }
        }
        HorizontalDivider(color = CloverLine.copy(alpha = .55f))
    }
}

@Composable
private fun BubbleRow(
    bubble: ChatBubble,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
) {
    var menuExpanded by remember(bubble.id) { mutableStateOf(false) }
    val isUser = bubble.role.equals("user", ignoreCase = true)
    val isSystem = bubble.role.equals("system", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        when {
            isSystem -> {
                Text(
                    bubble.content,
                    color = CloverText3,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            else -> {
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    if (!isUser && !bubble.role.equals("assistant", ignoreCase = true)) {
                        Text(
                            bubble.role.ifBlank { "assistant" },
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Box {
                        Column(
                            Modifier
                                .pointerInput(bubble.id) {
                                    detectTapGestures(onLongPress = { menuExpanded = true })
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                        bubble.quote?.let { quote ->
                            QuoteCard(quote = quote, compact = true)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (bubble.content.isNotBlank()) {
                            MarkdownText(
                                markdown = bubble.content,
                                imageHeaders = imageHeaders,
                                imageBaseUrl = imageBaseUrl,
                                streaming = bubble.streaming,
                            )
                        }
                        if (bubble.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val images = bubble.attachments.filter { attachmentKind(it.descriptor) == "image" }
                            val otherMedia = bubble.attachments.filter { attachmentKind(it.descriptor) != "image" }
                            if (images.size > 1) {
                                ImageAttachmentGrid(images, imageHeaders, imageBaseUrl, onDownload)
                            } else {
                                images.forEach { media ->
                                    ChatMediaPreview(
                                        media = media,
                                        imageHeaders = imageHeaders,
                                        imageBaseUrl = imageBaseUrl,
                                        onDownload = onDownload,
                                    )
                                }
                            }
                            otherMedia.forEach { media ->
                                ChatMediaPreview(
                                    media = media,
                                    imageHeaders = imageHeaders,
                                    imageBaseUrl = imageBaseUrl,
                                    onDownload = onDownload,
                                )
                            }
                        }
                    }
                        MessageActionMenu(
                            expanded = menuExpanded,
                            onDismiss = { menuExpanded = false },
                            onQuote = { onQuote(bubble) },
                            onCopy = { onCopy(bubble) },
                        )
                    }
                    if (bubble.createdAt != null || bubble.usage != null) {
                        MessageMetadata(bubble)
                    }
                }
            }
        }
    }
}


@Composable
private fun MessageActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onCopy: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("引用") },
            onClick = {
                onQuote()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                onCopy()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
    }
}

@Composable
private fun QuoteCard(quote: MessageQuote, compact: Boolean = false, onClear: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface2)
            .border(0.5.dp, CloverLine.copy(alpha = .55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (compact) 28.dp else 34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CloverAccent),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "回复 ${quoteRoleLabel(quote.role)}",
                style = MaterialTheme.typography.labelSmall,
                color = CloverAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                quotePreview(quote.content, max = if (compact) 90 else 120),
                style = MaterialTheme.typography.bodySmall,
                color = CloverText2,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "取消引用",
                    tint = CloverText3,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ImageAttachmentGrid(
    media: List<ChatMedia>,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        media.chunked(3).forEach { rowMedia ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowMedia.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        ChatMediaPreview(
                            media = item,
                            imageHeaders = imageHeaders,
                            imageBaseUrl = imageBaseUrl,
                            onDownload = onDownload,
                            compact = true,
                        )
                    }
                }
                repeat(3 - rowMedia.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MessageMetadata(bubble: ChatBubble) {
    var expanded by remember(bubble.id) { mutableStateOf(false) }
    val usage = bubble.usage
    val summary = listOfNotNull(
        bubble.createdAt?.let(::formatMessageTime),
        usage?.let(::formatUsageSummary),
    ).joinToString(" · ")
    if (summary.isBlank()) return

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = usage != null) { expanded = !expanded },
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "运行详情 · $summary",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (expanded && usage != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text("输入 ${formatTokenCount(usage.inputTokens)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                if (usage.cachedInputTokens > 0) {
                    Text(
                        "缓存 ${formatTokenCount(usage.cachedInputTokens)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CloverText3,
                    )
                }
                Text("输出 ${formatTokenCount(usage.outputTokens)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                Text("总计 ${formatTokenCount(usage.totalTokens)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                usage.model?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatMessageTime(raw: String): String {
    return runCatching {
        val zone = ZoneId.systemDefault()
        val local = Instant.parse(raw).atZone(zone)
        val today = LocalDate.now(zone)
        if (local.toLocalDate() == today) {
            local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        } else {
            local.format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
        }
    }.getOrElse { raw.take(16).replace('T', ' ') }
}

private fun formatUsageSummary(usage: TokenUsage): String? = TokenFormat.usageSummary(usage)

private fun formatTokenCount(tokens: Int): String = TokenFormat.full(tokens)

private fun compactTokenCount(tokens: Int): String = TokenFormat.compact(tokens)

@Composable
private fun ApprovalCards(
    approvals: List<PendingApproval>,
    onDecision: (PendingApproval, String, String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        approvals.forEach { approval ->
            ApprovalCard(approval = approval, onDecision = onDecision)
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    onDecision: (PendingApproval, String, String) -> Unit,
) {
    val needsInput = approval.needsTextInput()
    var draft by rememberSaveable(approval.id) { mutableStateOf("") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloverSurface2,
        border = BorderStroke(1.dp, CloverAccent.copy(alpha = .45f)),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (needsInput) "需要你补充信息" else "需要审批",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    approval.provider.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverAccent,
                )
            }
            Text(
                if (needsInput) approval.inputPrompt() else (
                    approval.summary?.ifBlank { null }
                        ?: approval.method?.ifBlank { null }
                        ?: "外部工具请求权限"
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!needsInput) {
                approval.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = CloverText2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (needsInput) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    label = { Text("填写内容") },
                    placeholder = { Text("输入后点提交") },
                    minLines = 3,
                )
            }
            if (approval.options.isEmpty()) {
                Text("没有可用的审批选项", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                approval.options.forEach { option ->
                    val negative = option.kind.orEmpty().lowercase().let { kind ->
                        kind.contains("reject") || kind.contains("deny") || kind.contains("cancel")
                    }
                    val submit = option.kind.equals("submit", ignoreCase = true) || option.id.equals("submit", ignoreCase = true)
                    val enabled = !submit || draft.isNotBlank()
                    if (negative) {
                        OutlinedButton(
                            onClick = { onDecision(approval, option.id, "") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option.label) }
                    } else {
                        Button(
                            onClick = { onDecision(approval, option.id, if (needsInput) draft else "") },
                            enabled = enabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option.label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessTimeline(
    steps: List<ChatBubble>,
    isResponding: Boolean,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
) {
    var expanded by remember(steps.first().id) { mutableStateOf(isResponding) }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloverPulseIndicator(
                modifier = Modifier.size(12.dp),
                tint = if (isResponding) CloverLeaf else CloverText3,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isResponding && steps.any { !it.toolDone && it.role == "tool" } -> "执行中"
                    steps.size == 1 -> "思考过程"
                    else -> "思考过程 · ${steps.size} 步"
                },
                style = MaterialTheme.typography.labelMedium,
                color = CloverText3,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = CloverText3,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                steps.forEach { step ->
                    if (step.role == "reasoning") {
                        ReasoningPart(step, imageHeaders, imageBaseUrl)
                    } else {
                        ToolPart(step, imageHeaders, imageBaseUrl, onDownload, onQuote, onCopy)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningPart(
    bubble: ChatBubble,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
) {
    if (bubble.content.isBlank()) return
    Column(Modifier.fillMaxWidth()) {
        bubble.reasoningRound?.takeIf { it > 1 }?.let { round ->
            Text("第 $round 轮", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        MarkdownText(markdown = bubble.content, imageHeaders = imageHeaders, imageBaseUrl = imageBaseUrl)
    }
}

@Composable
private fun ToolPart(
    bubble: ChatBubble,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
) {
    var expanded by remember(bubble.id) { mutableStateOf(false) }
    var menuExpanded by remember(bubble.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    onLongClick = { menuExpanded = true },
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!bubble.toolDone && bubble.toolSuccess != false) {
                CloverPulseIndicator(Modifier.size(10.dp), CloverAccent)
            } else {
                Box(
                    Modifier.size(7.dp).clip(CircleShape).background(
                        if (bubble.toolSuccess == false) CloverError else CloverLeaf,
                    ),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                bubble.toolName ?: "工具调用",
                style = MaterialTheme.typography.bodySmall,
                color = CloverText2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    !bubble.toolDone -> "运行中"
                    bubble.toolSuccess == false -> "失败"
                    else -> "完成"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (bubble.toolSuccess == false) CloverError else CloverText3,
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起工具详情" else "展开工具详情",
                tint = CloverText3,
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
        MessageActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onQuote = { onQuote(bubble) },
            onCopy = { onCopy(bubble) },
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp)) {
            bubble.toolArgs?.takeIf { it.isNotBlank() }?.let {
                Text("输入", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                MonoBlock(it)
            }
            bubble.toolOutput?.takeIf { it.isNotBlank() }?.let {
                Text(if (bubble.toolSuccess == false) "错误" else "结果", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                MarkdownText(
                    markdown = truncateToolOutput(it),
                    color = CloverText2,
                    imageHeaders = imageHeaders,
                    imageBaseUrl = imageBaseUrl,
                )
            }
            bubble.attachments.forEach { media ->
                ChatMediaPreview(media, imageHeaders, imageBaseUrl, onDownload)
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        maxLines = 12,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Keep the existing compact tool preview while letting complete GFM tables render. */
private fun truncateToolOutput(text: String, maxLines: Int = 12): String {
    val lines = text.replace("\r\n", "\n").split('\n')
    if (lines.size <= maxLines) return text
    return lines.take(maxLines).joinToString("\n") + "\n…"
}

@Composable
private fun ComposerBar(
    state: AppUiState,
    vm: AppViewModel,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    showAttachmentOptions: Boolean,
    onToggleAttachment: () -> Unit,
    onCameraCapture: () -> Unit,
    onVoiceCapture: () -> Unit,
    onVisualPick: () -> Unit,
    onFilePick: () -> Unit,
    onRemoveMedia: (String) -> Unit,
    onRetryMedia: (String) -> Unit,
    onClearQuote: () -> Unit,
    isRecordingVoice: Boolean,
    recordingElapsedSec: Int,
    captureHint: String?,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
) {
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showContextSheet by rememberSaveable { mutableStateOf(false) }
    val chatWorking = state.isResponding || state.bubbles.any { it.streaming }
    val activityWorking = chatWorking || state.commandExecuting
    val hasInput = state.composer.isNotBlank() || state.pendingMedia.isNotEmpty() || state.pendingQuote != null
    val isStopCommand = state.composer.trim().equals("/stop", ignoreCase = true)
    val attachmentActivity = state.activityLine?.takeIf {
        it.contains("下载") || it.contains("附件")
    }
    LaunchedEffect(showModelSheet) {
        if (showModelSheet) vm.loadModels()
    }
    LaunchedEffect(showContextSheet) {
        if (showContextSheet) vm.refreshContextInspect()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HorizontalDivider(color = CloverLine.copy(alpha = .7f), modifier = Modifier.padding(bottom = 10.dp))
        state.pendingQuote?.let { quote ->
            QuoteCard(
                quote = quote,
                compact = false,
                onClear = onClearQuote,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!isRecordingVoice && !activityWorking && !attachmentActivity.isNullOrBlank()) {
            Text(
                attachmentActivity,
                color = if (attachmentActivity.contains("失败")) CloverError else CloverText3,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.attachmentNotice?.let { notice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when {
                    notice.isError -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = CloverError, modifier = Modifier.size(15.dp))
                    notice.isComplete -> Icon(Icons.Outlined.Check, contentDescription = null, tint = CloverLeaf, modifier = Modifier.size(15.dp))
                    else -> CloverPulseIndicator(Modifier.size(14.dp), CloverAccent)
                }
                Text(
                    notice.text,
                    color = when {
                        notice.isError -> CloverError
                        notice.isComplete -> CloverAccent
                        else -> CloverText3
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(visible = showAttachmentOptions && !isRecordingVoice) {
            Column {
                AttachmentActionRow(
                    onCameraCapture = onCameraCapture,
                    onVoiceCapture = onVoiceCapture,
                    onVisualPick = onVisualPick,
                    onFilePick = onFilePick,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (isRecordingVoice) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CloverPulseIndicator(tint = CloverError)
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = CloverError, modifier = Modifier.size(18.dp))
                Text(
                    text = "录音中 ${formatElapsed(recordingElapsedSec)}",
                    color = CloverText2,
                    style = MaterialTheme.typography.bodyMedium,
                )
                VoiceWaveform(Modifier.weight(1f))
                TextButton(onClick = onCancelVoice) { Text("取消") }
                TextButton(onClick = onStopVoice) { Text("完成") }
            }
            Spacer(Modifier.height(8.dp))
        } else if (!captureHint.isNullOrBlank()) {
            Text(
                text = captureHint,
                color = CloverError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        if (state.pendingMedia.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("附件 ${state.pendingMedia.size}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                val failed = state.pendingMedia.count { it.error != null }
                val uploading = state.pendingMedia.count { it.descriptor == null && it.error == null }
                Text(
                    when {
                        failed > 0 -> "$failed 个失败"
                        uploading > 0 -> "$uploading 个上传中"
                        else -> "已就绪"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed > 0) CloverError else CloverText3,
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pendingMedia.forEach { media ->
                    PendingMediaChip(media, onRemove = { onRemoveMedia(media.id) }, onRetry = { onRetryMedia(media.id) })
                }
            }
        }
        if (activityWorking) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CloverPulseIndicator()
                StreamingDots()
                Text(
                    if (state.commandExecuting) "执行中" else "生成中",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
        }
        LuckyCommandRow(
            active = state.luckyActive,
            segments = state.luckySegments,
            attachments = state.luckyAttachments,
            pending = state.luckyPending,
            onCommand = { action ->
                onChange(action)
                onSend()
            },
            onRetry = vm::retryLuckySegment,
        )
        if (state.outboundQueue.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "发送队列 · ${state.outboundQueue.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.outboundQueue.forEach { item ->
                        OutboundQueueChip(item = item, onRetry = { vm.retryOutboundMessage(item.id) })
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CloverSurface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, CloverLine),
            shadowElevation = 0.dp,
        ) {
        Row(
            Modifier
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleAttachment,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (showAttachmentOptions) Icons.Outlined.Close else Icons.Outlined.Add,
                    contentDescription = if (showAttachmentOptions) "关闭附件选项" else "添加附件",
                    tint = CloverText2,
                )
            }
            BasicTextField(
                value = state.composer,
                onValueChange = onChange,
                cursorBrush = SolidColor(CloverAccent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 140.dp)
                    .padding(horizontal = 2.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (state.composer.isEmpty()) {
                        Text(
                            if (state.luckyActive) "继续添加，/lucky off 一次发送" else "输入消息或 /command",
                            color = CloverText3,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            ContextUsageButton(
                state = state,
                onClick = { showContextSheet = true },
            )
            Spacer(Modifier.width(2.dp))
            // Model picker sits immediately left of send/stop, compact enough
            // not to reflow the placeholder.
            ModelSwitchButton(
                state = state,
                onClick = { showModelSheet = true },
                compact = true,
            )
            Spacer(Modifier.width(2.dp))
            if (chatWorking) {
                IconButton(
                    onClick = onSend,
                    enabled = hasInput,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(if (hasInput) CloverAccent else CloverSurface2),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = if (isStopCommand) "Stop current run" else "Send and queue",
                        tint = if (hasInput) MaterialTheme.colorScheme.onPrimary else CloverText3,
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(CloverSurface2),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = CloverError)
                }
            } else if (state.commandExecuting) {
                IconButton(onClick = {}, enabled = false, modifier = Modifier.padding(start = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = hasInput,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(if (hasInput) CloverAccent else CloverSurface2),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (hasInput) MaterialTheme.colorScheme.onPrimary else CloverText3,
                    )
                }
            }
        }
        }
        if (showContextSheet) {
            ContextInspectSheet(
                state = state,
                onDismiss = { showContextSheet = false },
                onRetry = vm::refreshContextInspect,
            )
        }
        if (showModelSheet) {
            ModelSwitchSheet(
                state = state,
                onDismiss = { showModelSheet = false },
                onRetry = { vm.loadModels(force = true) },
                onSelect = vm::switchModel,
            )
        }
    }
}

@Composable
private fun LuckyCommandRow(
    active: Boolean,
    segments: Int,
    attachments: Int,
    pending: List<com.luckyagent.android.ui.LuckyPendingSegment>,
    onCommand: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val waiting = pending.count { it.error == null }
    val failed = pending.count { it.error != null }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 7.dp)) {
        Text(
            when {
                !active && pending.isEmpty() -> "Lucky 未开启"
                failed > 0 -> "Lucky 收集中 · 已确认 $segments 段 · $failed 段未送进"
                waiting > 0 -> "Lucky 收集中 · 已确认 $segments 段 · $waiting 段发送中"
                else -> "Lucky 收集中 · $segments 段 · 附件 $attachments"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (failed > 0) CloverError else if (active) CloverAccent else CloverText3,
        )
        if (pending.isNotEmpty()) {
            pending.forEach { segment ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        segment.error ?: "发送中 · ${segment.preview}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (segment.error != null) CloverError else CloverText3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (segment.error != null) {
                        TextButton(
                            onClick = { onRetry(segment.id) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) { Text("重试") }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "/lucky on" to "开始",
                "/lucky off" to "提交",
                "/lucky status" to "状态",
                "/lucky cancel" to "放弃",
            ).forEach { (command, label) ->
                TextButton(
                    onClick = { onCommand(command) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("$label", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun OutboundQueueChip(
    item: OutboundQueueItem,
    onRetry: () -> Unit,
) {
    val (label, tint) = when (item.status) {
        OutboundQueueStatus.Sending -> "发送中" to CloverAccent
        OutboundQueueStatus.Queued -> "排队中" to CloverText3
        OutboundQueueStatus.Failed -> "失败" to CloverError
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = CloverSurface2,
        border = BorderStroke(1.dp, tint.copy(alpha = .4f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
            Text(
                item.preview,
                style = MaterialTheme.typography.labelSmall,
                color = CloverText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            if (item.status == OutboundQueueStatus.Failed) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

private val ContextBucketOrder = listOf(
    "system" to "系统",
    "history" to "历史",
    "memory" to "记忆",
    "rag" to "检索",
    "tool_result" to "工具",
    "user" to "用户",
)

private val ContextBucketColors = listOf(
    Color(0xFF356F42),
    Color(0xFF3D6B8C),
    Color(0xFF8A6A2F),
    Color(0xFF6E5A8A),
    Color(0xFF8C4E3A),
    Color(0xFF4E7A55),
)

@Composable
private fun ContextUsageButton(
    state: AppUiState,
    onClick: () -> Unit,
) {
    val usage = state.contextInspect?.usage
    val ratio = ((usage?.ratio ?: 0.0).toFloat()).coerceIn(0f, 1.5f)
    val tint = when {
        state.contextInspectError != null -> CloverError
        ratio >= 0.9f -> CloverError
        ratio >= 0.75f -> CloverWarning
        else -> CloverAccent
    }
    val label = when {
        usage == null -> "—"
        else -> TokenFormat.compact(usage.totalTokens)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (state.contextInspectLoading && usage == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CloverAccent,
                )
            } else {
                ContextUsageRing(ratio = ratio.coerceAtMost(1f), color = tint)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun ContextUsageRing(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        if (ratio > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
    }
}

@Composable
private fun ContextInspectSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val inspect = state.contextInspect
    val usage = inspect?.usage
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CloverBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前上下文", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("本地估算，不是供应商账单", style = MaterialTheme.typography.bodySmall, color = CloverText3)
                }
                IconButton(onClick = onRetry, enabled = !state.contextInspectLoading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新上下文", tint = CloverText2)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = CloverText2)
                }
            }
            if (state.contextInspectLoading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = CloverAccent,
                )
            }
            state.contextInspectError?.let { error ->
                Text(error, color = CloverError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (usage != null) {
                val ratioPct = (usage.ratio * 100).toInt().coerceAtLeast(0)
                Text(
                    "${TokenFormat.full(usage.totalTokens)} / ${TokenFormat.full(usage.availableTokens)} · $ratioPct%",
                    style = MaterialTheme.typography.titleMedium,
                    color = CloverText,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "剩余 ${TokenFormat.full(usage.headroomTokens)} · ${usage.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloverText3,
                )
                Spacer(Modifier.height(12.dp))
                ContextBucketOrder.forEachIndexed { index, (key, label) ->
                    ContextBucketRow(
                        label = label,
                        bucket = usage.buckets[key] ?: ContextBucketUsage(),
                        color = ContextBucketColors[index],
                        total = usage.totalTokens.coerceAtLeast(1),
                    )
                }
                if (inspect.sections.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("组成", style = MaterialTheme.typography.titleSmall, color = CloverText)
                    Spacer(Modifier.height(8.dp))
                    inspect.sections.forEach { section ->
                        ContextSectionRow(section)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContextBucketRow(
    label: String,
    bucket: ContextBucketUsage,
    color: Color,
    total: Int,
) {
    val fraction = (bucket.tokens.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = CloverText)
            Text(
                "${TokenFormat.full(bucket.tokens)} · ${bucket.messages}",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CloverLine),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ContextSectionRow(section: ContextSection) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${section.index + 1}. ${contextSectionLabel(section.label)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = CloverText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                TokenFormat.compact(section.tokens),
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
            )
        }
        if (section.preview.isNotBlank()) {
            Text(
                section.preview,
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun contextSectionLabel(label: String): String = when (label) {
    "system_prompt" -> "系统提示"
    "tool_catalog" -> "工具目录"
    "history" -> "历史"
    "compact_summary" -> "压缩摘要"
    "core_memory" -> "核心记忆"
    "working_memory" -> "工作记忆"
    "session_history_memory" -> "会话记忆"
    "recent_context" -> "近期上下文"
    "rag" -> "检索"
    "skill_route" -> "技能路由"
    "attachment" -> "附件"
    "tool_result" -> "工具结果"
    "user" -> "用户"
    else -> label.ifBlank { "其他" }
}

@Composable
private fun ModelSwitchButton(
    state: AppUiState,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val activeChat = state.activeModels["chat"]
        ?: state.models.firstOrNull { it.kind.equals("chat", ignoreCase = true) && it.current }
    val rawLabel = activeChat?.displayName?.takeIf { it.isNotBlank() } ?: activeChat?.id ?: "模型"
    // Keep the in-composer chip short so it stays left of send without crushing input.
    val label = if (compact) {
        rawLabel.substringAfterLast('/').let { short ->
            if (short.length <= 14) short else short.take(13) + "…"
        }
    } else {
        rawLabel
    }
    val switching = state.modelSwitchingKey?.startsWith("chat:") == true
    Surface(
        modifier = Modifier
            .widthIn(max = if (compact) 118.dp else 180.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = !switching, onClick = onClick),
        color = if (compact) CloverSurface2 else CloverSurface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, CloverLine.copy(alpha = 0.9f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 6.dp else 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (switching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = CloverAccent,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = CloverText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = "切换模型",
                tint = CloverText3,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
            )
        }
    }
}

@Composable
private fun ModelSwitchSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (ModelRef) -> Unit,
) {
    val grouped = remember(state.models) { modelsByKind(state.models) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CloverBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("模型", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("按当前 API Key 拉取可用模型，切换后保存", style = MaterialTheme.typography.bodySmall, color = CloverText3)
                }
                IconButton(onClick = onRetry, enabled = !state.modelsLoading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新模型列表", tint = CloverText2)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = CloverText2)
                }
            }
            if (state.modelsLoading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = CloverAccent,
                )
            }
            state.modelsError?.let { error ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = CloverError, modifier = Modifier.size(18.dp))
                    Text(
                        error,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = CloverError,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
            if (state.modelsLoaded && !state.modelsLoading && state.models.isEmpty() && state.modelsError == null) {
                Text(
                    "服务器未返回可用模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloverText3,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            FunctionalModelKinds.forEach { kind ->
                ModelKindSection(
                    kind = kind.wireValue,
                    models = grouped[kind.wireValue].orEmpty(),
                    active = state.activeModels[kind.wireValue],
                    switchingKey = state.modelSwitchingKey,
                    onSelect = onSelect,
                )
            }
            val extraKinds = grouped.keys.filterNot { key -> FunctionalModelKinds.any { it.wireValue == key } }.sorted()
            extraKinds.forEach { kind ->
                ModelKindSection(
                    kind = kind,
                    models = grouped[kind].orEmpty(),
                    active = state.activeModels[kind],
                    switchingKey = state.modelSwitchingKey,
                    onSelect = onSelect,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModelKindSection(
    kind: String,
    models: List<ModelRef>,
    active: ModelRef?,
    switchingKey: String?,
    onSelect: (ModelRef) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(modelKindLabel(kind), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                active?.id ?: "未配置",
                style = MaterialTheme.typography.labelSmall,
                color = if (active == null) CloverText3 else CloverAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (models.isEmpty()) {
            Text(
                "暂无可用模型",
                style = MaterialTheme.typography.bodySmall,
                color = CloverText3,
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
            )
        } else {
            models.forEach { model ->
                val isCurrent = active?.id == model.id || (active == null && model.current)
                ModelOptionRow(
                    model = model,
                    isCurrent = isCurrent,
                    enabled = switchingKey == null && !isCurrent,
                    switching = switchingKey == "${kind.lowercase()}|${model.id}",
                    onClick = { onSelect(model) },
                )
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    model: ModelRef,
    isCurrent: Boolean,
    enabled: Boolean,
    switching: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            switching -> CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = CloverAccent)
            isCurrent -> Icon(Icons.Outlined.Check, contentDescription = "当前", tint = CloverAccent, modifier = Modifier.size(17.dp))
            else -> Spacer(Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                model.displayName?.takeIf { it.isNotBlank() } ?: model.id,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled || isCurrent) CloverText else CloverText3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val provider = model.provider?.takeIf { it.isNotBlank() }
            if (provider != null && provider != model.id) {
                Text(provider, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1)
            }
        }
        if (isCurrent) Text("当前", style = MaterialTheme.typography.labelSmall, color = CloverAccent)
    }
}

@Composable
private fun AttachmentActionRow(
    onCameraCapture: () -> Unit,
    onVoiceCapture: () -> Unit,
    onVisualPick: () -> Unit,
    onFilePick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AttachmentAction(Icons.Outlined.CameraAlt, "拍照", onCameraCapture)
        AttachmentAction(Icons.Outlined.Mic, "录音", onVoiceCapture)
        AttachmentAction(Icons.Outlined.PhotoLibrary, "相册", onVisualPick)
        AttachmentAction(Icons.Outlined.AttachFile, "文件", onFilePick)
    }
}

@Composable
private fun AttachmentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = CloverText2, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}

@Composable
private fun CloverPulseIndicator(
    modifier: Modifier = Modifier,
    tint: Color = CloverLeaf,
) {
    val transition = rememberInfiniteTransition(label = "clover-pulse")
    val pulse = transition.animateFloat(
        initialValue = .65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "clover-alpha",
    ).value
    Canvas(modifier.size(14.dp)) {
        val radius = size.minDimension * .27f
        val x1 = size.width * .31f
        val x2 = size.width * .69f
        val y1 = size.height * .31f
        val y2 = size.height * .69f
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x1, y1))
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x2, y1))
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x1, y2))
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x2, y2))
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "streaming-dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha = transition.animateFloat(
                initialValue = .22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1200
                        .22f at 0
                        .22f at (index * 150)
                        1f at (index * 150 + 180)
                        .22f at (index * 150 + 430)
                        .22f at 1200
                    },
                    RepeatMode.Restart,
                ),
                label = "dot-$index",
            ).value
            Box(Modifier.size(4.dp).clip(CircleShape).background(CloverLeaf.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun VoiceWaveform(modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { index ->
            val transition = rememberInfiniteTransition(label = "voice-wave-$index")
            val level = transition.animateFloat(
                initialValue = .25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        .25f at 0
                        (if (index % 3 == 0) 1f else .55f) at 260
                        .25f at 520
                        .25f at 900
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "voice-bar-$index",
            ).value
            Box(
                Modifier
                    .width(2.dp)
                    .height((5f + level * 13f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CloverLeaf),
            )
        }
    }
}

@Composable
private fun ChatMediaPreview(
    media: ChatMedia,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    compact: Boolean = false,
) {
    val descriptor = media.descriptor
    val context = LocalContext.current
    val remoteUrl = descriptor.fileUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { resolveMediaUrl(it, imageBaseUrl) }
    val sourceUri = media.localUri?.let(Uri::parse) ?: remoteUrl?.let(Uri::parse)
    val kind = attachmentKind(descriptor)
    val imageModel = remoteUrl?.let { url ->
        remember(url, imageHeaders, imageBaseUrl) {
            ImageRequest.Builder(context)
                .data(url)
                .apply {
                    if (sameMediaOrigin(url, imageBaseUrl)) {
                        imageHeaders.forEach { (name, value) -> addHeader(name, value) }
                    }
                }
                .build()
        }
    }
    var showImageViewer by remember(sourceUri?.toString(), kind) { mutableStateOf(false) }
    var playbackError by remember(sourceUri?.toString(), descriptor.mimeType, kind) { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    val player = rememberAttachmentPlayer(
        context = context,
        sourceUri = sourceUri,
        mimeType = descriptor.mimeType,
        headers = if (remoteUrl != null && sameMediaOrigin(remoteUrl, imageBaseUrl)) imageHeaders else emptyMap(),
        enabled = (kind == "audio" || kind == "video") && sourceUri != null && !playbackError,
    )
    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    durationMs = player.duration.takeIf { it > 0 } ?: 0L
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = true
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    LaunchedEffect(player, sourceUri, descriptor.mimeType) {
        if (player == null || sourceUri == null) return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(sourceUri)
            .apply {
                descriptor.mimeType
                    ?.takeIf { it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true) }
                    ?.let(::setMimeType)
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0 } ?: durationMs
                isPlaying = player.isPlaying
            }
            kotlinx.coroutines.delay(250)
        }
    }

    when {
        kind == "image" && sourceUri != null && imageModel != null -> {
            ImageAttachmentCard(media, imageModel, compact, { showImageViewer = true }, onDownload)
        }
        kind == "image" && sourceUri != null && media.localUri != null -> {
            ImageAttachmentCard(media, sourceUri, compact, { showImageViewer = true }, onDownload)
        }
        kind == "audio" && player != null -> {
            AudioAttachmentCard(media, player, isPlaying, positionMs, durationMs, onDownload)
        }
        kind == "video" && player != null -> {
            VideoAttachmentCard(media, player, onDownload)
        }
        else -> AttachmentFileCard(media, kind, onDownload)
    }

    if (showImageViewer && sourceUri != null) {
        FullscreenImagePreview(
            model = imageModel ?: sourceUri,
            name = descriptor.fileName ?: "图片附件",
            onDismiss = { showImageViewer = false },
            onDownload = if (!descriptor.fileUrl.isNullOrBlank() || !descriptor.filePath.isNullOrBlank()) {
                {
                    onDownload(media)
                    showImageViewer = false
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun ImageAttachmentCard(
    media: ChatMedia,
    model: Any,
    compact: Boolean,
    onOpen: () -> Unit,
    onDownload: (ChatMedia) -> Unit,
) {
    val descriptor = media.descriptor
    Box(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .then(if (compact) Modifier.aspectRatio(1f) else Modifier.heightIn(min = 180.dp, max = 300.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .clickable(onClick = onOpen),
    ) {
        AsyncImage(
            model = model,
            contentDescription = descriptor.fileName ?: "图片附件",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = .48f))
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    descriptor.fileName ?: "图片附件",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                descriptor.fileSize?.takeIf { it > 0 }?.let {
                    Text(formatMediaSize(it), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                }
            }
            AttachmentDownloadButton(media, onDownload, tint = Color.White)
        }
    }
}

@Composable
private fun AudioAttachmentCard(
    media: ChatMedia,
    player: ExoPlayer,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onDownload: (ChatMedia) -> Unit,
) {
    val descriptor = media.descriptor
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CloverSurface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CloverAccent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "暂停音频" else "播放音频",
                        tint = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    descriptor.fileName ?: "音频附件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloverText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
            AttachmentDownloadButton(media, onDownload)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
            color = CloverAccent,
            trackColor = CloverLine,
        )
    }
}

@Composable
private fun VideoAttachmentCard(
    media: ChatMedia,
    player: ExoPlayer,
    onDownload: (ChatMedia) -> Unit,
) {
    val descriptor = media.descriptor
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CloverSurface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp, max = 260.dp)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { PlayerView(it) },
                update = { view ->
                    view.player = player
                    view.useController = true
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .38f))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    descriptor.fileName ?: "视频附件",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AttachmentDownloadButton(media, onDownload, tint = Color.White)
            }
        }
        Text(
            text = "视频 · ${descriptor.fileName ?: "附件"}",
            style = MaterialTheme.typography.labelMedium,
            color = CloverText2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberAttachmentPlayer(
    context: Context,
    sourceUri: Uri?,
    mimeType: String?,
    headers: Map<String, String>,
    enabled: Boolean,
): ExoPlayer? {
    if (!enabled || sourceUri == null) return null
    val player = remember(sourceUri.toString(), mimeType, headers) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@Composable
private fun FullscreenImagePreview(
    model: Any,
    name: String,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = model,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭预览", tint = Color.White)
            }
            if (onDownload != null) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = "下载附件", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AttachmentDownloadButton(media: ChatMedia, onDownload: (ChatMedia) -> Unit, tint: Color = CloverText2) {
    if (!media.descriptor.fileUrl.isNullOrBlank() || !media.descriptor.filePath.isNullOrBlank()) {
        IconButton(onClick = { onDownload(media) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = "下载附件", tint = tint)
        }
    }
}

@Composable
private fun AttachmentFileCard(media: ChatMedia, kind: String, onDownload: (ChatMedia) -> Unit) {
    val descriptor = media.descriptor
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CloverSurface2)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = descriptor.fileName ?: "附件",
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText2,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(mediaTypeLabel(kind), style = MaterialTheme.typography.labelSmall, color = CloverText3)
            AttachmentDownloadButton(media, onDownload)
        }
    }
}

private fun formatElapsed(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val min = safe / 60
    val sec = safe % 60
    return "%d:%02d".format(min, sec)
}

private fun formatMediaSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun attachmentKind(descriptor: com.luckyagent.android.data.api.MediaAttachment): String {
    val type = descriptor.type.lowercase()
    val mimeType = descriptor.mimeType.orEmpty().lowercase()
    return when {
        type == "image" || type.startsWith("image/") || mimeType.startsWith("image/") -> "image"
        type == "audio" || type.startsWith("audio/") || mimeType.startsWith("audio/") -> "audio"
        type == "video" || type.startsWith("video/") || mimeType.startsWith("video/") -> "video"
        else -> "document"
    }
}

private fun resolveMediaUrl(url: String, baseUrl: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (baseUrl.isBlank()) return url
    return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
}

private fun sameMediaOrigin(url: String, baseUrl: String): Boolean {
    val target = Uri.parse(url)
    val base = Uri.parse(baseUrl)
    val targetPort = target.port.takeIf { it >= 0 } ?: if (target.scheme.equals("https", true)) 443 else 80
    val basePort = base.port.takeIf { it >= 0 } ?: if (base.scheme.equals("https", true)) 443 else 80
    return target.scheme == base.scheme && target.host.equals(base.host, true) && targetPort == basePort
}

@Composable
private fun PendingMediaChip(media: PendingMedia, onRemove: () -> Unit, onRetry: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 230.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media.mimeType.startsWith("image/")) {
            AsyncImage(
                model = Uri.parse(media.uri),
                contentDescription = media.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(7.dp))
        } else {
            Icon(
                if (media.mimeType.startsWith("audio/")) Icons.Outlined.AudioFile else Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = CloverText2,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(media.fileName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                media.error ?: if (media.descriptor == null) "上传中…" else "已就绪",
                style = MaterialTheme.typography.labelSmall,
                color = if (media.error != null) CloverError else CloverText3,
                maxLines = 1,
            )
        }
        when {
            media.error != null -> IconButton(onClick = onRetry, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "重试上传", tint = CloverError, modifier = Modifier.size(17.dp))
            }
            media.descriptor == null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = CloverAccent)
            else -> Icon(Icons.Outlined.Check, contentDescription = "上传完成", tint = CloverLeaf, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "移除附件", modifier = Modifier.size(16.dp), tint = CloverText3)
        }
    }
}

private fun mediaTypeLabel(type: String): String = when (type.lowercase()) {
    "image" -> "图片"
    "video" -> "视频"
    "audio" -> "音频"
    else -> "文件"
}

@Composable
private fun SessionWorkingBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CloverAccent.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(CloverAccent),
        )
        Text(
            "Working",
            style = MaterialTheme.typography.labelSmall,
            color = CloverAccent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SessionDrawer(
    state: AppUiState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRename: (RuntimeSession) -> Unit,
    onRefresh: () -> Unit,
    onCompact: (RuntimeSession) -> Unit = {},
    showClose: Boolean,
) {
    val query = state.sessionQuery.trim()
    val sessions = state.sessions
    val showInitialLoading = state.sessionsLoading && sessions.isEmpty() && state.sessionsError == null
    val showEmpty = !state.sessionsLoading && state.sessionsError == null && sessions.isEmpty()

    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sessions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "New session")
            }
            if (showClose) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
        }
        HorizontalDivider(color = CloverLine)
        OutlinedTextField(
            value = state.sessionQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onRefresh() }),
            placeholder = { Text("Search sessions") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (state.sessionsLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh sessions")
                    }
                }
            },
        )

        if (state.sessionsError != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    state.sessionsError ?: "Failed to load sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onRefresh) {
                    Text("Retry")
                }
            }
        }

        when {
            showInitialLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CloverAccent, strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading sessions…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CloverText3,
                        )
                    }
                }
            }

            showEmpty -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (query.isNotEmpty()) {
                                "No sessions match \"$query\""
                            } else {
                                "No sessions yet"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = CloverText2,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isNotEmpty()) {
                                "Try another keyword, or clear the search."
                            } else {
                                "Create a session to start chatting."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CloverText3,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (query.isNotEmpty()) {
                            TextButton(onClick = {
                                onQueryChange("")
                                onRefresh()
                            }) {
                                Text("Clear search")
                            }
                        } else {
                            TextButton(onClick = onCreate) {
                                Text("New session")
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.sessionsLoading) {
                        item(key = "sessions-loading-bar") {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                color = CloverAccent,
                            )
                        }
                    }
                    items(sessions, key = { it.id }) { session ->
                        val selected = session.id == state.settings.sessionId
                        val working = session.id in state.workingSessionIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) CloverUserBubble else CloverSurface)
                                .border(
                                    1.dp,
                                    if (selected) CloverAccent else if (working) CloverAccent.copy(alpha = 0.45f) else CloverLine,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onSelect(session.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        session.title?.takeIf { it.isNotBlank() } ?: session.id,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (working) {
                                        Spacer(Modifier.width(8.dp))
                                        SessionWorkingBadge()
                                    }
                                    IconButton(onClick = { onCompact(session) }) {
                                        Icon(Icons.Outlined.Compress, contentDescription = "压缩上下文", tint = CloverText2)
                                    }
                                }
                                Text(
                                    session.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloverText3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                (session.updatedAt ?: session.createdAt)?.let { timestamp ->
                                    Text(
                                        buildString {
                                            append("活跃 ")
                                            append(formatMessageTime(timestamp))
                                            session.messageCount?.let { append(" · ${it} 条消息") }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CloverText3,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { onRename(session) }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = CloverText2)
                            }
                        }
                    }
                }
            }
        }
    }
}
