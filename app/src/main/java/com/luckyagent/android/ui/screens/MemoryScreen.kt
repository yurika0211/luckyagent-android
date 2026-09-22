package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(state: AppUiState, vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg)
            .padding(16.dp),
    ) {
        Text("Memory", style = MaterialTheme.typography.headlineSmall, color = CloverText)
        Text(
            "对照 GUI Memory：召回列表 + stats/graph 摘要（图可视化下一迭代）。",
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText2,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        BasicTextField(
            value = state.memoryQuery,
            onValueChange = vm::updateMemoryQuery,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.refreshMemory() }),
            cursorBrush = SolidColor(CloverAccent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CloverLine, RoundedCornerShape(10.dp))
                .background(CloverSurface)
                .padding(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = CloverText),
            decorationBox = { inner ->
                if (state.memoryQuery.isEmpty()) {
                    Text("Recall query，例如 project / preference", color = CloverText3)
                }
                inner()
            },
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::refreshMemory) {
                Text(if (state.memoryLoading) "Loading…" else "Recall")
            }
            Text(
                "${state.memoryEntries.size} entries",
                style = MaterialTheme.typography.labelMedium,
                color = CloverText3,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        state.memoryStats?.let { stats ->
            Text(
                "stats · short=${stats.short ?: 0} medium=${stats.medium ?: 0} long=${stats.long ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        state.memoryGraphSummary?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = CloverText2)
        }
        state.memoryError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (state.memoryEntries.isEmpty() && !state.memoryLoading && state.memoryError == null) {
            Text(
                "No memories yet. Chat with lh serve so remember/recall can populate this list.",
                color = CloverText2,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.memoryEntries, key = { it.id ?: it.content.orEmpty() }) { entry ->
                MemoryCard(entry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryCard(entry: MemoryEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverSurface, RoundedCornerShape(12.dp))
            .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.category?.takeIf { it.isNotBlank() }?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }
            entry.tier?.takeIf { it.isNotBlank() }?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }
            entry.importance?.let {
                AssistChip(onClick = {}, label = { Text("imp=${"%.2f".format(it)}") })
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.content.orEmpty().ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText,
        )
        val meta = listOfNotNull(
            entry.id?.let { id -> "id=${if (id.length > 18) id.take(8) + "…" + id.takeLast(6) else id}" },
            entry.stateKey?.let { "state=$it=${entry.stateValue.orEmpty()}" },
            entry.updatedAt ?: entry.createdAt,
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (entry.tags.isNotEmpty()) {
            Text(
                entry.tags.joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.labelSmall,
                color = CloverAccent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
