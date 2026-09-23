package com.luckyagent.android.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
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
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.ChatMedia
import com.luckyagent.android.ui.PendingMedia
import com.luckyagent.android.ui.components.MarkdownText
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.LocalOpenNavigationDrawer
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverAssistantBubble
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import com.luckyagent.android.ui.theme.CloverUserBubble
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val visualPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            vm.addPickedMedia(context.contentResolver, uris)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            vm.addPickedMedia(context.contentResolver, uris)
        }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val usePermanentSessionPane = LocalConfiguration.current.screenWidthDp >= 900
    var sessionPaneExpanded by rememberSaveable { mutableStateOf(true) }
    var renameTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (showAttachmentOptions) {
        AlertDialog(
            onDismissRequest = { showAttachmentOptions = false },
            title = { Text("添加附件") },
            text = {
                Column {
                    TextButton(onClick = {
                        showAttachmentOptions = false
                        visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }) { Text("照片和视频") }
                    TextButton(onClick = {
                        showAttachmentOptions = false
                        filePicker.launch(arrayOf("*/*"))
                    }) { Text("浏览文件") }
                }
            },
            confirmButton = {},
        )
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
                onRequestAttachment = { showAttachmentOptions = true },
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
                onRequestAttachment = { showAttachmentOptions = true },
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
    onRequestAttachment: () -> Unit,
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
        val timeline = remember(state.bubbles) { buildTimeline(state.bubbles) }
        var lastAutoScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(timeline.size, timeline.lastOrNull()) {
            if (timeline.isEmpty() || listState.isScrollInProgress) return@LaunchedEffect
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
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

        ComposerBar(
            state = state,
            onChange = vm::updateComposer,
            onSend = vm::sendComposer,
            onStop = vm::cancelRun,
            onAttach = onRequestAttachment,
            onRemoveMedia = vm::removePendingMedia,
        )
    }
}

@Composable
private fun ChatTopBar(state: AppUiState, onMenu: () -> Unit, showMenu: Boolean) {
    val openNavigation = LocalOpenNavigationDrawer.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(CloverSurface)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Outlined.Menu, contentDescription = "Sessions")
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
        val live = state.socketState == SocketState.Connected || state.socketState == SocketState.Running
        MetaChip(if (live) "live" else state.socketState.name.lowercase())
        if (state.isResponding || state.commandExecuting) {
            Spacer(Modifier.width(6.dp))
            MetaChip("working")
        }
        if (openNavigation != null) {
            IconButton(onClick = openNavigation) {
                Icon(Icons.Outlined.AccountCircle, contentDescription = "Profile and navigation")
            }
        }
    }
    if (state.progressSteps.isEmpty()) state.activityLine?.let {
        Text(
            it,
            color = CloverText2,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(CloverSurface2)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CloverSurface2)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            else -> {
                val bg = if (isUser) CloverUserBubble else CloverAssistantBubble
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(bg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    } else if (bubble.streaming) {
                        Text("…", color = CloverText3)
                    }
                    if (bubble.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        bubble.attachments.forEach { media ->
                            ChatMediaPreview(
                                media = media,
                                imageHeaders = imageHeaders,
                                imageBaseUrl = imageBaseUrl,
                                onDownload = onDownload,
                            )
                        }
                    }
                    if (bubble.streaming) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                            .background(CloverLeaf),
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
private fun MessageMetadata(bubble: ChatBubble) {
    var expanded by remember(bubble.id) { mutableStateOf(false) }
    val usage = bubble.usage
    val summary = listOfNotNull(
        bubble.createdAt?.let(::formatMessageTime),
        usage?.totalTokens?.takeIf { it > 0 }?.let { "${compactTokenCount(it)} tokens" },
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
                Text("输入 ${usage.inputTokens}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                Text("输出 ${usage.outputTokens}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                Text("总计 ${usage.totalTokens}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
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

private fun compactTokenCount(tokens: Int): String {
    if (tokens < 1000) return tokens.toString()
    val value = tokens / 1000.0
    return String.format(Locale.US, "%.1fk", value).removeSuffix(".0k")
}

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
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(
                when {
                    bubble.toolSuccess == false -> CloverError
                    !bubble.toolDone -> CloverAccent
                    else -> CloverLeaf
                },
            ),
        )
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
    onAttach: () -> Unit,
    onRemoveMedia: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HorizontalDivider(color = CloverLine.copy(alpha = .7f), modifier = Modifier.padding(bottom = 10.dp))
        if (state.pendingMedia.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pendingMedia.forEach { media -> PendingMediaChip(media, onRemove = { onRemoveMedia(media.id) }) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = onAttach, modifier = Modifier.padding(end = 2.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "添加附件", tint = CloverText2)
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
                    .clip(RoundedCornerShape(22.dp))
                    .background(CloverSurface)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    if (state.composer.isEmpty()) {
                        Text("Message or /command", color = CloverText3)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            val chatWorking = state.isResponding || state.bubbles.any { it.streaming }
            val hasInput = state.composer.isNotBlank() || state.pendingMedia.isNotEmpty()
            val isStopCommand = state.composer.trim().equals("/stop", ignoreCase = true)
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
private fun ChatMediaPreview(
    media: ChatMedia,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
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

    when {
        kind == "image" && sourceUri != null && imageModel != null -> {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .widthIn(max = 420.dp)
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showImageViewer = true },
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = descriptor.fileName ?: "图片附件",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AttachmentDownloadButton(media, onDownload)
                }
            }
        }
        kind == "image" && sourceUri != null && media.localUri != null -> {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .widthIn(max = 420.dp)
                        .heightIn(max = 280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showImageViewer = true },
                ) {
                    AsyncImage(
                        model = sourceUri,
                        contentDescription = descriptor.fileName ?: "图片附件",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AttachmentDownloadButton(media, onDownload)
                }
            }
        }
        (kind == "audio" || kind == "video") && player != null -> {
            AndroidView(
                factory = { PlayerView(it) },
                update = { view ->
                    view.player = player
                    view.useController = true
                    view.resizeMode = if (kind == "video") {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (kind == "video") 230.dp else 76.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            AttachmentDownloadButton(media, onDownload)
        }
        else -> AttachmentFileCard(media, kind, onDownload)
    }

    if (showImageViewer && sourceUri != null) {
        FullscreenImagePreview(
            model = imageModel ?: sourceUri,
            name = descriptor.fileName ?: "图片附件",
            onDismiss = { showImageViewer = false },
            onDownload = if (!descriptor.fileUrl.isNullOrBlank()) {
                { onDownload(media) }
            } else {
                null
            },
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
private fun AttachmentDownloadButton(media: ChatMedia, onDownload: (ChatMedia) -> Unit) {
    if (!media.descriptor.fileUrl.isNullOrBlank()) {
        IconButton(onClick = { onDownload(media) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = "下载附件", tint = CloverText2)
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
            .padding(start = 10.dp, end = 2.dp),
    ) {
        Text(
            text = "${mediaTypeLabel(kind)} · ${descriptor.fileName ?: "附件"}",
            style = MaterialTheme.typography.bodySmall,
            color = CloverText2,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        AttachmentDownloadButton(media, onDownload)
    }
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
            .widthIn(max = 190.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface2)
            .padding(start = 6.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
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
