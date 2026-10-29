package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.luckyagent.android.ui.components.statusChipLabel
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.TaskDetail
import com.luckyagent.android.data.api.TaskEvent
import com.luckyagent.android.data.api.TaskNode
import com.luckyagent.android.data.api.TaskOrigin
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.BlackboardEntry
import com.luckyagent.android.data.api.AgentMessage
import com.luckyagent.android.data.api.executionSession
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.TaskFilter
import com.luckyagent.android.ui.components.CardTitleRow
import com.luckyagent.android.ui.components.CloverCard
import androidx.compose.material.icons.outlined.AssignmentLate
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.StatusChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TasksScreen(state: AppUiState, vm: AppViewModel) {
    val query = state.taskQuery.trim().lowercase()
    val sessionId = state.settings.sessionId
    val tasks = remember(state.tasks, state.taskFilter, state.taskQuery, state.hiddenSessionCards, sessionId) {
        state.tasks.filter { task ->
            if (hiddenSessionCard(state.hiddenSessionCards, sessionId, SessionCardKind.Task, task.id)) return@filter false
            val status = task.status.lowercase()
            val matchesFilter = when (state.taskFilter) {
                TaskFilter.All -> true
                TaskFilter.Active -> status == "pending" || status == "running" || status == "blocked"
                TaskFilter.Completed -> status == "completed"
                TaskFilter.Failed -> status == "failed" || status == "timeout"
                TaskFilter.Cancelled -> status == "cancelled"
            }
            val matchesQuery = query.isBlank() || listOf(
                task.id,
                task.description,
                task.source,
                task.mode,
                task.status,
            ).any { it.lowercase().contains(query) }
            matchesFilter && matchesQuery
        }
    }

    val detail = state.selectedTask
    Column(
        Modifier
            .fillMaxSize(),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = if (detail == null) "任务" else "任务详情",
            subtitle = if (detail == null) {
                if (state.taskPolling) "正在看进行中的任务" else "从当前会话长出来的任务"
            } else {
                detail.root.summary.description.ifBlank { detail.root.summary.id }
            },
            onBack = if (detail != null) vm::clearSelectedTask else ({ vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) }),
            actions = {
                IconButton(onClick = { if (detail == null) vm.refreshTasks() else vm.selectTask(detail.root.summary.id) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (detail == null) {
            TaskListContent(state, tasks, vm)
        } else {
            TaskDetailContent(state, detail, vm)
        }
    }
}

@Composable
private fun TaskListContent(state: AppUiState, tasks: List<TaskSummary>, vm: AppViewModel) {
    state.tasksError?.let { ErrorLine(it) }

    val filters = listOf(
        TaskFilter.All to "全部",
        TaskFilter.Active to "进行中",
        TaskFilter.Completed to "已完成",
        TaskFilter.Failed to "失败",
        TaskFilter.Cancelled to "已取消",
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(filters, key = { it.first.name }) { (filter, label) ->
            FilterChip(
                selected = state.taskFilter == filter,
                onClick = { vm.setTaskFilter(filter) },
                label = { Text(label) },
            )
        }
    }

    BasicTextField(
        value = state.taskQuery,
        onValueChange = vm::updateTaskQuery,
        singleLine = true,
        cursorBrush = SolidColor(CloverAccent),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (state.taskQuery.isEmpty()) Text("搜索任务、代理或状态", color = CloverText3)
            inner()
        },
    )

    if (tasks.isNotEmpty()) {
        TaskOverviewCard(tasks)
    }

    when {
        state.tasksLoading && state.tasks.isEmpty() -> {
            Text("正在读取任务", color = CloverText2, modifier = Modifier.padding(16.dp))
        }
        !state.tasksLoading && tasks.isEmpty() -> {
            EmptyState(
                title = if (state.tasks.isEmpty()) "还没有任务" else "没有匹配的任务",
                body = if (state.tasks.isEmpty()) {
                    "从聊天发出的协作任务会出现在这里。"
                } else {
                    "换个筛选，或回到聊天继续这一轮。"
                },
                modifier = Modifier.padding(16.dp),
                actionLabel = "回到聊天",
                onAction = { vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) },
                icon = {
                    Icon(
                        Icons.Outlined.AssignmentLate,
                        contentDescription = null,
                        tint = CloverText3,
                        modifier = Modifier.size(48.dp),
                    )
                },
                centered = state.tasks.isEmpty(),
            )
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val attention = tasks.filter { it.status.taskNeedsAttention() }
                val running = tasks.filter { it.status.taskIsActive() && !it.status.taskNeedsAttention() }
                val recent = tasks.filter { !it.status.taskNeedsAttention() && !it.status.taskIsActive() }
                if (attention.isNotEmpty()) {
                    item { TaskSectionHeader("Needs your attention", "Blocked or failed tasks") }
                    items(attention, key = { it.id }) { task ->
                        TaskSummaryCard(
                            task,
                            onClick = { vm.selectTask(task.id) },
                            onOpenSession = task.executionSession()?.let { { vm.openSession(it) } },
                            onHide = { vm.hideSessionCard(state.settings.sessionId, SessionCardKind.Task, task.id) },
                        )
                    }
                }
                if (running.isNotEmpty()) {
                    item { TaskSectionHeader("In progress", "Work happening now") }
                    items(running, key = { it.id }) { task ->
                        TaskSummaryCard(
                            task,
                            onClick = { vm.selectTask(task.id) },
                            onOpenSession = task.executionSession()?.let { { vm.openSession(it) } },
                            onHide = { vm.hideSessionCard(state.settings.sessionId, SessionCardKind.Task, task.id) },
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    item { TaskSectionHeader("Recent", "Finished and cancelled tasks") }
                    items(recent, key = { it.id }) { task ->
                        TaskSummaryCard(
                            task,
                            onClick = { vm.selectTask(task.id) },
                            onOpenSession = task.executionSession()?.let { { vm.openSession(it) } },
                            onHide = { vm.hideSessionCard(state.settings.sessionId, SessionCardKind.Task, task.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskOverviewCard(tasks: List<TaskSummary>) {
    val attention = tasks.count { it.status.taskNeedsAttention() }
    val active = tasks.count { it.status.taskIsActive() }
    val finished = tasks.count { it.status.equals("completed", ignoreCase = true) }
    CloverCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("这一轮", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OverviewMetric("需要处理", attention.toString(), if (attention > 0) CloverError else CloverText2)
            OverviewMetric("进行中", active.toString(), CloverAccent)
            OverviewMetric("已完成", finished.toString(), CloverLeaf)
        }
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}

@Composable
private fun TaskSectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}

@Composable
private fun TaskSummaryCard(
    task: TaskSummary,
    onClick: () -> Unit,
    onOpenSession: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
) {
    var confirmHide by remember { mutableStateOf(false) }
    if (confirmHide && onHide != null) {
        HideSessionCardDialog(
            name = "任务",
            onDismiss = { confirmHide = false },
            onConfirm = {
                confirmHide = false
                onHide()
            },
        )
    }
    CloverCard(onClick = onClick) {
        CardTitleRow(
            title = task.description,
            subtitle = task.id,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onHide != null) {
                        IconButton(onClick = { confirmHide = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除任务", modifier = Modifier.size(16.dp), tint = CloverText3)
                        }
                    }
                    StatusChip(friendlyTaskStatus(task.status))
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaChip(friendlyTaskMode(task.mode))
            MetaChip(friendlyTaskSource(task.source))
            task.role?.takeIf { it.isNotBlank() }?.let { MetaChip(friendlyTaskRole(it)) }
            if (task.origin == TaskOrigin.Legacy) MetaChip("legacy")
        }
        Text(
            task.status.taskReadableAction(task),
            style = MaterialTheme.typography.bodySmall,
            color = CloverText2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        onOpenSession?.let { open ->
            TextButton(onClick = open, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("打开结果")
            }
        }
        if (onOpenSession == null) {
            Text("还没有关联的对话", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        LinearProgressIndicator(
            progress = { task.progress.coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = if (task.status.equals("failed", ignoreCase = true)) CloverError else CloverAccent,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TaskMetric("Progress", "${(task.progress * 100).toInt()}%")
            if (task.childCount > 0) TaskMetric("Steps", task.childCount.toString())
            if (task.completedChildren > 0) TaskMetric("Done", task.completedChildren.toString(), CloverLeaf)
            if (task.failedChildren > 0) TaskMetric("Failed", task.failedChildren.toString(), CloverError)
            Spacer(Modifier.weight(1f))
            Text(formatTaskTime(task.lastActivityAt), style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
    }
}

@Composable
private fun TaskDetailContent(state: AppUiState, detail: TaskDetail, vm: AppViewModel) {
    val root = detail.root
    val summary = root.summary
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.taskDetailLoading) {
            item { Text("Refreshing task…", color = CloverText2) }
        }
        state.taskDetailError?.let { error -> item { ErrorLine(error, Modifier.padding(horizontal = 0.dp)) } }
        item {
            CloverCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(summary.status)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(summary.description, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(summary.id, style = MaterialTheme.typography.labelSmall, color = CloverText3)
                    }
                    StatusChip(friendlyTaskStatus(summary.status))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip(friendlyTaskMode(summary.mode))
                    MetaChip(friendlyTaskSource(summary.source))
                    summary.role?.takeIf { it.isNotBlank() }?.let { MetaChip(friendlyTaskRole(it)) }
                }
                summary.executionSession()?.let { sessionId ->
                    TextButton(
                        onClick = { vm.openSession(sessionId) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("打开结果")
                    }
                }
                if (summary.executionSession() == null) {
                    Text("还没有关联的对话", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                }
                Text(
                    summary.status.taskReadableAction(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = CloverText2,
                    modifier = Modifier.padding(top = 4.dp),
                )
                LinearProgressIndicator(
                    progress = { summary.progress.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (summary.status.equals("failed", ignoreCase = true)) CloverError else CloverAccent,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TaskMetric("Progress", "${(summary.progress * 100).toInt()}%")
                    TaskMetric("In progress", summary.runningChildren.toString(), CloverAccent)
                    TaskMetric("Done", summary.completedChildren.toString(), CloverLeaf)
                    TaskMetric("Failed", summary.failedChildren.toString(), CloverError)
                }
                if (!summary.status.isTerminalTaskStatus()) {
                    Button(onClick = { vm.cancelTask(summary) }) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("取消")
                    }
                }
            }
        }
        if (!summary.input.isNullOrBlank()) {
            item { DetailPayload("Input", summary.input) }
        }
        detail.result?.takeIf { it.isNotBlank() }?.let { result ->
            item { DetailPayload("Result", result) }
        }
        summary.error?.takeIf { it.isNotBlank() }?.let { error ->
            item { DetailPayload("Blocker", error, CloverError) }
        }
        if (detail.plannerTrace != null || summary.metadata["planner_trace"] != null || summary.metadata["planner"] != null) {
            item { PlannerDecisionCard(summary, detail.plannerTrace) }
        }
        if (detail.blackboard.isNotEmpty()) {
            item { BlackboardCard(detail.blackboard) }
        }
        if (detail.messages.isNotEmpty()) {
            item { AgentMessagesCard(detail.messages) }
        }
        if (root.children.isNotEmpty()) {
            item { TaskFlowStrip(root.children, summary.mode, vm::openTaskSession) }
            item {
                TaskSectionHeader("Steps", "State, progress and output for each step")
            }
            items(root.children, key = { it.summary.id }) { child ->
                TaskNodeCard(child, onOpenSession = vm::openTaskSession)
            }
        }
        if (detail.events.isNotEmpty()) {
            item {
                TaskSectionHeader("What happened", "A readable activity timeline")
            }
            items(detail.events, key = { event -> "${event.type}-${event.time}-${event.childId}" }) { event ->
                TaskEventCard(event)
            }
        } else if (detail.origin == TaskOrigin.Legacy) {
            item {
                EmptyState(
                    title = "没有事件记录",
                    body = "这条旧任务只有步骤和结果，没有保存过程。",
                    actionLabel = "回到聊天",
                    onAction = { vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) },
                )
            }
        }
    }
}

@Composable
private fun BlackboardCard(entries: List<BlackboardEntry>) {
    var expanded by remember(entries.firstOrNull()?.taskId) { mutableStateOf(false) }
    CloverCard {
        Text("Shared progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Notes shared by the agents working on this task",
            style = MaterialTheme.typography.labelSmall,
            color = CloverText3,
        )
        (if (expanded) entries else entries.takeLast(12)).forEach { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    entry.author?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = CloverText3)
                    }
                }
                DetailPayload("Shared note", entry.value)
            }
        }
        if (entries.size > 12) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show recent notes" else "Show all ${entries.size} notes")
        }
    }
}

@Composable
private fun AgentMessagesCard(messages: List<AgentMessage>) {
    var expanded by remember(messages.firstOrNull()?.correlation) { mutableStateOf(false) }
    CloverCard {
        Text("Agent collaboration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Messages exchanged while this task was running",
            style = MaterialTheme.typography.labelSmall,
            color = CloverText3,
        )
        (if (expanded) messages else messages.takeLast(12)).forEach { message ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(friendlyAgentMessageType(message.type))
                            message.payload["round"]?.let { append(" · Round ${jsonLabel(it)}") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(formatTaskTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = CloverText3)
                }
                Text(
                    listOfNotNull(
                        message.from.takeIf { it.isNotBlank() }?.let { "from $it" },
                        message.to.takeIf { it.isNotBlank() }?.let { "to $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
                agentMessageSummary(message)?.let {
                    DetailPayload("Message", it)
                }
                if (message.correlation.isNotBlank()) Text(
                    "Thread: ${message.correlation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
        }
        if (messages.size > 12) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Show recent messages" else "Show all ${messages.size} messages")
        }
    }
}

private fun agentMessageSummary(message: AgentMessage): String? {
    val preferred = listOf("error", "output", "position", "vote", "content", "message", "summary", "state")
    for (key in preferred) {
        val value = message.payload[key]?.let(::jsonLabel)?.trim()
        if (!value.isNullOrBlank()) return "$key: $value"
    }
    return message.payload.takeIf { it.isNotEmpty() }?.entries
        ?.joinToString(", ") { "${it.key}=${it.value}" }
}

internal fun friendlyAgentMessageType(type: String): String = when (type.lowercase()) {
    "task_progress" -> "Progress update"
    "task_result" -> "Step result"
    "task_assign" -> "Task assigned"
    "task_cancel" -> "Task cancelled"
    "debate_turn" -> "Debate turn"
    "debate_vote" -> "Debate vote"
    "error" -> "Agent error"
    else -> type.ifBlank { "Agent message" }.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
internal fun TaskFlowStrip(
    children: List<TaskNode>,
    mode: String,
    onOpenSession: ((TaskSummary) -> Unit)? = null,
    onOpenTask: ((TaskSummary) -> Unit)? = null,
    title: String? = null,
) {
    val sequential = mode.equals("pipeline", ignoreCase = true)
    CloverCard {
        Text(title ?: friendlyTaskMode(mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (onOpenTask != null) "点击节点查看子代理任务详情" else when (mode.lowercase()) {
                "parallel" -> "These agents work independently, then their results are combined."
                "pipeline" -> "Each step passes its output to the next."
                "debate" -> "Participants exchange positions and votes. See their rounds in Agent collaboration."
                else -> "Tap a linked agent to open its conversation."
            },
            style = MaterialTheme.typography.bodySmall,
            color = CloverText3,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(children, key = { _, node -> "flow-${node.summary.id}" }) { index, node ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.width(126.dp).clickable(
                            enabled = onOpenTask != null || (onOpenSession != null && node.summary.executionSession() != null),
                            onClick = { (onOpenTask ?: onOpenSession)?.invoke(node.summary) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(taskStatusColor(node.summary.status)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (index + 1).toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            node.summary.metadata["agent_id"] ?: node.summary.description,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            friendlyTaskStatus(node.summary.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                        )
                    }
                    if (index < children.lastIndex && sequential) {
                        Text("→", color = CloverText3, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

private fun jsonLabel(value: kotlinx.serialization.json.JsonElement): String =
    (value as? JsonPrimitive)?.content ?: value.toString()

@Composable
private fun PlannerDecisionCard(summary: TaskSummary, plannerTrace: kotlinx.serialization.json.JsonElement?) {
    var expanded by remember(summary.id) { mutableStateOf(false) }
    val trace = remember(summary.id, plannerTrace, summary.metadata["planner_trace"]) {
        when {
            plannerTrace is JsonObject -> plannerTrace
            else -> runCatching { Json.parseToJsonElement(summary.metadata["planner_trace"].orEmpty()) as? JsonObject }.getOrNull()
        }
    }
    val chosen = trace?.get("mode")?.let(::jsonLabel) ?: summary.metadata["planned_mode"] ?: summary.mode
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scheduling decision", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Selected: ${friendlyTaskMode(chosen)}", style = MaterialTheme.typography.bodySmall, color = CloverText2)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = "Scheduling details")
            }
        }
        if (expanded) {
            trace?.get("decision_basis")?.let { Text(jsonLabel(it), style = MaterialTheme.typography.bodySmall, color = CloverText2) }
            (trace?.get("candidates") as? JsonArray)?.forEach { raw ->
                val candidate = raw as? JsonObject ?: return@forEach
                val candidateMode = candidate["mode"]?.let(::jsonLabel).orEmpty()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (candidateMode == chosen) "✓ " else ""}${friendlyTaskMode(candidateMode)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (candidateMode == chosen) CloverAccent else CloverText2,
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Weight ${candidate["decision_weight"]?.let(::jsonLabel).orEmpty()}", style = MaterialTheme.typography.labelSmall)
                        Text("Q ${candidate["mdp_q_value"]?.let(::jsonLabel).orEmpty()}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                    }
                }
            }
            val mdp = trace?.get("mdp") as? JsonObject
            (mdp?.get("state") as? JsonObject)?.let { state ->
                DetailPayload("Decision inputs", state.entries.joinToString("\n") { "${it.key.replace('_', ' ')}: ${jsonLabel(it.value)}" })
            }
            summary.metadata["planner_trace"]?.let { DetailPayload("Full planner trace", it) }
                ?: DetailPayload("Planner", summary.metadata.filterKeys { it.startsWith("planner") || it.startsWith("mdp") }.entries.joinToString("\n"))
        }
    }
}

@Composable
private fun TaskNodeCard(node: TaskNode, onOpenSession: (TaskSummary) -> Unit) {
    var expanded by remember(node.summary.id) { mutableStateOf(false) }
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(node.summary.status)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(node.summary.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        friendlyTaskSource(node.summary.source),
                        friendlyTaskStatus(node.summary.status),
                        node.summary.progress.takeIf { it > 0.0 }?.let { "${(it * 100).toInt()}%" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
            if (node.children.isNotEmpty()) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = "Expand")
                }
            }
        }
        if (node.summary.progress > 0.0 || node.summary.status.taskIsActive()) {
            LinearProgressIndicator(
                progress = { node.summary.progress.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (node.summary.status.taskNeedsAttention()) CloverError else CloverAccent,
            )
        }
        if (node.children.isNotEmpty()) {
            Text(
                "${node.children.size} ${if (node.children.size == 1) "substep" else "substeps"}",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
            )
        }
        node.summary.executionSession()?.let { sessionId ->
            TextButton(
                onClick = { onOpenSession(node.summary) },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("打开结果")
            }
        }
        node.summary.result?.takeIf { it.isNotBlank() }?.let { DetailPayload("Output", it) }
        node.summary.error?.takeIf { it.isNotBlank() }?.let { DetailPayload("Error", it, CloverError) }
        if (expanded) {
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { child -> TaskNodeCard(child, onOpenSession) }
            }
        }
    }
}

@Composable
private fun TaskEventCard(event: TaskEvent) {
    var expanded by remember(event.type, event.time, event.childId) { mutableStateOf(false) }
    val hasDetails = event.evidence.isNotEmpty() || event.files.isNotEmpty() || event.tests.isNotEmpty() || !event.error.isNullOrBlank()
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(event.status ?: event.type)
            Spacer(Modifier.width(8.dp))
            Text(friendlyEventType(event.type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatTaskTime(event.time), style = MaterialTheme.typography.labelSmall, color = CloverText3)
            if (hasDetails) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Hide details" else "Show details",
                    )
                }
            }
        }
        event.childId?.let { Text("Step ${it.takeLast(8)}", style = MaterialTheme.typography.labelSmall, color = CloverText3) }
        event.message?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = CloverText2, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) {
            event.error?.takeIf { it.isNotBlank() }?.let { Text(it, color = CloverError) }
            if (event.evidence.isNotEmpty()) DetailPayload("Evidence", event.evidence.joinToString("\n"))
            if (event.files.isNotEmpty()) DetailPayload("Files", event.files.joinToString("\n"))
            if (event.tests.isNotEmpty()) DetailPayload("Tests", event.tests.joinToString("\n"))
        }
    }
}

@Composable
private fun DetailPayload(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
    }
}

@Composable
private fun TaskMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color = CloverText2) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

internal fun String.taskNeedsAttention(): Boolean = when (lowercase()) {
    "blocked", "failed", "timeout", "error" -> true
    else -> false
}

internal fun String.taskIsActive(): Boolean = when (lowercase()) {
    "pending", "queued", "running", "in_progress", "in-progress" -> true
    else -> false
}

@Composable
internal fun HideSessionCardDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从当前会话拿掉$name") },
        text = { Text("只从这台手机的当前会话拿掉。电脑上的记录还在。") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("删除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

internal fun friendlyTaskStatus(status: String): String = statusChipLabel(status)

internal fun friendlyTaskMode(mode: String): String = when (mode.lowercase()) {
    "single" -> "Single step"
    "parallel" -> "Parallel"
    "pipeline" -> "Step by step"
    "debate" -> "Review rounds"
    "auto" -> "Auto planned"
    "autonomy_queue" -> "Background"
    else -> mode.ifBlank { "Task" }.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

internal fun friendlyTaskSource(source: String): String = when (source.lowercase()) {
    "tool" -> "Assistant"
    "gateway" -> "Gateway"
    "autonomy" -> "Background"
    "legacy" -> "Older task"
    else -> source.ifBlank { "Task" }.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun friendlyTaskRole(role: String): String = when (role.lowercase()) {
    "orchestrator" -> "Coordinator"
    "subagent" -> "Subagent"
    "worker-task" -> "Worker"
    else -> role.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun String.taskReadableAction(task: TaskSummary): String = when {
    taskNeedsAttention() -> task.error?.takeIf { it.isNotBlank() } ?: "This task needs your attention"
    taskIsActive() && task.childCount > 0 -> "${task.completedChildren} of ${task.childCount} steps complete"
    taskIsActive() -> "Working on this task now"
    equals("completed", ignoreCase = true) -> task.result?.takeIf { it.isNotBlank() }?.lineSequence()?.firstOrNull() ?: "Task finished successfully"
    equals("cancelled", ignoreCase = true) -> "Task was cancelled"
    else -> "Open to review the task details"
}

private fun taskStatusColor(status: String): androidx.compose.ui.graphics.Color {
    val normalized = status.lowercase()
    return when {
        normalized == "completed" || normalized == "done" -> CloverLeaf
        normalized == "failed" || normalized == "timeout" || normalized == "error" || normalized == "blocked" -> CloverError
        normalized == "cancelled" || normalized == "canceled" -> CloverText3
        else -> CloverAccent
    }
}

internal fun friendlyEventType(type: String): String = when (type.lowercase()) {
    "task.created" -> "Task created"
    "task.planned" -> "Plan ready"
    "task.started" -> "Work started"
    "task.child_created" -> "Step added"
    "task.progress" -> "Progress updated"
    "task.tool_used" -> "Tool used"
    "task.completed" -> "Task completed"
    "task.failed" -> "Task failed"
    else -> type.ifBlank { "Activity" }.replace('.', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun StatusDot(status: String) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(taskStatusColor(status)))
}

private fun formatTaskTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrElse { raw.take(16).replace('T', ' ') }
}
