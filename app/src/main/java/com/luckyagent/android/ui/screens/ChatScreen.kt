package com.luckyagent.android.ui.screens

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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val usePermanentSessionPane = LocalConfiguration.current.screenWidthDp >= 900
    var sessionPaneExpanded by rememberSaveable { mutableStateOf(true) }
    var renameTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var renameText by remember { mutableStateOf("") }

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
    modifier: Modifier = Modifier,
) {
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
                    is ChatTimelineItem.Message -> BubbleRow(item.bubble)
                    is ChatTimelineItem.Process -> ProcessTimeline(item.steps, state.isResponding)
                }
            }
            item(key = "chat-tail") { Spacer(Modifier.height(1.dp)) }
        }

        ComposerBar(state = state, onChange = vm::updateComposer, onSend = vm::sendComposer, onStop = vm::cancelRun)
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
        if (state.isResponding) {
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
private fun BubbleRow(bubble: ChatBubble) {
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
                            MarkdownText(markdown = bubble.content)
                        }
                    } else if (bubble.streaming) {
                        Text("…", color = CloverText3)
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
                }
            }
        }
    }
}

@Composable
private fun ProcessTimeline(steps: List<ChatBubble>, isResponding: Boolean) {
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
                        ReasoningPart(step)
                    } else {
                        ToolPart(step)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningPart(bubble: ChatBubble) {
    if (bubble.content.isBlank()) return
    Column(Modifier.fillMaxWidth()) {
        bubble.reasoningRound?.takeIf { it > 1 }?.let { round ->
            Text("第 $round 轮", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        MarkdownText(markdown = bubble.content)
    }
}

@Composable
private fun ToolPart(bubble: ChatBubble) {
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
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HorizontalDivider(color = CloverLine.copy(alpha = .7f), modifier = Modifier.padding(bottom = 10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
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
                        Text("Message LuckyAgent…", color = CloverText3)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            val working = state.isResponding || state.bubbles.any { it.streaming }
            if (working) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(CloverSurface2),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = CloverError)
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = state.composer.isNotBlank(),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(if (state.composer.isNotBlank()) CloverAccent else CloverSurface2),
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (state.composer.isNotBlank()) MaterialTheme.colorScheme.onPrimary else CloverText3,
                    )
                }
            }
        }
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
