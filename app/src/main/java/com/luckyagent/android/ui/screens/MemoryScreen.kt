package com.luckyagent.android.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
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
import com.luckyagent.android.ui.components.MetaChip
import com.luckyagent.android.ui.components.ScreenHeader
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.*

private data class GraphPoint(val x: Float, val y: Float)
private data class PositionedNode(val node: MemoryGraphNode, val center: Offset, val radius: Float, val label: String)
private data class PositionedEdge(val edge: MemoryGraphEdge, val index: Int, val from: Offset, val to: Offset)

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
        Modifier.fillMaxSize().background(CloverBg),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                eyebrow = "Vault",
                title = "Memory graph",
                subtitle = "Knowledge graph and recall paths",
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
                        decorationBox = { inner -> if (state.memoryTraceQuery.isEmpty()) Text("Trace a memory query…", color = CloverText3); inner() },
                    )
                    Button(onClick = { vm.runMemoryTrace() }, enabled = !state.memoryTraceLoading && state.memoryTraceQuery.isNotBlank()) {
                        Text(if (state.memoryTraceLoading) "Tracing…" else "Trace")
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
                    TextButton(onClick = { playhead = 0; playing = false }) { Text("⟲ Reset") }
                    TextButton(onClick = { playhead = (playhead - 1).coerceAtLeast(0); playing = false }, enabled = playhead > 0) { Text("◀ Step") }
                    Button(onClick = { if (playhead >= maxDepth) playhead = 0; playing = !playing }) { Text(if (playing) "Ⅱ Pause" else "▶ Play") }
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
                BasicTextField(value = graphSearch, onValueChange = { graphSearch = it }, singleLine = true, cursorBrush = SolidColor(CloverAccent), textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CloverSurface2).padding(10.dp), decorationBox = { inner -> if (graphSearch.isBlank()) Text("Search title, category, tag", color = CloverText3); inner() })
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(state.memoryGraphIsolated, vm::setMemoryGraphIsolated); Text("Isolated", style = MaterialTheme.typography.labelMedium) }
            }
        }
        state.memoryError?.let { error -> item(key = "memory-error") { ErrorLine(error) } }
        item(key = "graph") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 720.dp) {
                    Row(Modifier.fillMaxWidth().height(500.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GraphPanel(nodes, state.memoryGraphEdges, graphSearch, revealedDepth, revealedEdges, selectedId, onSelect = { selectedId = if (selectedId == it) null else it }, modifier = Modifier.weight(1.7f).fillMaxHeight(), loading = state.memoryLoading)
                        DetailPanel(selectedNode, traceInfo[selectedId], neighbors, onSelect = { selectedId = it }, modifier = Modifier.weight(1f).fillMaxHeight(), bounded = true)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GraphPanel(nodes, state.memoryGraphEdges, graphSearch, revealedDepth, revealedEdges, selectedId, onSelect = { selectedId = if (selectedId == it) null else it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(370.dp), loading = state.memoryLoading)
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

@Composable
private fun GraphPanel(nodes: List<MemoryGraphNode>, edges: List<MemoryGraphEdge>, search: String, revealed: Map<String, Int>, revealedEdges: Map<String, Int>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var positions by remember(nodes, edges) { mutableStateOf(emptyMap<String, GraphPoint>()) }
    val focusIds = remember(selected, edges) {
        if (selected == null) emptySet() else buildSet { add(selected); edges.forEach { edge -> if (edge.source == selected) add(edge.target); if (edge.target == selected) add(edge.source) } }
    }
    val standingLabels = remember(nodes) {
        val cap = if (nodes.size > 120) 14 else if (nodes.size > 40) 20 else nodes.size
        nodes.sortedByDescending { it.degree ?: 0 }.take(cap).filter { (it.degree ?: 0) > 0 }.mapTo(hashSetOf()) { it.id }
    }
    val positionedNodes = remember(nodes, positions) {
        nodes.mapNotNull { node ->
            positions[node.id]?.let { point ->
                val radius = (6f + sqrt((node.degree ?: 0).toFloat()) * 3.4f).coerceAtMost(26f)
                val title = node.title ?: node.id
                PositionedNode(node, Offset(point.x, point.y), radius, if (title.length > 22) title.take(21) + "…" else title)
            }
        }
    }
    val positionedEdges = remember(edges, positions) {
        edges.mapIndexedNotNull { index, edge ->
            val from = positions[edge.source] ?: return@mapIndexedNotNull null
            val to = positions[edge.target] ?: return@mapIndexedNotNull null
            PositionedEdge(edge, index, Offset(from.x, from.y), Offset(to.x, to.y))
        }
    }
    val traceEdgeDepths = remember(edges, revealedEdges) {
        IntArray(edges.size) { index ->
            val edge = edges[index]
            revealedEdges["${edge.source}|${edge.target}"] ?: revealedEdges["${edge.target}|${edge.source}"] ?: -1
        }
    }
    val searchTerm = remember(search) { search.trim().lowercase() }
    val searchMatches = remember(nodes, searchTerm) {
        if (searchTerm.isEmpty()) emptySet() else nodes.filter { node ->
            sequenceOf(node.title.orEmpty(), node.category.orEmpty()).any { it.contains(searchTerm, ignoreCase = true) } ||
                node.tags.any { it.contains(searchTerm, ignoreCase = true) }
        }.mapTo(hashSetOf()) { it.id }
    }
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val unresolvedStroke = remember { androidx.compose.ui.graphics.drawscope.Stroke(2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 4f))) }
    val selectedUnresolvedStroke = remember { androidx.compose.ui.graphics.drawscope.Stroke(3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 4f))) }
    val selectedStroke = remember { androidx.compose.ui.graphics.drawscope.Stroke(3f) }
    LaunchedEffect(nodes, edges) { positions = withContext(Dispatchers.Default) { forceLayout(nodes, edges) } }
    Column(modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(CloverSurface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Knowledge topology · ${nodes.size} nodes / ${edges.size} links", style = MaterialTheme.typography.labelMedium, color = CloverText2, modifier = Modifier.weight(1f))
            TextButton(onClick = { zoom = (zoom * 1.2f).coerceAtMost(4f) }) { Text("+") }
            TextButton(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(.35f) }) { Text("−") }
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Reset") }
        }
        val zoomState = rememberUpdatedState(zoom)
        val panState = rememberUpdatedState(pan)
        val onSelectState = rememberUpdatedState(onSelect)
        if (nodes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (loading) "Loading memory graph…" else "No graph nodes to display", color = CloverText3, style = MaterialTheme.typography.bodySmall)
            }
        } else Canvas(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
            detectTransformGestures { centroid, gesturePan, gestureZoom, _ ->
                val next = (zoomState.value * gestureZoom).coerceIn(.35f, 4f)
                pan = (panState.value - centroid) * (next / zoomState.value) + centroid + gesturePan
                zoom = next
            }
        }.pointerInput(positionedNodes) {
            detectTapGestures { tap ->
                val scale = min(size.width / 1200f, size.height / 820f).coerceAtLeast(.001f) * zoomState.value
                val x = (tap.x - size.width / 2f - panState.value.x) / scale + 600f
                val y = (tap.y - size.height / 2f - panState.value.y) / scale + 410f
                val hit = positionedNodes.minByOrNull { hypot(it.center.x - x, it.center.y - y) }
                if (hit != null && hypot(hit.center.x - x, hit.center.y - y) < max(hit.radius, 20f / scale)) onSelectState.value(hit.node.id)
            }
        }) {
            val scaleFactor = min(size.width / 1200f, size.height / 820f).coerceAtLeast(.001f) * zoom
            labelPaint.textSize = 13f / scaleFactor
            val left = 600f - (size.width / 2f + pan.x) / scaleFactor
            val right = 600f + (size.width / 2f - pan.x) / scaleFactor
            val top = 410f - (size.height / 2f + pan.y) / scaleFactor
            val bottom = 410f + (size.height / 2f - pan.y) / scaleFactor
            withTransform({ translate(size.width / 2f + pan.x - 600f * scaleFactor, size.height / 2f + pan.y - 410f * scaleFactor); scale(scaleFactor, scaleFactor, Offset.Zero) }) {
                positionedEdges.forEach { item ->
                    val edge = item.edge
                    val a = item.from
                    val b = item.to
                    if ((a.x < left && b.x < left) || (a.x > right && b.x > right) ||
                        (a.y < top && b.y < top) || (a.y > bottom && b.y > bottom)) return@forEach
                    val traceHopDepth = traceEdgeDepths[item.index]
                    val color = when {
                        revealed.isNotEmpty() && traceHopDepth < 0 -> CloverLine.copy(alpha = .22f)
                        traceHopDepth >= 0 -> traceColor(traceHopDepth)
                        focusIds.isNotEmpty() && (edge.source !in focusIds || edge.target !in focusIds) -> CloverLine.copy(alpha = .22f)
                        else -> CloverLine.copy(alpha = .75f)
                    }
                    drawLine(color, a, b, strokeWidth = (edge.weight ?: 1).toFloat().coerceIn(1f, 4f))
                }
                positionedNodes.forEach { item ->
                    val node = item.node
                    val p = item.center
                    val radius = max(item.radius, 5f / scaleFactor)
                    if (p.x + radius + 30f < left || p.x - radius - 30f > right ||
                        p.y + radius + 30f < top || p.y - radius - 30f > bottom) return@forEach
                    val degree = node.degree ?: 0
                    val isMatch = node.id in searchMatches
                    val isSelected = selected == node.id
                    val fill = when {
                        revealed[node.id] == 0 -> Color(0xFF70D7BD)
                        revealed[node.id] != null -> traceColor(revealed.getValue(node.id))
                        degree >= 12 -> Color(0xFF6E9EFF)
                        degree >= 6 -> Color(0xFF55B8A0)
                        degree >= 3 -> CloverAccent
                        else -> Color(0xFF74839B)
                    }
                    val alpha = if ((searchTerm.isNotEmpty() && !isMatch) || (focusIds.isNotEmpty() && node.id !in focusIds)) .28f else 1f
                    if (node.resolved != false) drawCircle(fill.copy(alpha = alpha), radius, p)
                    if (isSelected || node.resolved == false) {
                        val outline = when {
                            node.resolved != false -> selectedStroke
                            isSelected -> selectedUnresolvedStroke
                            else -> unresolvedStroke
                        }
                        drawCircle(if (node.resolved == false) CloverAccent.copy(alpha = alpha) else fill.copy(alpha = alpha), radius, p, style = outline)
                    }
                    if (isSelected || isMatch || node.id in standingLabels || revealed[node.id] != null) {
                        drawIntoCanvas { canvas ->
                            labelPaint.color = CloverText2.toArgb()
                            labelPaint.alpha = (alpha * 255).roundToInt()
                            canvas.nativeCanvas.drawText(item.label, p.x, p.y + radius + 16f / scaleFactor, labelPaint)
                        }
                    }
                }
            }
        }
        if (nodes.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (revealed.isEmpty()) listOf("1–2 links" to Color(0xFF74839B), "3–5" to CloverAccent, "6–11" to Color(0xFF55B8A0), "12+" to Color(0xFF6E9EFF)).forEach { LegendDot(it.first, it.second) }
            else (0..3).forEach { LegendDot(if (it == 0) "seed" else "hop $it", traceColor(it)) }
            LegendDot("unresolved", CloverAccent, dashed = true)
        }
    }
}

private fun traceColor(depth: Int) = when (depth) { 0 -> Color(0xFF70D7BD); 1 -> Color(0xFFFFC66D); 2 -> Color(0xFFFF8E6E); else -> Color(0xFFD38BFF) }

@Composable
private fun LegendDot(label: String, color: Color, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(if (dashed) Color.Transparent else color).then(if (dashed) Modifier.border(1.dp, color, RoundedCornerShape(50)) else Modifier))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}

private suspend fun forceLayout(nodes: List<MemoryGraphNode>, edges: List<MemoryGraphEdge>): Map<String, GraphPoint> {
    if (nodes.isEmpty()) return emptyMap()
    val index = nodes.mapIndexed { i, n -> n.id to i }.toMap()
    val count = nodes.size
    val x = FloatArray(count) { i -> 600f + cos(i * 2.39996).toFloat() * (70f + sqrt(i.toFloat()) * 26f) }
    val y = FloatArray(count) { i -> 410f + sin(i * 2.39996).toFloat() * (48f + sqrt(i.toFloat()) * 19f) }
    val vx = FloatArray(count); val vy = FloatArray(count)
    val fx = FloatArray(count); val fy = FloatArray(count)
    val links = edges.mapNotNull { e -> val a = index[e.source]; val b = index[e.target]; if (a == null || b == null) null else a to b }
    val iterations = if (count > 220) 100 else 180
    for (step in 0 until iterations) {
        if (step % 16 == 0) currentCoroutineContext().ensureActive()
        fx.fill(0f); fy.fill(0f)
        for (i in 0 until count) for (j in i + 1 until count) {
            val dx = x[i] - x[j]; val dy = y[i] - y[j]
            val d2 = (dx * dx + dy * dy).coerceAtLeast(20f); val force = 7000f / d2
            val inv = 1f / sqrt(d2); val ax = dx * inv * force; val ay = dy * inv * force
            fx[i] += ax; fy[i] += ay; fx[j] -= ax; fy[j] -= ay
        }
        links.forEach { (a, b) ->
            val dx = x[b] - x[a]; val dy = y[b] - y[a]; val d = hypot(dx, dy).coerceAtLeast(1f)
            val f = (d - 100f) * .025f; val ax = dx / d * f; val ay = dy / d * f
            fx[a] += ax; fy[a] += ay; fx[b] -= ax; fy[b] -= ay
        }
        for (i in 0 until count) {
            val hub = 1f + sqrt((nodes[i].degree ?: 0).toFloat()) * .1f
            fx[i] += (600f - x[i]) * .002f * hub; fy[i] += (410f - y[i]) * .002f * hub
            vx[i] = ((vx[i] + fx[i]) * .82f).coerceIn(-18f, 18f); vy[i] = ((vy[i] + fy[i]) * .82f).coerceIn(-18f, 18f)
            x[i] = (x[i] + vx[i]).coerceIn(35f, 1165f); y[i] = (y[i] + vy[i]).coerceIn(35f, 785f)
        }
    }
    return nodes.indices.associate { nodes[it].id to GraphPoint(x[it], y[it]) }
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
        item(key = "recall-empty") { EmptyState("No memories matched", "Try another recall query. Write/remember stays on the host runtime.", Modifier.padding(horizontal = 16.dp)) }
    }
    itemsIndexed(state.memoryEntries, key = { index, entry -> entry.id?.let { "memory-$it" } ?: "memory-index-$index" }) { _, entry ->
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MemoryEntryCard(entry) }
    }
}

@Composable private fun RecallSearchBar(query: String, count: Int, vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(value = query, onValueChange = vm::updateMemoryQuery, singleLine = true, cursorBrush = SolidColor(CloverAccent), textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.refreshMemory() }), modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CloverSurface2).padding(10.dp), decorationBox = { inner -> if (query.isBlank()) Text("Recall query…", color = CloverText3); inner() })
        IconButton(onClick = vm::refreshMemory) { Icon(Icons.Outlined.Search, contentDescription = "Recall") }
        Text("$count results", style = MaterialTheme.typography.titleSmall, color = CloverText2)
    }
}

private fun relativeTime(time: Long): String { val seconds = ((System.currentTimeMillis() - time).coerceAtLeast(0) / 1000).toInt(); return when { seconds < 60 -> "${seconds}s ago"; seconds < 3600 -> "${seconds / 60}m ago"; else -> "${seconds / 3600}h ago" } }

@Composable private fun StatPill(label: String, value: String) { Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) } }

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MemoryEntryCard(entry: MemoryEntry) {
    CloverCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.category ?: "memory", style = MaterialTheme.typography.labelLarge, color = CloverAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            entry.tier?.let { MetaChip(it) }
            entry.importance?.let { Spacer(Modifier.width(6.dp)); MetaChip("imp ${"%.1f".format(it)}") }
        }
        Text(entry.content.orEmpty().ifBlank { "(empty)" }, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.tags.forEach { MetaChip(it) }; entry.stateKey?.let { MetaChip("$it=${entry.stateValue ?: "?"}") }; entry.id?.let { MetaChip(it.take(12)) }
        }
    }
}
