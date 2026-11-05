package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.api.MemoryGraphEdge
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.data.api.MemorySearchTrace
import com.luckyagent.android.data.api.MemoryTraceNode
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.EmptyState
import com.luckyagent.android.ui.components.ErrorLine
import com.luckyagent.android.ui.components.MemoryGraphPanel
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import kotlinx.coroutines.delay

@Composable
fun MemoryScreen(state: AppUiState, vm: AppViewModel) {
    var graphSearch by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var liveDialog by remember { mutableStateOf(false) }
    var seenLiveAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var playhead by remember(state.memoryTrace) { mutableIntStateOf(0) }
    var playing by remember(state.memoryTrace) { mutableStateOf(state.memoryTrace != null) }
    var speed by remember { mutableIntStateOf(950) }
    val trace = state.memoryTrace
    val maxDepth = trace?.hops?.maxOfOrNull { it.depth } ?: trace?.graphDepth ?: 0
    LaunchedEffect(trace, playing, playhead, speed) {
        if (trace != null && playing && playhead < maxDepth) {
            delay(speed.toLong())
            playhead += 1
        } else if (playhead >= maxDepth) playing = false
    }
    val traceInfo = remember(trace) {
        buildMap<String, MemoryTraceNode> {
            trace?.seeds.orEmpty().forEach { put(it.id, it) }
            trace?.results.orEmpty().forEach { put(it.id, it) }
            trace?.hops.orEmpty().forEach { hop ->
                if (hop.fromId !in this) put(hop.fromId, MemoryTraceNode(hop.fromId, hop.fromRef))
                if (hop.toId !in this) put(hop.toId, MemoryTraceNode(hop.toId, hop.toRef))
            }
        }
    }
    val graphNodeIds = remember(state.memoryGraphNodes) { state.memoryGraphNodes.mapTo(hashSetOf()) { it.id } }
    val offGraphTraceNodes = remember(traceInfo, graphNodeIds) { traceInfo.values.filter { it.id !in graphNodeIds }.distinctBy { it.id } }
    val revealedDepth = remember(trace, playhead) {
        buildMap<String, Int> {
            trace?.seeds.orEmpty().forEach { put(it.id, 0) }
            trace?.hops.orEmpty().filter { it.depth <= playhead }.forEach { hop -> putIfAbsent(hop.toId, hop.depth) }
            trace?.results.orEmpty().forEach { result -> if (result.id !in this && playhead >= maxDepth) put(result.id, maxDepth) }
        }
    }
    val revealedEdges = remember(trace, playhead) {
        trace?.hops.orEmpty().filter { it.depth <= playhead }.associate { "${it.fromId}|${it.toId}" to it.depth }
    }

    val nodes = state.memoryGraphNodes
    val selectedNode = nodes.firstOrNull { it.id == selectedId }
    val neighbors = remember(selectedNode, state.memoryGraphEdges, nodes) {
        if (selectedNode == null) emptyList() else state.memoryGraphEdges.mapNotNull { edge ->
            when (selectedNode.id) { edge.source -> edge.target; edge.target -> edge.source; else -> null }
        }.distinct().mapNotNull { id -> nodes.firstOrNull { it.id == id } }.sortedByDescending { it.degree ?: 0 }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                eyebrow = "Vault",
                title = "记忆",
                subtitle = "已连接电脑上的知识图谱和召回",
                onBack = { vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) },
                actions = { IconButton(onClick = vm::refreshMemory) { Icon(Icons.Outlined.Refresh, "Refresh") } },
            )
        }
        item(key = "stats") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Notes shown", nodes.size.toString())
                StatPill("Links", state.memoryGraphEdges.size.toString())
                StatPill("Isolated", state.memoryGraphSummary?.let { Regex("isolated=(\\d+)").find(it)?.groupValues?.get(1) } ?: "—")
                StatPill("Unresolved", state.memoryGraphSummary?.let { Regex("unresolved=(\\d+)").find(it)?.groupValues?.get(1) } ?: "—")
                StatPill("Vault total", state.memoryStats?.total?.toString() ?: "—")
            }
        }
        item(key = "trace-search") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = state.memoryTraceQuery,
                        onValueChange = vm::updateMemoryTraceQuery,
                        singleLine = true,
                        cursorBrush = SolidColor(CloverAccent),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.runMemoryTrace() }),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(CloverSurface2).padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { inner -> if (state.memoryTraceQuery.isEmpty()) Text("输入要召回的内容", color = CloverText3); inner() },
                    )
                    Button(onClick = { vm.runMemoryTrace() }, enabled = !state.memoryTraceLoading && state.memoryTraceQuery.isNotBlank()) {
                        Text(if (state.memoryTraceLoading) "正在召回" else "召回")
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { depth -> FilterChip(selected = state.memoryTraceDepth == depth, onClick = { vm.setMemoryTraceDepth(depth) }, label = { Text("${depth} hop") }) }
                    OutlinedButton(onClick = { seenLiveAt = System.currentTimeMillis(); liveDialog = true }) {
                        val unseen = state.memoryLiveTraces.count { it.receivedAt > seenLiveAt }
                        Text("Live ${state.memoryLiveTraces.size}" + if (unseen > 0) " · $unseen new" else "")
                    }
                    if (trace != null) TextButton(onClick = { vm.selectMemoryTrace(null); selectedId = null }) { Text("Clear") }
                }
            }
        }
        state.memoryTraceError?.let { error -> item(key = "trace-error") { ErrorLine(error) } }
        if (trace != null) {
            item(key = "trace-controls") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { playhead = 0; playing = false }) { Text("↺ Reset") }
                    TextButton(onClick = { playhead = (playhead - 1).coerceAtLeast(0); playing = false }, enabled = playhead > 0) { Text("‹ Step") }
                    Button(onClick = { if (playhead >= maxDepth) playhead = 0; playing = !playing }) { Text(if (playing) "❚❚ Pause" else "▶ Play") }
                    TextButton(onClick = { playhead = (playhead + 1).coerceAtMost(maxDepth); playing = false }, enabled = playhead < maxDepth) { Text("Step ▶") }
                    TextButton(onClick = { playhead = maxDepth; playing = false }) { Text("Reveal all") }
                    listOf(1700, 950, 480).forEachIndexed { i, ms -> FilterChip(speed == ms, { speed = ms }, label = { Text(listOf("Slow", "Normal", "Fast")[i]) }) }
                    Text("${if (playhead == 0) "Seeds" else "Depth $playhead"} of $maxDepth · ${revealedDepth.size} touched", color = CloverText3, style = MaterialTheme.typography.labelMedium)
                }
            }
            item(key = "trace-layers") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TraceLayerRow("Seeds", trace.seeds.joinToString(" · ") { it.ref ?: it.id }, playhead == 0) { playhead = 0; playing = false }
                    (1..maxDepth).forEach { depth ->
                        val hops = trace.hops.filter { it.depth == depth }
                        TraceLayerRow("Depth $depth", hops.joinToString(" · ") { "${it.fromRef ?: it.fromId} → ${it.toRef ?: it.toId}" }.ifBlank { "(no new notes at this depth)" }, playhead == depth) { playhead = depth; playing = false }
                    }
                }
            }
            if (offGraphTraceNodes.isNotEmpty()) item(key = "off-graph") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Off graph", color = CloverText3, style = MaterialTheme.typography.labelSmall)
                    offGraphTraceNodes.forEach { node -> AssistChip(onClick = { selectedId = node.id }, label = { Text(node.ref ?: node.id, maxLines = 1) }) }
                }
            }
        }
        item(key = "graph-search") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(value = graphSearch, onValueChange = { graphSearch = it }, singleLine = true, cursorBrush = SolidColor(CloverAccent), textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CloverSurface2).padding(10.dp), decorationBox = { inner -> if (graphSearch.isBlank()) Text("搜索标题、分类或标签", color = CloverText3); inner() })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(state.memoryGraphIsolated, vm::setMemoryGraphIsolated); Text("Isolated", style = MaterialTheme.typography.labelMedium) }
            }
        }
        state.memoryError?.let { error -> item(key = "memory-error") { ErrorLine(error) } }
        item(key = "graph") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 720.dp) {
                    Row(Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MemoryGraphPanel(nodes, state.memoryGraphEdges, graphSearch, revealedDepth, revealedEdges, selectedId, onSelect = { selectedId = it }, modifier = Modifier.weight(1.7f).fillMaxHeight(), loading = state.memoryLoading, includeIsolated = state.memoryGraphIsolated, truncated = state.memoryGraphSummary?.contains("truncated") == true)
                        DetailPanel(selectedNode, traceInfo[selectedId], neighbors, onSelect = { selectedId = it }, modifier = Modifier.weight(1f).fillMaxHeight(), bounded = true)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MemoryGraphPanel(nodes, state.memoryGraphEdges, graphSearch, revealedDepth, revealedEdges, selectedId, onSelect = { selectedId = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(400.dp), loading = state.memoryLoading, includeIsolated = state.memoryGraphIsolated, truncated = state.memoryGraphSummary?.contains("truncated") == true)
                        DetailPanel(selectedNode, traceInfo[selectedId], neighbors, onSelect = { selectedId = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
            }
        }
        memoryRecallItems(state, vm)
    }
    if (liveDialog) AlertDialog(
        onDismissRequest = { liveDialog = false },
        title = { Text("Live recall traces") },
        text = {
            if (state.memoryLiveTraces.isEmpty()) Text("No recalls observed yet. Ask the agent something that touches memory.")
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.memoryLiveTraces) { item ->
                    TextButton(onClick = { vm.selectMemoryTrace(item.trace); selectedId = null; liveDialog = false }) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text(item.trace.query.ifBlank { "(empty query)" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${item.trace.results.size} hits · depth ${item.trace.graphDepth} · ${relativeTime(item.receivedAt)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { liveDialog = false }) { Text("Close") } },
    )
}

private fun traceColor(depth: Int) = when (depth) { 0 -> Color(0xFF70D7BD); 1 -> Color(0xFFFFC66D); 2 -> Color(0xFFFF8E6E); else -> Color(0xFFD38BFF) }

@Composable
private fun LegendDot(label: String, color: Color, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (dashed) Color.Transparent else color).then(if (dashed) Modifier.border(1.dp, color, RoundedCornerShape(50)) else Modifier))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}

@Composable
private fun DetailPanel(node: MemoryGraphNode?, traceNode: MemoryTraceNode?, neighbors: List<MemoryGraphNode>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, bounded: Boolean = false) {
    var showAllNeighbors by remember(node?.id) { mutableStateOf(false) }
    Column(
        modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(CloverSurface)
            .then(if (bounded) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(16.dp),
    ) {
        if (node == null && traceNode == null) {
            Text("Node details", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text("Tap a node to inspect its note, trace scores, and connected neighbours. Pinch to zoom; drag to pan.", color = CloverText3, style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        val title = node?.title ?: traceNode?.ref ?: traceNode?.id.orEmpty()
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (node != null) {
            DetailValue("Links", (node.degree ?: 0).toString())
            node.category?.let { DetailValue("Category", it) }
            node.tier?.let { DetailValue("Tier", it) }
            node.importance?.let { DetailValue("Importance", "%.2f".format(it)) }
            if (node.resolved == false) DetailValue("Status", "No note yet")
            node.path?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = CloverText3, modifier = Modifier.padding(vertical = 5.dp)) }
            FlowTags(node.tags)
        } else Text("Not in the current graph view", color = CloverText3, style = MaterialTheme.typography.bodySmall)
        if (traceNode != null) {
            Text("This trace", style = MaterialTheme.typography.labelLarge, color = CloverAccent, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            traceNode.score?.let { DetailValue("Score", "%.3f".format(it)) }
            traceNode.directScore?.let { DetailValue("Direct", "%.3f".format(it)) }
            traceNode.graphScore?.let { DetailValue("Graph boost", "%.3f".format(it)) }
            traceNode.contentPreview?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CloverText2, maxLines = 5, overflow = TextOverflow.Ellipsis) }
        }
        if (neighbors.isNotEmpty()) {
            Text("Connected to ${neighbors.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Column {
                (if (showAllNeighbors) neighbors.take(40) else neighbors.take(6)).forEach { neighbor -> TextButton(onClick = { onSelect(neighbor.id) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text(neighbor.title ?: neighbor.id, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Text("${neighbor.degree ?: 0}", color = CloverText3)
                } }
                if (neighbors.size > 6) TextButton(onClick = { showAllNeighbors = !showAllNeighbors }) {
                    Text(if (showAllNeighbors) "Show fewer" else "Show more · ${neighbors.size - 6} remaining")
                }
                if (showAllNeighbors && neighbors.size > 40) Text("Showing first 40 of ${neighbors.size}", color = CloverText3, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun DetailValue(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = CloverText3, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable private fun FlowTags(tags: List<String>) { if (tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { tags.forEach { MetaChip("#$it") } } }

@Composable private fun TraceLayerRow(title: String, content: String, selected: Boolean, onClick: () -> Unit) { Surface(onClick = onClick, color = if (selected) CloverAccent.copy(alpha = .12f) else CloverSurface2, shape = RoundedCornerShape(8.dp)) { Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, fontWeight = FontWeight.SemiBold, color = CloverAccent, style = MaterialTheme.typography.labelSmall); Text(content.ifBlank { "(none matched directly)" }, color = CloverText2, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }

private fun LazyListScope.memoryRecallItems(state: AppUiState, vm: AppViewModel) {
    item(key = "recall-search") { RecallSearchBar(state.memoryQuery, state.memoryEntries.size, vm) }
    if (state.memoryEntries.isEmpty() && !state.memoryLoading && state.memoryError == null) {
        item(key = "recall-empty") {
            EmptyState(
                "没有匹配的记忆",
                "换个召回词再试。写入仍在已连接的电脑上完成。",
                Modifier.padding(horizontal = 16.dp),
                actionLabel = "回到聊天",
                onAction = { vm.navigate(com.luckyagent.android.ui.AppDestination.Chat) },
            )
        }
    }
    itemsIndexed(state.memoryEntries, key = { index, entry -> entry.id?.let { "memory-$it" } ?: "memory-index-$index" }) { index, entry ->
        val key = memoryCardKey(entry, index)
        if (hiddenSessionCard(state.hiddenSessionCards, state.settings.sessionId, SessionCardKind.Memory, key)) return@itemsIndexed
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MemoryEntryCard(entry) {
                vm.hideSessionCard(state.settings.sessionId, SessionCardKind.Memory, key)
            }
        }
    }
}

@Composable private fun RecallSearchBar(query: String, count: Int, vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(value = query, onValueChange = vm::updateMemoryQuery, singleLine = true, cursorBrush = SolidColor(CloverAccent), textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.refreshMemory() }), modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CloverSurface2).padding(10.dp), decorationBox = { inner -> if (query.isBlank()) Text("输入要召回的内容", color = CloverText3); inner() })
        IconButton(onClick = vm::refreshMemory) { Icon(Icons.Outlined.Search, contentDescription = "Recall") }
        Text("$count results", style = MaterialTheme.typography.titleSmall, color = CloverText2)
    }
}

private fun relativeTime(time: Long): String { val seconds = ((System.currentTimeMillis() - time).coerceAtLeast(0) / 1000).toInt(); return when { seconds < 60 -> "${seconds}s ago"; seconds < 3600 -> "${seconds / 60}m ago"; else -> "${seconds / 3600}h ago" } }

@Composable private fun StatPill(label: String, value: String) { Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) } }

internal fun memoryCardKey(entry: MemoryEntry, index: Int): String {
    val id = entry.id?.trim().orEmpty()
    if (id.isNotEmpty()) return id
    return "index-$index-${entry.content.orEmpty().hashCode()}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MemoryEntryCard(entry: MemoryEntry, onHide: (() -> Unit)? = null) {
    var confirmHide by remember { mutableStateOf(false) }
    if (confirmHide && onHide != null) {
        HideSessionCardDialog(
            name = "记忆",
            onDismiss = { confirmHide = false },
            onConfirm = {
                confirmHide = false
                onHide()
            },
        )
    }
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.category ?: "memory", style = MaterialTheme.typography.labelLarge, color = CloverAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (onHide != null) {
                IconButton(onClick = { confirmHide = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除记忆", modifier = Modifier.size(16.dp), tint = CloverText3)
                }
            }
            entry.tier?.let { MetaChip(it) }
            entry.importance?.let { Spacer(Modifier.width(6.dp)); MetaChip("imp ${"%.1f".format(it)}") }
        }
        Text(entry.content.orEmpty().ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
        if (entry.tags.isNotEmpty() || entry.stateKey != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.tags.forEach { MetaChip(it) }; entry.stateKey?.let { MetaChip("$it=${entry.stateValue ?: "?"}") }
            }
        }
        entry.id?.let { Text(it.take(12), style = MaterialTheme.typography.labelSmall, color = CloverText3) }
    }
}
