package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3

@Composable
fun MemoryScreen(state: AppUiState, vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CloverBg),
    ) {
        ScreenHeader(
            eyebrow = "Vault",
            title = "Memory",
            subtitle = state.memoryGraphSummary ?: "Recall + topology from lh serve",
            actions = {
                IconButton(onClick = vm::refreshMemory) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                }
            },
        )

        state.memoryStats?.let { stats ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatPill("Total", stats.total?.toString() ?: "—")
                StatPill("Active", stats.active?.toString() ?: "—")
                StatPill("Short", stats.short?.toString() ?: "—")
                StatPill("Medium", stats.medium?.toString() ?: "—")
                StatPill("Long", (stats.long ?: stats.longTerm)?.toString() ?: "—")
                StatPill("Categories", stats.categories?.toString() ?: "—")
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = state.memoryQuery,
                onValueChange = vm::updateMemoryQuery,
                singleLine = true,
                cursorBrush = SolidColor(CloverAccent),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.refreshMemory() }),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CloverSurface)
                    .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (state.memoryQuery.isEmpty()) {
                        Text("Recall query…", color = CloverText3)
                    }
                    inner()
                },
            )
            IconButton(onClick = vm::refreshMemory) {
                Icon(Icons.Outlined.Search, contentDescription = "Search")
            }
        }

        if (state.memoryLoading) {
            Text("Loading memory…", color = CloverText2, modifier = Modifier.padding(16.dp))
        }
        state.memoryError?.let { ErrorLine(it) }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.memoryGraphNodes.isNotEmpty()) {
                item {
                    Text("Graph nodes", style = MaterialTheme.typography.titleSmall, color = CloverText2)
                }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.memoryGraphNodes.take(24).forEach { node ->
                            GraphNodeChip(node)
                        }
                    }
                }
            }

            item {
                Text(
                    "Recall results · ${state.memoryEntries.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = CloverText2,
                )
            }

            if (!state.memoryLoading && state.memoryEntries.isEmpty() && state.memoryError == null) {
                item {
                    EmptyState(
                        title = "No memories matched",
                        body = "Try another recall query. Write/remember stays on the host runtime.",
                    )
                }
            }

            items(state.memoryEntries, key = { it.id ?: it.content.orEmpty().hashCode().toString() }) { entry ->
                MemoryEntryCard(entry)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface)
            .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GraphNodeChip(node: MemoryGraphNode) {
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface2)
            .border(1.dp, CloverLine, RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Text(
            node.title ?: node.id,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        val meta = listOfNotNull(node.category, node.tier, node.degree?.let { "deg $it" })
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryEntryCard(entry: MemoryEntry) {
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.category ?: "memory",
                style = MaterialTheme.typography.labelLarge,
                color = CloverAccent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            entry.tier?.let { MetaChip(it) }
            entry.importance?.let {
                Spacer(Modifier.width(6.dp))
                MetaChip("imp ${"%.1f".format(it)}")
            }
        }
        Text(
            entry.content.orEmpty().ifBlank { "(empty)" },
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entry.tags.forEach { MetaChip(it) }
            entry.stateKey?.let { MetaChip("$it=${entry.stateValue ?: "?"}") }
            entry.id?.let { MetaChip(it.take(12)) }
        }
    }
}
