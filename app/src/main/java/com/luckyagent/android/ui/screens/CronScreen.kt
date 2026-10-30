package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.CronJob
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CardTitleRow
import com.luckyagent.android.ui.components.CloverCard
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.EventBusy
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.StatusChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun CronScreen(state: AppUiState, vm: AppViewModel) {
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CronJob?>(null) }
    Column(
        Modifier
            .fillMaxSize(),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = "定时",
            subtitle = when {
                state.cronLoading -> "正在读取定时任务"
                state.cronError != null && state.cronJobs.isEmpty() -> "没能读到定时任务。重试这一条。"
                state.cronRunning -> "定时已开启 · ${state.cronCount} 项"
                else -> "定时已停止 · ${state.cronCount} 项"
            },
            actions = {
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建定时")
                }
                IconButton(onClick = vm::refreshCron, enabled = !state.cronLoading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        state.cronError?.let { ErrorLine(it) }

        when {
            state.cronLoading && state.cronJobs.isEmpty() -> {
                Text("正在读取定时任务", color = CloverText2, modifier = Modifier.padding(16.dp))
            }
            !state.cronLoading && state.cronJobs.isEmpty() -> {
                EmptyState(
                    title = "还没有定时任务",
                    body = "在这台电脑上创建一个以后执行的提醒。电脑关机或服务停止时不会运行。",
                    modifier = Modifier.padding(16.dp),
                    actionLabel = "新建定时",
                    onAction = { showCreate = true },
                    icon = {
                        Icon(
                            Icons.Outlined.EventBusy,
                            contentDescription = null,
                            tint = CloverText3,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    centered = true,
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.cronJobs, key = { it.id }) { job ->
                        CronJobCard(
                            job = job,
                            onPause = { vm.pauseCronJob(job.id) },
                            onResume = { vm.resumeCronJob(job.id) },
                            onDelete = { deleteTarget = job },
                            busy = state.cronLoading,
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CronCreateDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, schedule, command, description ->
                showCreate = false
                vm.createCronJob(name, schedule, command, description)
            },
        )
    }
    deleteTarget?.let { job ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除定时任务？") },
            text = { Text("“${job.name.ifBlank { job.id }}”会从电脑上的调度器删除。") },
            confirmButton = {
                Button(onClick = { deleteTarget = null; vm.deleteCronJob(job.id) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CronJobCard(
    job: CronJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
) {
    CloverCard {
        CardTitleRow(
            title = job.name.ifBlank { job.id.ifBlank { "定时任务" } },
            subtitle = job.schedule.ifBlank { null },
            trailing = { StatusChip(job.status.ifBlank { "unknown" }) },
        )
        job.description.takeIf { it.isNotBlank() }?.let {
            Text(it, color = CloverText2, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val timing = listOfNotNull(
            job.nextRun?.let { "下次 ${formatCronTimestamp(it)}" },
            job.lastRun?.let { "上次 ${formatCronTimestamp(it)}" },
        ).joinToString(" · ")
        if (timing.isNotBlank()) {
            Text(timing, color = CloverText3, style = MaterialTheme.typography.bodySmall)
        }
        job.metadata["session_id"]?.takeIf { it.isNotBlank() }?.let { sessionId ->
            val platform = job.metadata["platform"]?.takeIf { it.isNotBlank() } ?: "android"
            Text(
                "结果发到 $platform · $sessionId",
                color = CloverText3,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaChip("Runs ${job.runCount}")
            if (job.errorCount > 0) MetaChip("Errors ${job.errorCount}")
            job.metadata["mode"]?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val paused = job.status.equals("paused", ignoreCase = true)
            TextButton(onClick = if (paused) onResume else onPause, enabled = !busy) {
                Icon(
                    if (paused) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(if (paused) "启用" else "暂停")
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("删除")
            }
        }
        job.lastError?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = CloverError, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatCronTimestamp(value: String): String = value
    .replace('T', ' ')
    .removeSuffix("Z")
    .substringBefore('.')

@Composable
private fun CronCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, schedule: String, command: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("每天 09:00") }
    var command by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val canCreate = schedule.isNotBlank() && command.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建定时") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("任务会在已连接的电脑上运行。电脑关机或服务停止时不会执行。", color = CloverText2)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（可选）") }, singleLine = true)
                OutlinedTextField(value = schedule, onValueChange = { schedule = it }, label = { Text("何时发生") }, supportingText = { Text("例如：每天 09:00、每小时、每 30 分钟") }, singleLine = true)
                OutlinedTextField(value = command, onValueChange = { command = it }, label = { Text("要做的事") }, minLines = 2)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("备注（可选）") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, schedule, command, description) },
                enabled = canCreate,
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
