package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.AutonomyTaskDetail
import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.AutonomyWorker
import com.luckyagent.android.data.api.executionSession
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.BackgroundFilter
import com.luckyagent.android.ui.components.CardTitleRow
import com.luckyagent.android.ui.components.BackgroundBlackboardCanvas
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.components.StatusChip
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BackgroundScreen(state: AppUiState, vm: AppViewModel) {
    val query = state.backgroundQuery.trim().lowercase()
    val tasks = remember(state.backgroundTasks, state.backgroundFilter, state.backgroundQuery) {
        state.backgroundTasks.filter { task ->
            val matchesFilter = when (state.backgroundFilter) {
                BackgroundFilter.All -> true
                BackgroundFilter.Ready -> task.state.equals("ready", ignoreCase = true)
                BackgroundFilter.Running -> task.state.equals("in_progress", ignoreCase = true)
                BackgroundFilter.Blocked -> task.state.equals("blocked", ignoreCase = true)
                BackgroundFilter.Done -> task.state.equals("done", ignoreCase = true)
            }
            val matchesQuery = query.isBlank() || listOf(
                task.id,
                task.title,
                task.description,
                task.priority,
                task.state,
                task.assignedTo.orEmpty(),
                task.tags.joinToString(" "),
            ).any { value -> value.lowercase().contains(query) }
            matchesFilter && matchesQuery
        }
    }
    val detail = state.selectedBackgroundTask

    Column(
        Modifier
            .fillMaxSize(),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = if (detail == null) "后台黑板" else "后台任务详情",
            subtitle = if (detail == null) {
                if (state.backgroundDashboard == null) "正在获取后台状态" else
                "${state.backgroundDashboard.workers.size} Worker · ${state.backgroundTasks.size} 项任务" +
                    if (state.backgroundPolling) " · 自动更新" else ""
            } else {
                detail.task.id
            },
            onBack = if (detail != null) vm::clearSelectedBackgroundTask else null,
            actions = {
                IconButton(onClick = { if (detail == null) vm.refreshBackground() else vm.selectBackgroundTask(detail.task.id) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新后台黑板")
                }
            },
        )

        if (detail == null) {
            BackgroundListContent(state, tasks, vm)
        } else {
            BackgroundDetailContent(state, detail.task, vm)
        }
    }
}

@Composable
private fun BackgroundListContent(state: AppUiState, tasks: List<AutonomyTaskSummary>, vm: AppViewModel) {
    var canvasView by rememberSaveable { mutableStateOf(true) }
    var showFilters by rememberSaveable {
        mutableStateOf(state.backgroundFilter != BackgroundFilter.All || state.backgroundQuery.isNotBlank())
    }
    val hasFilter = state.backgroundFilter != BackgroundFilter.All || state.backgroundQuery.isNotBlank()
    state.backgroundError?.let { ErrorLine(it) }
    if (state.backgroundDetailLoading && state.selectedBackgroundTask == null) {
        Text("正在加载任务详情…", color = CloverText2, modifier = Modifier.padding(horizontal = 16.dp))
    }
    state.backgroundDetailError?.takeIf { state.selectedBackgroundTask == null }?.let { ErrorLine(it) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = canvasView, onClick = { canvasView = true }, label = { Text("画布") })
        FilterChip(selected = !canvasView, onClick = { canvasView = false }, label = { Text("列表") })
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showFilters = !showFilters }) {
            Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (hasFilter) "筛选中" else "筛选")
        }
    }

    if (showFilters) {
        val filters = listOf(
            BackgroundFilter.All to "全部",
            BackgroundFilter.Ready to "待处理",
            BackgroundFilter.Running to "运行中",
            BackgroundFilter.Blocked to "阻塞",
            BackgroundFilter.Done to "已完成",
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filters, key = { it.first.name }) { (filter, label) ->
                FilterChip(
                    selected = state.backgroundFilter == filter,
                    onClick = { vm.setBackgroundFilter(filter) },
                    label = { Text(label) },
                )
            }
        }

        BasicTextField(
            value = state.backgroundQuery,
            onValueChange = vm::updateBackgroundQuery,
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
                if (state.backgroundQuery.isEmpty()) Text("搜索任务、Worker 或状态", color = CloverText3)
                inner()
            },
        )
    }

    if (canvasView) {
        BackgroundBlackboardCanvas(
            dashboard = state.backgroundDashboard,
            tasks = tasks,
            loading = state.backgroundLoading,
            onOpenTask = vm::selectBackgroundTask,
            onOpenSession = vm::openBackgroundSession,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    when {
        state.backgroundLoading && state.backgroundDashboard == null -> {
            Text("Loading background tasks…", color = CloverText2, modifier = Modifier.padding(16.dp))
        }
        !state.backgroundLoading && tasks.isEmpty() && state.backgroundDashboard == null -> {
            EmptyState(
                title = "No background tasks",
                body = if (state.backgroundTasks.isEmpty()) {
                    "Autonomy queue tasks will appear here."
                } else {
                    "No background tasks match the current filters."
                },
                modifier = Modifier.padding(16.dp),
                icon = {
                    Icon(
                        Icons.Outlined.HourglassEmpty,
                        contentDescription = null,
                        tint = CloverText3,
                        modifier = Modifier.size(48.dp),
                    )
                },
                centered = state.backgroundTasks.isEmpty(),
            )
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "runtime-overview") {
                    BackgroundOverview(state, onOpenSettings = { vm.navigate(com.luckyagent.android.ui.AppDestination.Settings) })
                }
                item(key = "runtime-workers") { BackgroundWorkers(state, vm) }
                item(key = "runtime-heartbeat") { BackgroundHeartbeat(state) }
                if (tasks.isEmpty()) item(key = "empty-tasks") {
                    Text("当前筛选范围内没有任务。", color = CloverText3, modifier = Modifier.padding(12.dp))
                }
                items(tasks, key = { it.id }) { task ->
                    BackgroundTaskCard(
                        task,
                        onClick = { vm.selectBackgroundTask(task.id) },
                        onOpenSession = task.executionSession()?.let { { vm.openBackgroundSession(it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundOverview(state: AppUiState, onOpenSettings: () -> Unit) {
    val dashboard = state.backgroundDashboard ?: return
    CloverCard(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackgroundStatusDot(if (dashboard.started) "running" else "stopped")
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Autonomy runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (dashboard.started) "Workers are available for queued tasks" else "Runtime is stopped; monitoring is read-only",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloverText2,
                )
            }
            MetaChip(if (dashboard.started) "running" else "stopped")
        }
        if (!dashboard.started) {
            TextButton(onClick = onOpenSettings) { Text("查看配置") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BackgroundMetric("Ready", dashboard.queue.ready.toString())
            BackgroundMetric("Running", dashboard.queue.inProgress.toString())
            BackgroundMetric("Blocked", dashboard.queue.blocked.toString(), if (dashboard.queue.blocked > 0) CloverError else CloverText2)
            BackgroundMetric("Done", dashboard.queue.done.toString(), CloverLeaf)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BackgroundMetric("Workers", "${dashboard.pool.busyWorkers}/${dashboard.pool.workerCount}")
            BackgroundMetric("Idle", dashboard.pool.idleWorkers.toString())
            Spacer(Modifier.weight(1f))
            Text("Heartbeat ${formatBackgroundTime(dashboard.lastHeartbeat)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
    }
}

@Composable
private fun BackgroundWorkers(state: AppUiState, vm: AppViewModel) {
    val workers = state.backgroundDashboard?.workers.orEmpty()
    if (workers.isEmpty()) return
    CloverCard(Modifier.padding(vertical = 2.dp)) {
        Text("Workers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        workers.forEach { worker ->
            BackgroundWorkerRow(worker, onOpenSession = vm::openBackgroundSession)
        }
    }
}

@Composable
private fun BackgroundWorkerRow(worker: AutonomyWorker, onOpenSession: (String?) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        BackgroundStatusDot(worker.state)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(worker.id, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            val currentTask = worker.currentTaskTitle?.takeIf { it.isNotBlank() }
                ?: worker.currentTaskId?.takeIf { it.isNotBlank() }
            Text(
                currentTask ?: "Idle",
                style = MaterialTheme.typography.bodySmall,
                color = CloverText2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MetaChip(worker.state.ifBlank { "unknown" })
            Text("${worker.taskCount} tasks", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
    }
    worker.sessionId?.takeIf { it.isNotBlank() }?.let { sessionId ->
        TextButton(onClick = { onOpenSession(sessionId) }) {
            Text("Open worker conversation")
        }
    }
}

@Composable
private fun BackgroundHeartbeat(state: AppUiState) {
    val events = state.backgroundDashboard?.heartbeatEvents.orEmpty().takeLast(3).asReversed()
    if (events.isEmpty()) return
    CloverCard(Modifier.padding(vertical = 2.dp)) {
        Text("Recent heartbeat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        events.forEach { event ->
            Row(verticalAlignment = Alignment.Top) {
                BackgroundStatusDot(event.mode)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${event.mode.ifBlank { "heartbeat" }} · pulled ${event.tasksPulled}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    event.actions.firstOrNull()?.takeIf { it.isNotBlank() }?.let { action ->
                        Text(action, style = MaterialTheme.typography.bodySmall, color = CloverText2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(formatBackgroundTime(event.timestamp), style = MaterialTheme.typography.labelSmall, color = CloverText3)
            }
        }
    }
}

@Composable
private fun BackgroundTaskCard(
    task: AutonomyTaskSummary,
    onClick: () -> Unit,
    onOpenSession: (() -> Unit)? = null,
) {
    CloverCard(onClick = onClick) {
        CardTitleRow(
            title = task.title.ifBlank { task.id },
            subtitle = task.id.takeIf { task.title.isNotBlank() },
            trailing = { StatusChip(task.state.ifBlank { "unknown" }) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaChip(task.priority.ifBlank { "normal" })
            task.assignedTo?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
            task.tags.firstOrNull()?.let { MetaChip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BackgroundMetric("Attempts", task.attempts.toString())
            if (task.retries > 0) BackgroundMetric("Retries", task.retries.toString(), CloverError)
            if (task.checkpointPresent) BackgroundMetric("Checkpoint", "saved", CloverAccent)
            Spacer(Modifier.weight(1f))
            Text(formatBackgroundTime(task.lastActivityAt), style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        task.blockReason?.takeIf { it.isNotBlank() }?.let { Text(it, color = CloverError, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        onOpenSession?.let { open ->
            TextButton(onClick = open, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open agent chat")
            }
        }
    }
}

@Composable
private fun BackgroundDetailContent(state: AppUiState, task: AutonomyTaskDetail, vm: AppViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.backgroundDetailLoading) item { Text("Refreshing background task…", color = CloverText2) }
        state.backgroundDetailError?.let { error -> item { ErrorLine(error, Modifier.padding(horizontal = 0.dp)) } }
        item {
            CloverCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackgroundStatusDot(task.state)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(task.title.ifBlank { task.id }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(task.id, style = MaterialTheme.typography.labelSmall, color = CloverText3)
                    }
                    MetaChip(task.state.ifBlank { "unknown" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChip(task.priority.ifBlank { "normal" })
                    task.assignedTo?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
                    if (task.verified) MetaChip("verified")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackgroundMetric("Attempts", task.attempts.toString())
                    BackgroundMetric("Retries", task.retries.toString())
                    BackgroundMetric("Continued", task.continuations.toString())
                }
            }
        }
        task.description.takeIf { it.isNotBlank() }?.let { description ->
            item { BackgroundDetailPayload("Description", description) }
        }
        task.result?.takeIf { it.isNotBlank() }?.let { result ->
            item { BackgroundDetailPayload("Result", result) }
        }
        task.blockReason?.takeIf { it.isNotBlank() }?.let { reason ->
            item { BackgroundDetailPayload("Block reason", reason, CloverError) }
        }
        task.error?.takeIf { it.isNotBlank() }?.let { error ->
            item { BackgroundDetailPayload("Error", error, CloverError) }
        }
        if (task.acceptanceCriteria.isNotEmpty()) {
            item { BackgroundDetailPayload("Acceptance criteria", task.acceptanceCriteria.joinToString("\n")) }
        }
        if (task.operations.isNotEmpty()) {
            item { Text("Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(task.operations, key = { it.id }) { operation ->
                CloverCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BackgroundStatusDot(operation.state)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(operation.name.ifBlank { operation.id }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(operation.id, style = MaterialTheme.typography.labelSmall, color = CloverText3)
                        }
                        MetaChip(operation.state.ifBlank { "unknown" })
                    }
                    operation.resolvedBy?.takeIf { it.isNotBlank() }?.let { Text("Resolved by $it", style = MaterialTheme.typography.labelSmall, color = CloverText3) }
                }
            }
        }
        item {
            CloverCard {
                BackgroundDetailPayload("Session", task.sessionId ?: "—")
                task.executionSession()?.let { sessionId ->
                    TextButton(
                        onClick = { vm.openBackgroundSession(sessionId) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open agent chat")
                    }
                }
                BackgroundDetailPayload("Last activity", formatBackgroundTime(task.lastActivityAt))
                BackgroundDetailPayload("Checkpoint", if (task.checkpointPresent) "Saved · ${formatBackgroundTime(task.checkpointAt)}" else "No checkpoint")
                task.verification?.takeIf { it.isNotBlank() }?.let { BackgroundDetailPayload("Verification", it) }
            }
        }
        if (task.metadata.isNotEmpty()) {
            item { BackgroundDetailPayload("Metadata", task.metadata.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
        }
    }
}

@Composable
private fun BackgroundMetric(label: String, value: String, color: Color = CloverText2) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BackgroundDetailPayload(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = color)
    }
}

@Composable
private fun BackgroundStatusDot(status: String) {
    val normalized = status.lowercase()
    val color = when {
        normalized == "done" -> CloverLeaf
        normalized == "blocked" -> CloverError
        normalized == "stopped" -> CloverText3
        else -> CloverAccent
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

private fun formatBackgroundTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrElse { raw.take(16).replace('T', ' ') }
}
