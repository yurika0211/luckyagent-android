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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.ToolTraceRecord
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.AppDestination
import com.luckyagent.android.ui.TrajectoryFilter
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.DetailDisclosure
import com.luckyagent.android.ui.components.StatusChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun TrajectoryScreen(state: AppUiState, vm: AppViewModel) {
    val trace = state.trajectory
    val records = remember(trace, state.trajectoryFilter, state.trajectoryQuery, state.hiddenSessionCards, state.settings.sessionId) {
        val q = state.trajectoryQuery.trim().lowercase()
        val sessionId = state.settings.sessionId
        (trace?.tools.orEmpty()).filterIndexed { index, record ->
            val key = traceRecordKey(record, index, trace?.offset ?: 0)
            if (hiddenSessionCard(state.hiddenSessionCards, sessionId, SessionCardKind.Trace, key)) return@filterIndexed false
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
            .fillMaxSize(),
    ) {
        ScreenHeader(
            eyebrow = "Runtime",
            title = "本轮轨迹",
            subtitle = state.settings.sessionId.ifBlank { "先打开一个会话" },
            onBack = { vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) },
            actions = {
                IconButton(onClick = vm::refreshTrajectory) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        if (state.trajectoryLoading) {
            Text(
                "正在读取本轮轨迹",
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
                SummaryStat("调用", (summary.totalCalls ?: summary.tools.size).toString(), Modifier.weight(1f))
                SummaryStat("成功", (summary.successes ?: summary.tools.count { it.success }).toString(), Modifier.weight(1f))
                SummaryStat("失败", (summary.failures ?: summary.tools.count { !it.success }).toString(), Modifier.weight(1f))
                val rate = summary.successRate
                SummaryStat(
                    "成功率",
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
                TrajectoryFilter.All to "全部",
                TrajectoryFilter.Success to "成功",
                TrajectoryFilter.Failure to "失败",
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
                .background(CloverSurface2)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (state.trajectoryQuery.isEmpty()) {
                    Text("搜索工具、参数或结果", color = CloverText3)
                }
                inner()
            },
        )

        when {
            state.settings.sessionId.isBlank() -> {
                EmptyState(
                    title = "还没有会话",
                    body = "先回聊天打开一轮对话，工具调用会出现在这里。",
                    modifier = Modifier.padding(16.dp),
                    actionLabel = "打开聊天",
                    onAction = { vm.navigate(AppDestination.Chat) },
                )
            }
            !state.trajectoryLoading && state.trajectoryError == null && records.isEmpty() && trace?.tools.isNullOrEmpty() -> {
                EmptyState(
                    title = "这轮还没有轨迹",
                    body = "聊天里用到工具之后，调用会按步骤出现在这里。",
                    modifier = Modifier.padding(16.dp),
                    actionLabel = "回到聊天",
                    onAction = { vm.navigate(AppDestination.Chat) },
                    icon = {
                        Icon(
                            Icons.Outlined.PlaylistRemove,
                            contentDescription = null,
                            tint = CloverText3,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    centered = true,
                )
            }
            !state.trajectoryLoading && records.isEmpty() -> {
                EmptyState(
                    title = "没有匹配的轨迹",
                    body = "换个筛选，或回到聊天继续这一轮。",
                    modifier = Modifier.padding(16.dp),
                    actionLabel = "回到聊天",
                    onAction = { vm.navigate(AppDestination.Chat) },
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(records, key = { idx, r -> traceRecordKey(r, idx, trace?.offset ?: 0) }) { index, record ->
                        val hideKey = traceRecordKey(record, index, trace?.offset ?: 0)
                        TrajectoryEventCard(
                            record,
                            onHide = {
                                vm.hideSessionCard(
                                    state.settings.sessionId,
                                    SessionCardKind.Trace,
                                    hideKey,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

internal fun traceRecordKey(record: ToolTraceRecord, index: Int, offset: Int): String {
    val id = record.id?.trim().orEmpty()
    if (id.isNotEmpty()) return id
    return "page-${offset + index}-${record.name}-${record.arguments.orEmpty().hashCode()}"
}

@Composable
private fun TrajectoryEventCard(
    record: ToolTraceRecord,
    onHide: (() -> Unit)? = null,
) {
    var confirmHide by remember { mutableStateOf(false) }
    if (confirmHide && onHide != null) {
        HideSessionCardDialog(
            name = "轨迹",
            onDismiss = { confirmHide = false },
            onConfirm = {
                confirmHide = false
                onHide()
            },
        )
    }
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
            if (onHide != null) {
                IconButton(onClick = { confirmHide = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除轨迹", modifier = Modifier.size(16.dp), tint = CloverText3)
                }
            }
            StatusChip(if (ok) "success" else "failure")
            record.durationMs?.let {
                Spacer(Modifier.width(6.dp))
                MetaChip("${it} ms")
            }
        }
        if (!record.annotation.isNullOrBlank()) {
            Text(record.annotation, color = CloverText2, style = MaterialTheme.typography.bodyMedium)
        }
        DetailDisclosure("Arguments", record.arguments, json = true, previewLines = 2)
        DetailDisclosure("Result", record.result, json = true, previewLines = 3)
        if (!record.error.isNullOrBlank()) {
            Text(record.error, color = CloverError, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
