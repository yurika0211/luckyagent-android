package com.luckyagent.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.components.MarkdownText
import com.luckyagent.android.ui.components.MetaChip
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
import com.luckyagent.android.ui.theme.CloverToolBg
import com.luckyagent.android.ui.theme.CloverUserBubble
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(state: AppUiState, vm: AppViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renameTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CloverBgSide,
                modifier = Modifier.width(320.dp),
            ) {
                SessionDrawer(
                    state = state,
                    onClose = { scope.launch { drawerState.close() } },
                    onSelect = { id ->
                        vm.selectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onCreate = {
                        vm.createSession()
                        scope.launch { drawerState.close() }
                    },
                    onRename = { session ->
                        renameTarget = session
                        renameText = session.title?.takeIf { it.isNotBlank() } ?: session.id
                    },
                    onRefresh = vm::refreshSessions,
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CloverBg)
                .imePadding(),
        ) {
            ChatTopBar(
                state = state,
                onMenu = { scope.launch { drawerState.open() } },
            )

            val listState = rememberLazyListState()
            LaunchedEffect(state.bubbles.size, state.bubbles.lastOrNull()?.content) {
                if (state.bubbles.isNotEmpty()) {
                    listState.animateScrollToItem(state.bubbles.lastIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.bubbles.isEmpty()) {
                    item {
                        WelcomeBlock(
                            suggestions = state.suggestionPrompts,
                            onPick = vm::applySuggestion,
                        )
                    }
                }
                items(state.bubbles, key = { it.id }) { bubble ->
                    BubbleRow(bubble)
                }
            }

            if (state.suggestionPrompts.isNotEmpty() && state.bubbles.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.suggestionPrompts.take(4).forEach { s ->
                        Text(
                            text = s,
                            style = MaterialTheme.typography.labelMedium,
                            color = CloverAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(1.dp, CloverLine, RoundedCornerShape(999.dp))
                                .clickable { vm.applySuggestion(s) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            ComposerBar(
                state = state,
                onChange = vm::updateComposer,
                onSend = vm::sendComposer,
                onStop = vm::cancelRun,
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
private fun ChatTopBar(state: AppUiState, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CloverSurface)
            .border(1.dp, CloverLine)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Outlined.Menu, contentDescription = "Sessions")
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
        val streaming = state.bubbles.any { it.streaming }
        if (streaming) {
            Spacer(Modifier.width(6.dp))
            MetaChip("streaming")
        }
    }
    state.activityLine?.let {
        Text(
            it,
            color = CloverText2,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .background(CloverSurface2)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WelcomeBlock(suggestions: List<String>, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, CloverLine, RoundedCornerShape(16.dp))
            .background(CloverSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("LuckyAgent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Thin Android client for your host runtime. Start a session from the drawer, or pick a suggestion.",
            color = CloverText2,
            style = MaterialTheme.typography.bodyMedium,
        )
        suggestions.forEach { s ->
            Text(
                s,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CloverSurface2)
                    .clickable { onPick(s) }
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun BubbleRow(bubble: ChatBubble) {
    val isUser = bubble.role.equals("user", ignoreCase = true)
    val isTool = bubble.role.equals("tool", ignoreCase = true) || bubble.toolName != null
    val isSystem = bubble.role.equals("system", ignoreCase = true)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        when {
            isTool -> ToolBubble(bubble)
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .border(1.dp, CloverLine, RoundedCornerShape(16.dp))
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
                    if (!bubble.reasoning.isNullOrBlank()) {
                        Text(
                            bubble.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = CloverText2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CloverSurface2)
                                .padding(8.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (bubble.content.isNotBlank()) {
                        MarkdownText(markdown = bubble.content)
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
private fun ToolBubble(bubble: ChatBubble) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CloverToolBg)
            .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                bubble.toolName ?: "tool",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            when (bubble.toolSuccess) {
                true -> MetaChip("ok")
                false -> MetaChip("fail")
                null -> if (!bubble.toolDone) MetaChip("running")
            }
        }
        bubble.toolArgs?.takeIf { it.isNotBlank() }?.let {
            MonoBlock(it)
        }
        bubble.toolOutput?.takeIf { it.isNotBlank() }?.let {
            MonoBlock(it)
        }
        if (bubble.content.isNotBlank() && bubble.toolOutput.isNullOrBlank()) {
            Text(bubble.content, style = MaterialTheme.typography.bodyMedium)
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
            .clip(RoundedCornerShape(8.dp))
            .background(CloverSurface)
            .padding(8.dp),
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
            .background(CloverSurface)
            .border(1.dp, CloverLine)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(onClick = { /* attachment entry degraded — no upload API on phone */ }) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = "Attachments (host-side only)",
                    tint = CloverText3,
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(CloverBg)
                    .border(1.dp, CloverLine, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (state.composer.isEmpty()) {
                        Text("Message LuckyAgent…", color = CloverText3)
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            val streaming = state.bubbles.any { it.streaming }
            if (streaming) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = CloverError)
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = state.composer.isNotBlank(),
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (state.composer.isNotBlank()) CloverAccent else CloverText3,
                    )
                }
            }
        }
        Text(
            "Attachments upload is not wired on Android yet — use host GUI if needed.",
            style = MaterialTheme.typography.labelSmall,
            color = CloverText3,
            modifier = Modifier.padding(start = 48.dp, top = 4.dp),
        )
    }
}

@Composable
private fun SessionDrawer(
    state: AppUiState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onRename: (RuntimeSession) -> Unit,
    onRefresh: () -> Unit,
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
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
            }
        }
        HorizontalDivider(color = CloverLine)
        TextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Refresh list")
        }
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
