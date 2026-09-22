package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.components.MarkdownText
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import com.luckyagent.android.ui.theme.CloverUserBubble
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(state: AppUiState, vm: AppViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(state.bubbles.size, state.bubbles.lastOrNull()?.content) {
        if (state.bubbles.isNotEmpty()) {
            listState.animateScrollToItem(state.bubbles.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = CloverBgSide) {
                SessionDrawerContent(state = state, vm = vm)
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CloverBg),
        ) {
            ChatTopBar(
                state = state,
                onOpenSessions = { scope.launch { drawerState.open() } },
                onReload = {
                    vm.loadHistory()
                    vm.refreshSessions()
                },
                onReconnect = vm::connectSocket,
            )
            HorizontalDivider(color = CloverLine)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.bubbles, key = { it.id }) { bubble ->
                    MessageBubble(bubble)
                }
            }
            state.activityLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ComposerBar(
                value = state.composer,
                running = state.socketState == SocketState.Running,
                onChange = vm::updateComposer,
                onSend = vm::sendComposer,
                onCancel = vm::cancelRun,
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    state: AppUiState,
    onOpenSessions: () -> Unit,
    onReload: () -> Unit,
    onReconnect: () -> Unit,
) {
    val reconnectHint = state.reconnectInfo?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .background(CloverSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenSessions) {
            Icon(Icons.Outlined.History, contentDescription = "Sessions")
        }
        Column(Modifier.weight(1f)) {
            Text("LuckyAgent", style = MaterialTheme.typography.titleMedium, color = CloverText)
            Text(
                text = "${state.settings.sessionId} · ${state.socketState.name.lowercase()}$reconnectHint",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onReload) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Reload")
        }
        TextButton(onClick = onReconnect) { Text("WS") }
    }
}

@Composable
private fun MessageBubble(bubble: ChatBubble) {
    val isUser = bubble.role == "user"
    val isError = bubble.role == "error"
    val isTool = bubble.role == "tool"
    val isSystem = bubble.role == "system"
    val bg = when {
        isUser -> CloverUserBubble
        isError -> MaterialTheme.colorScheme.errorContainer
        isTool -> CloverBgSide
        isSystem -> CloverBgSide
        else -> CloverSurface
    }
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(1.dp, CloverLine, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = when {
                    isTool -> "tool"
                    bubble.streaming -> "assistant · streaming"
                    else -> bubble.role
                },
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
            )
            Spacer(Modifier.height(4.dp))
            when {
                isTool -> {
                    val status = when {
                        !bubble.toolDone -> "running"
                        bubble.toolSuccess == false -> "failed"
                        else -> "done"
                    }
                    Text(
                        text = "${bubble.toolName ?: "tool"} · $status",
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            bubble.toolSuccess == false -> MaterialTheme.colorScheme.error
                            bubble.toolDone -> CloverAccent
                            else -> CloverText
                        },
                    )
                    val args = bubble.toolArgs.orEmpty()
                    if (args.isNotBlank()) {
                        Text(
                            text = args,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = CloverText2,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .background(CloverSurface, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        )
                    }
                    val out = bubble.toolOutput.orEmpty()
                    if (out.isNotBlank()) {
                        Text(
                            text = out,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = CloverText,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                isError || isSystem || isUser -> {
                    Text(
                        text = bubble.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CloverText,
                    )
                }
                else -> {
                    MarkdownText(markdown = bubble.content.ifBlank { if (bubble.streaming) "…" else "" })
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    running: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CloverSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp, max = 140.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, CloverLine, RoundedCornerShape(14.dp))
                .background(CloverBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = CloverText),
                cursorBrush = SolidColor(CloverAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!running) onSend() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text("Message LuckyAgent…", color = CloverText3)
                    }
                    inner()
                },
            )
        }
        if (running) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = CloverAccent)
            }
        } else {
            IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(Icons.Outlined.Send, contentDescription = "Send", tint = CloverAccent)
            }
        }
    }
}

@Composable
private fun SessionDrawerContent(state: AppUiState, vm: AppViewModel) {
    Column(Modifier.padding(16.dp)) {
        Text("Sessions", style = MaterialTheme.typography.titleMedium, color = CloverText)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = state.sessionQuery,
            onValueChange = { vm.updateSessionQuery(it) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CloverLine, RoundedCornerShape(10.dp))
                .background(CloverSurface)
                .padding(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = CloverText),
            decorationBox = { inner ->
                if (state.sessionQuery.isEmpty()) {
                    Text("Search sessions", color = CloverText3, style = MaterialTheme.typography.bodyMedium)
                }
                inner()
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = vm::refreshSessions) { Text("Search") }
            TextButton(onClick = {
                vm.updateSessionQuery("")
                vm.refreshSessions()
            }) { Text("Clear") }
            TextButton(onClick = { vm.loadHistory() }) { Text("History") }
        }
        if (state.sessionsLoading) {
            Text("Loading…", color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        }
        state.sessionsError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        state.sessions.forEach { session ->
            val selected = session.id == state.settings.sessionId
            FilterChip(
                selected = selected,
                onClick = { vm.selectSession(session.id) },
                label = {
                    Column {
                        Text(
                            session.title?.ifBlank { session.id } ?: session.id,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${session.messageCount ?: 0} msgs · ${session.updatedAt ?: session.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )
        }
        if (state.sessions.isEmpty() && !state.sessionsLoading) {
            Text(
                "No sessions yet. Connect to lh serve and chat once.",
                color = CloverText2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
