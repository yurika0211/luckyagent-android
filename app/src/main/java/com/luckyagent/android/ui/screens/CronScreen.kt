package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.CronJob
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
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
            title = "Cron tasks",
            subtitle = when {
                state.cronLoading -> "Querying scheduled tasks…"
                state.cronError != null && state.cronJobs.isEmpty() -> "Unable to query scheduler"
                state.cronRunning -> "Scheduler running · ${state.cronCount} task(s)"
                else -> "Scheduler stopped · ${state.cronCount} task(s)"
            },
            actions = {
                Button(onClick = vm::refreshCron, enabled = !state.cronLoading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text(if (state.cronLoading) "Querying…" else "Query tasks")
                }
            },
        )

        state.cronError?.let { ErrorLine(it) }

        when {
            state.cronLoading && state.cronJobs.isEmpty() -> {
                Text("Loading cron tasks…", color = CloverText2, modifier = Modifier.padding(16.dp))
            }
            !state.cronLoading && state.cronJobs.isEmpty() -> {
                EmptyState(
                    title = "No cron tasks",
                    body = "Scheduled tasks created by /cron add will appear here.",
                    modifier = Modifier.padding(16.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    job.id.ifBlank { job.name.ifBlank { "Cron task" } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                job.description.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = CloverText2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            StatusChip(job.status.ifBlank { "unknown" })
        }
        Text("Schedule · ${job.schedule.ifBlank { "—" }}", color = CloverText2)
        job.metadata["session_id"]?.takeIf { it.isNotBlank() }?.let { sessionId ->
            val platform = job.metadata["platform"]?.takeIf { it.isNotBlank() } ?: "android"
            Text(
                "结果发到会话 · $platform · $sessionId",
                color = CloverText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip("Runs ${job.runCount}")
            if (job.errorCount > 0) MetaChip("Errors ${job.errorCount}", modifier = Modifier)
            job.metadata["mode"]?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
        }
        job.nextRun?.let { Text("Next run · ${formatCronTimestamp(it)}", color = CloverText3, style = MaterialTheme.typography.bodySmall) }
        job.lastRun?.let { Text("Last run · ${formatCronTimestamp(it)}", color = CloverText3, style = MaterialTheme.typography.bodySmall) }
        job.lastError?.takeIf { it.isNotBlank() }?.let { Text(it, color = CloverError, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun formatCronTimestamp(value: String): String = value
    .replace('T', ' ')
    .removeSuffix("Z")
    .substringBefore('.')
