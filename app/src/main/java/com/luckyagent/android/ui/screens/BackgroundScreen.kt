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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.BackgroundFilter
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
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
            .fillMaxSize()
            .background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = if (detail == null) "Background" else "Background details",
            subtitle = if (detail == null) {
                if (state.backgroundPolling) "Watching autonomy queue" else "Autonomy worker monitor"
            } else {
                detail.task.id
            },
            actions = {
                if (detail != null) {
                    IconButton(onClick = vm::clearSelectedBackgroundTask) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to background tasks")
                    }
                }
                IconButton(onClick = { if (detail == null) vm.refreshBackground() else vm.selectBackgroundTask(detail.task.id) }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (detail == null) {
            BackgroundListContent(state, tasks, vm)
        } else {
            BackgroundDetailContent(state, detail.task)
        }
    }
}

@Composable
private fun BackgroundListContent(state: AppUiState, tasks: List<AutonomyTaskSummary>, vm: AppViewModel) {
    state.backgroundError?.let { ErrorLine(it) }

    BackgroundOverview(state)
    BackgroundWorkers(state)
    BackgroundHeartbeat(state)

    val filters = listOf(
        BackgroundFilter.All to "All",
        BackgroundFilter.Ready to "Ready",
        BackgroundFilter.Running to "Running",
        BackgroundFilter.Blocked to "Blocked",
        BackgroundFilter.Done to "Done",
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
            if (state.backgroundQuery.isEmpty()) Text("Filter by title, worker, priority or status", color = CloverText3)
            inner()
        },
    )

    when {
        state.backgroundLoading && state.backgroundDashboard == null -> {
            Text("Loading background tasks…", color = CloverText2, modifier = Modifier.padding(16.dp))
        }
        !state.backgroundLoading && tasks.isEmpty() -> {
            EmptyState(
                title = "No background tasks",
                body = if (state.backgroundTasks.isEmpty()) {
                    "Autonomy queue tasks will appear here."
                } else {
                    "No background tasks match the current filters."
                },
                modifier = Modifier.padding(16.dp),
            )
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(tasks, key = { it.id }) { task ->
                    BackgroundTaskCard(task, onClick = { vm.selectBackgroundTask(task.id) })
                }
            }
        }
    }
}

@Composable
private fun BackgroundOverview(state: AppUiState) {
    val dashboard = state.backgroundDashboard ?: return
    CloverCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
private fun BackgroundWorkers(state: AppUiState) {
    val workers = state.backgroundDashboard?.workers.orEmpty()
    if (workers.isEmpty()) return
    CloverCard(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text("Workers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        workers.forEach { worker ->
            BackgroundWorkerRow(worker)
        }
    }
}

@Composable
private fun BackgroundWorkerRow(worker: AutonomyWorker) {
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
}

@Composable
private fun BackgroundHeartbeat(state: AppUiState) {
    val events = state.backgroundDashboard?.heartbeatEvents.orEmpty().takeLast(3).asReversed()
    if (events.isEmpty()) return
    CloverCard(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
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
private fun BackgroundTaskCard(task: AutonomyTaskSummary, onClick: () -> Unit) {
    CloverCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BackgroundStatusDot(task.state)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title.ifBlank { task.id }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(task.id, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MetaChip(task.state.ifBlank { "unknown" })
        }
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
    }
}

@Composable
private fun BackgroundDetailContent(state: AppUiState, task: AutonomyTaskDetail) {
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
