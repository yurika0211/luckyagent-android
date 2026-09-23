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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.ToolTraceRecord
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.TrajectoryFilter
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun TrajectoryScreen(state: AppUiState, vm: AppViewModel) {
    val trace = state.trajectory
    val records = remember(trace, state.trajectoryFilter, state.trajectoryQuery) {
        val q = state.trajectoryQuery.trim().lowercase()
        (trace?.tools.orEmpty()).filter { record ->
            val okFilter = when (state.trajectoryFilter) {
                TrajectoryFilter.All -> true
                TrajectoryFilter.Success -> record.success
                TrajectoryFilter.Failure -> !record.success
            }
            val okQuery = q.isEmpty() || listOfNotNull(
                record.name,
                record.annotation,
                record.arguments,
                record.result,
                record.error,
            ).any { it.lowercase().contains(q) }
            okFilter && okQuery
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = "Tool trajectory",
            subtitle = state.settings.sessionId.ifBlank { "Select a chat session first" },
            actions = {
                IconButton(onClick = vm::refreshTrajectory) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (state.trajectoryLoading) {
            Text(
                "Loading tool trace…",
                color = CloverText2,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        state.trajectoryError?.let { ErrorLine(it) }

        val summary = trace
        if (summary != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryStat("Calls", (summary.totalCalls ?: summary.tools.size).toString(), Modifier.weight(1f))
                SummaryStat("OK", (summary.successes ?: summary.tools.count { it.success }).toString(), Modifier.weight(1f))
                SummaryStat("Fail", (summary.failures ?: summary.tools.count { !it.success }).toString(), Modifier.weight(1f))
                val rate = summary.successRate
                SummaryStat(
                    "Rate",
                    if (rate != null) "${(rate * 100).toInt()}%" else "—",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                TrajectoryFilter.All to "All",
                TrajectoryFilter.Success to "Success",
                TrajectoryFilter.Failure to "Failure",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = state.trajectoryFilter == value,
                    onClick = { vm.setTrajectoryFilter(value) },
                    label = { Text(label) },
                )
            }
        }

        BasicTextField(
            value = state.trajectoryQuery,
            onValueChange = vm::updateTrajectoryQuery,
            singleLine = true,
            cursorBrush = SolidColor(CloverAccent),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CloverSurface)
                .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (state.trajectoryQuery.isEmpty()) {
                    Text("Filter by tool / args / result", color = CloverText3)
                }
                inner()
            },
        )

        when {
            state.settings.sessionId.isBlank() -> {
                EmptyState(
                    title = "No session selected",
                    body = "Open Chat, pick a session, then return here to inspect tool calls.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            !state.trajectoryLoading && state.trajectoryError == null && records.isEmpty() && trace?.tools.isNullOrEmpty() -> {
                EmptyState(
                    title = "No tool activity yet",
                    body = "Tool calls from this session will show as structured cards once the agent runs tools.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            !state.trajectoryLoading && records.isEmpty() -> {
                EmptyState(
                    title = "No matches",
                    body = "No calls match the current filters.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(records, key = { idx, r -> "${r.name}-$idx-${r.arguments.orEmpty().hashCode()}" }) { _, record ->
                        TrajectoryEventCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface)
            .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrajectoryEventCard(record: ToolTraceRecord) {
    val ok = record.success
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (ok) CloverLeaf else CloverError),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                record.name.ifBlank { "(unnamed tool)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            MetaChip(if (ok) "success" else "failure")
            record.durationMs?.let {
                Spacer(Modifier.width(6.dp))
                MetaChip("${it} ms")
            }
        }
        if (!record.annotation.isNullOrBlank()) {
            Text(record.annotation, color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        }
        PayloadBlock("Arguments", record.arguments)
        PayloadBlock("Result", record.result)
        if (!record.error.isNullOrBlank()) {
            Text(record.error, color = CloverError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PayloadBlock(label: String, value: String?) {
    val text = value?.trim().orEmpty()
    if (text.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CloverSurface2)
            .padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
