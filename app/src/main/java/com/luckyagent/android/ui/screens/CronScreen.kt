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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
                    body = "在聊天里用 /cron add 创建之后，会出现在这里。",
                    modifier = Modifier.padding(16.dp),
                    actionLabel = "回到聊天",
                    onAction = { vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) },
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
                    items(state.cronJobs, key = { it.id }) { job -> CronJobCard(job) }
                }
            }
        }
    }
}

@Composable
private fun CronJobCard(job: CronJob) {
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
        job.lastError?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = CloverError, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatCronTimestamp(value: String): String = value
    .replace('T', ' ')
    .removeSuffix("Z")
    .substringBefore('.')
