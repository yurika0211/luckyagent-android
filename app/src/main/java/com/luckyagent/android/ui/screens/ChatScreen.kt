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
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
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
import com.luckyagent.android.data.api.TokenUsage
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
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
import kotlinx.coroutines.launch
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
fun ChatScreen(state: AppUiState, vm: AppViewModel) {
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
    val usePermanentSessionPane = LocalConfiguration.current.screenWidthDp >= 900
    var sessionPaneExpanded by rememberSaveable { mutableStateOf(true) }
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
            showClose = showClose,
        )
    }

    if (usePermanentSessionPane) {
        Row(Modifier.fillMaxSize().background(CloverBg)) {
            if (sessionPaneExpanded) {
                Column(
                    Modifier
                        .width(304.dp)
                        .fillMaxHeight()
                        .background(CloverBgSide),
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
                    modifier = Modifier.width(320.dp),
                ) {
                    sessionDrawer(true)
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
    val imageHeaders = remember(state.settings.apiKey, state.settings.useBearer) {
        if (state.settings.apiKey.isBlank()) emptyMap()
        else if (state.settings.useBearer) mapOf("Authorization" to "Bearer ${state.settings.apiKey}")
        else mapOf("X-API-Key" to state.settings.apiKey)
    }
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
        )

        val listState = rememberLazyListState()
        val scrollScope = rememberCoroutineScope()
        val timeline = remember(state.bubbles) { buildTimeline(state.bubbles) }
        var lastAutoScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var restoredSession by remember { mutableStateOf("") }
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
                items(timeline, key = { it.key }) { item ->
                    when (item) {
                        is ChatTimelineItem.Message -> BubbleRow(
                            bubble = item.bubble,
                            imageHeaders = imageHeaders,
                            imageBaseUrl = state.settings.apiBase,
                            onDownload = { media -> vm.downloadAttachment(context, media) },
                        )
                        is ChatTimelineItem.Process -> ProcessTimeline(
                            steps = item.steps,
                            isResponding = state.isResponding,
                            imageHeaders = imageHeaders,
                            imageBaseUrl = state.settings.apiBase,
                            onDownload = { media -> vm.downloadAttachment(context, media) },
                        )
                    }
                }
                item(key = "chat-tail") { Spacer(Modifier.height(1.dp)) }
            }
            if (timeline.size > 1) {
                // Mini scroll rail: compact pill so it doesn't dominate the bubble area.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CloverSurface.copy(alpha = .82f))
                        .border(0.5.dp, CloverLine.copy(alpha = .45f), RoundedCornerShape(999.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 22.dp, height = 20.dp)
                            .clickable { scrollScope.launch { listState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ExpandLess,
                            contentDescription = "到顶",
                            modifier = Modifier.size(13.dp),
                            tint = CloverText3,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 22.dp, height = 20.dp)
                            .clickable { scrollScope.launch { listState.animateScrollToItem(timeline.size) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "到底",
                            modifier = Modifier.size(13.dp),
                            tint = CloverText3,
                        )
                    }
                }
            }
        }

        ComposerBar(
            state = state,
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
            isRecordingVoice = isRecordingVoice,
            recordingElapsedSec = recordingElapsedSec,
            captureHint = captureHint,
            onStopVoice = onStopVoice,
            onCancelVoice = onCancelVoice,
        )
    }
}

@Composable
private fun ChatTopBar(state: AppUiState, onMenu: () -> Unit, showMenu: Boolean) {
    val openNavigation = LocalOpenNavigationDrawer.current
    val live = state.socketState == SocketState.Connected || state.socketState == SocketState.Running
    val connectionLabel = if (live) "live" else state.socketState.name.lowercase()
    val connectionColor = if (live) CloverLeaf else CloverError
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
                Text("Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sid = state.settings.sessionId
                Text(
                    if (sid.isBlank()) "No session" else sid.take(18) + if (sid.length > 18) "…" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(connectionColor))
                Text(connectionLabel, style = MaterialTheme.typography.labelSmall, color = CloverText3)
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
) {
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
                    if (!isUser) {
                        Text(
                            bubble.role.ifBlank { "assistant" },
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (bubble.content.isNotBlank()) {
                        if (bubble.streaming) {
                            Text(bubble.content, style = MaterialTheme.typography.bodyLarge)
                        } else {
                            MarkdownText(
                                markdown = bubble.content,
                                imageHeaders = imageHeaders,
                                imageBaseUrl = imageBaseUrl,
                            )
                        }
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
                    if (bubble.createdAt != null || bubble.usage != null) {
                        MessageMetadata(bubble)
                    }
                }
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
            Text(summary, style = MaterialTheme.typography.labelSmall, color = CloverText3)
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
private fun ProcessTimeline(
    steps: List<ChatBubble>,
    isResponding: Boolean,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
) {
    var expanded by remember(steps.first().id) { mutableStateOf(isResponding) }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isResponding && steps.any { !it.toolDone && it.role == "tool" }) "执行中"
                else "思考过程 · ${steps.size} 步",
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
        HorizontalDivider(color = CloverLine.copy(alpha = .65f))
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                steps.forEach { step ->
                    if (step.role == "reasoning") {
                        ReasoningPart(step, imageHeaders, imageBaseUrl)
                    } else {
                        ToolPart(step, imageHeaders, imageBaseUrl, onDownload)
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
) {
    var expanded by remember(bubble.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp),
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
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
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
                MonoBlock(it)
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

@Composable
private fun ComposerBar(
    state: AppUiState,
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
    isRecordingVoice: Boolean,
    recordingElapsedSec: Int,
    captureHint: String?,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
) {
    val chatWorking = state.isResponding || state.bubbles.any { it.streaming }
    val activityWorking = chatWorking || state.commandExecuting
    val hasInput = state.composer.isNotBlank() || state.pendingMedia.isNotEmpty()
    val isStopCommand = state.composer.trim().equals("/stop", ignoreCase = true)
    val attachmentActivity = state.activityLine?.takeIf {
        it.contains("下载") || it.contains("附件")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverBg)
        .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HorizontalDivider(color = CloverLine.copy(alpha = .7f), modifier = Modifier.padding(bottom = 10.dp))
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
                state.pendingMedia.forEach { media -> PendingMediaChip(media, onRemove = { onRemoveMedia(media.id) }) }
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
        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onToggleAttachment, modifier = Modifier.padding(end = 2.dp)) {
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
                    .heightIn(min = 48.dp, max = 140.dp)
                    .padding(horizontal = 4.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    if (state.composer.isEmpty()) {
                        Text("Message or /command", color = CloverText3)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            if (chatWorking) {
                if (isStopCommand) {
                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(CloverAccent),
                    ) {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = "Stop current run",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
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
                        Icons.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (hasInput) MaterialTheme.colorScheme.onPrimary else CloverText3,
                    )
                }
            }
        }
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
private fun PendingMediaChip(media: PendingMedia, onRemove: () -> Unit) {
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
            media.error != null -> Icon(Icons.Outlined.ErrorOutline, contentDescription = "上传失败", tint = CloverError, modifier = Modifier.size(17.dp))
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
private fun SessionDrawer(
    state: AppUiState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRename: (RuntimeSession) -> Unit,
    onRefresh: () -> Unit,
    showClose: Boolean,
) {
    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh sessions")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.sessions, key = { it.id }) { session ->
                val selected = session.id == state.settings.sessionId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) CloverUserBubble else CloverSurface)
                        .border(1.dp, if (selected) CloverAccent else CloverLine, RoundedCornerShape(12.dp))
                        .clickable { onSelect(session.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.title?.takeIf { it.isNotBlank() } ?: session.id,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            session.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRename(session) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = CloverText2)
                    }
                }
            }
        }
    }
}
