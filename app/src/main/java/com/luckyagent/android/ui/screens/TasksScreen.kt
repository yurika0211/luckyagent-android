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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.TaskFilter
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
fun TasksScreen(state: AppUiState, vm: AppViewModel) {
    val query = state.taskQuery.trim().lowercase()
    val tasks = remember(state.tasks, state.taskFilter, state.taskQuery) {
        state.tasks.filter { task ->
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
            .fillMaxSize()
            .background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = if (detail == null) "Tasks" else "Task details",
            subtitle = if (detail == null) {
                if (state.taskPolling) "Watching active tasks" else "Multi-agent task center"
            } else {
                detail.root.summary.id
            },
            actions = {
                if (detail != null) {
                    IconButton(onClick = vm::clearSelectedTask) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to tasks")
                    }
                }
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
        TaskFilter.All to "All",
        TaskFilter.Active to "Active",
        TaskFilter.Completed to "Done",
        TaskFilter.Failed to "Failed",
        TaskFilter.Cancelled to "Cancelled",
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
            if (state.taskQuery.isEmpty()) Text("Filter by task, agent, mode or status", color = CloverText3)
            inner()
        },
    )

    when {
        state.tasksLoading && state.tasks.isEmpty() -> {
            Text("Loading tasks…", color = CloverText2, modifier = Modifier.padding(16.dp))
        }
        !state.tasksLoading && tasks.isEmpty() -> {
            EmptyState(
                title = "No tasks",
                body = if (state.tasks.isEmpty()) {
                    "Multi-agent and background tasks will appear here."
                } else {
                    "No tasks match the current filters."
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
                    TaskSummaryCard(task, onClick = { vm.selectTask(task.id) })
                }
            }
        }
    }
}

@Composable
private fun TaskSummaryCard(task: TaskSummary, onClick: () -> Unit) {
    CloverCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            StatusDot(task.status)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(task.id, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MetaChip(task.status.ifBlank { "unknown" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaChip(task.mode.ifBlank { "single" })
            MetaChip(task.source.ifBlank { "task" })
            if (task.origin == TaskOrigin.Legacy) MetaChip("legacy")
        }
        LinearProgressIndicator(
            progress = { task.progress.coerceIn(0f, 1f).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = if (task.status.equals("failed", ignoreCase = true)) CloverError else CloverAccent,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TaskMetric("Progress", "${(task.progress * 100).toInt()}%")
            if (task.childCount > 0) TaskMetric("Agents", task.childCount.toString())
            if (task.completedChildren > 0) TaskMetric("Done", task.completedChildren.toString())
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
                    MetaChip(summary.status.ifBlank { "unknown" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip(summary.mode)
                    MetaChip(summary.source)
                    MetaChip(if (detail.origin == TaskOrigin.Legacy) "legacy" else "unified")
                }
                LinearProgressIndicator(
                    progress = { summary.progress.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (summary.status.equals("failed", ignoreCase = true)) CloverError else CloverAccent,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TaskMetric("Progress", "${(summary.progress * 100).toInt()}%")
                    TaskMetric("Running", summary.runningChildren.toString())
                    TaskMetric("Done", summary.completedChildren.toString())
                    TaskMetric("Failed", summary.failedChildren.toString(), CloverError)
                }
                if (!summary.status.isTerminalTaskStatus()) {
                    Button(onClick = { vm.cancelTask(summary) }) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Request cancel")
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
        if (root.children.isNotEmpty()) {
            item { Text("Agents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(root.children, key = { it.summary.id }) { child ->
                TaskNodeCard(child)
            }
        }
        if (detail.events.isNotEmpty()) {
            item { Text("Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(detail.events, key = { event -> "${event.type}-${event.time}-${event.childId}" }) { event ->
                TaskEventCard(event)
            }
        } else if (detail.origin == TaskOrigin.Legacy) {
            item {
                EmptyState(
                    title = "No event stream",
                    body = "This legacy task exposes child state and output, but not persisted task events.",
                )
            }
        }
    }
}

@Composable
private fun TaskNodeCard(node: TaskNode) {
    var expanded by remember(node.summary.id) { mutableStateOf(false) }
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(node.summary.status)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(node.summary.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(node.summary.source, node.summary.status).joinToString(" · "),
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
        node.summary.result?.takeIf { it.isNotBlank() }?.let { DetailPayload("Output", it) }
        node.summary.error?.takeIf { it.isNotBlank() }?.let { DetailPayload("Error", it, CloverError) }
        if (expanded) {
            node.children.forEach { child -> TaskNodeCard(child) }
        }
    }
}

@Composable
private fun TaskEventCard(event: TaskEvent) {
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(event.status ?: event.type)
            Spacer(Modifier.width(8.dp))
            Text(event.type, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatTaskTime(event.time), style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        event.childId?.let { Text("child: $it", style = MaterialTheme.typography.labelSmall, color = CloverText3) }
        event.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = CloverText2) }
        event.error?.takeIf { it.isNotBlank() }?.let { Text(it, color = CloverError) }
        if (event.evidence.isNotEmpty()) DetailPayload("Evidence", event.evidence.joinToString("\n"))
        if (event.files.isNotEmpty()) DetailPayload("Files", event.files.joinToString("\n"))
        if (event.tests.isNotEmpty()) DetailPayload("Tests", event.tests.joinToString("\n"))
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

@Composable
private fun StatusDot(status: String) {
    val normalized = status.lowercase()
    val color = when {
        normalized == "completed" -> CloverLeaf
        normalized == "failed" || normalized == "timeout" -> CloverError
        normalized == "cancelled" -> CloverText3
        else -> CloverAccent
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

private fun formatTaskTime(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrElse { raw.take(16).replace('T', ' ') }
}
