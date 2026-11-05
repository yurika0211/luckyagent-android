package com.luckyagent.android.ui.components

import android.provider.Settings
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.MemoryGraphEdge
import com.luckyagent.android.data.api.MemoryGraphNode
import com.luckyagent.android.ui.graph.*
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun MemoryGraphPanel(
    nodes: List<MemoryGraphNode>,
    edges: List<MemoryGraphEdge>,
    search: String,
    revealed: Map<String, Int>,
    revealedEdges: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    includeIsolated: Boolean = false,
    truncated: Boolean = false,
) {
    val nodeMap = remember(nodes) { nodes.associateBy { it.id } }
    val topology = remember(nodes, edges) {
        GraphTopology(nodes.map { it.id }, edges.map { GraphLink(it.source, it.target, it.weight ?: 1) })
    }
    var localRoot by remember { mutableStateOf<String?>(null) }
    var depth by remember { mutableIntStateOf(1) }
    var incoming by remember { mutableStateOf(true) }
    var outgoing by remember { mutableStateOf(true) }
    var showUnresolved by remember { mutableStateOf(true) }
    var forces by remember { mutableStateOf(GraphForces()) }
    var optionsOpen by remember { mutableStateOf(false) }
    var browserOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var paused by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    var reheat by remember { mutableIntStateOf(0) }
    var layoutActive by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf<GraphPin?>(null) }
    // Keep hidden nodes' positions so switching search/local views does not reshuffle the vault.
    var positions by remember { mutableStateOf(emptyMap<String, GraphPoint>()) }
    var camera by remember { mutableStateOf(GraphCamera()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var autoFit by remember { mutableStateOf(true) }
    val density = LocalDensity.current
    val padding = with(density) { 32.dp.toPx() }
    val hitRadius = with(density) { 24.dp.toPx() }
    val labelFontSize = MaterialTheme.typography.labelMedium.fontSize
    val labelSize = with(density) { labelFontSize.toPx() }
    val labelWidth = with(density) { 160.dp.toPx() }

    LaunchedEffect(topology) {
        if (localRoot !in topology.nodeIds) localRoot = null
        positions = positions.filterKeys { it in nodeMap }
    }
    val graph = remember(topology, search, localRoot, depth, incoming, outgoing, showUnresolved, includeIsolated) {
        val term = search.trim()
        val allowed = nodes.filter { node ->
            (showUnresolved || node.resolved != false) &&
                (term.isEmpty() || listOf(node.title.orEmpty(), node.category.orEmpty(), node.path.orEmpty(), node.id)
                    .plus(node.tags).any { it.contains(term, ignoreCase = true) })
        }.mapTo(hashSetOf()) { it.id }
        topology.view(allowed, localRoot, depth, incoming, outgoing,
            includeIsolated = includeIsolated || term.isNotEmpty())
    }
    val visibleIds = remember(graph) { graph.nodeIds.toSet() }
    val focusIds = remember(topology, selected) {
        if (selected == null) emptySet() else topology.neighbors[selected].orEmpty() + selected
    }
    val standingLabels = remember(graph) {
        graph.nodeIds.sortedByDescending { nodeMap[it]?.degree ?: 0 }.take(if (graph.nodeIds.size > 40) 14 else 40).toSet()
    }
    val labelPaint = remember {
        TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER }
    }
    val labels = remember(nodes, labelSize, labelWidth) {
        labelPaint.textSize = labelSize
        nodes.associate { node ->
            node.id to TextUtils.ellipsize(node.title ?: node.id, labelPaint, labelWidth, TextUtils.TruncateAt.END).toString()
        }
    }

    LaunchedEffect(graph, forces, paused, reheat, dragged?.id) {
        layoutActive = false
        if (graph.nodeIds.isEmpty()) return@LaunchedEffect
        val seeds = positions.filterKeys { it in nodeMap }
        val simulation = withContext(Dispatchers.Default) { ForceSimulation(graph, seeds, forces) }
        if (paused && seeds.isEmpty()) {
            // System reduced-motion users receive one settled frame, then the layout stops.
            positions = withContext(Dispatchers.Default) {
                var steps = 0
                while (simulation.active && steps < 300) {
                    ensureActive()
                    simulation.tick()
                    steps++
                }
                simulation.snapshot()
            }
            return@LaunchedEffect
        }
        positions = seeds + simulation.snapshot()
        if (paused) return@LaunchedEffect
        layoutActive = true
        try {
            var lastTick = 0L
            while (simulation.active) {
                val time = withFrameNanos { it }
                // High refresh-rate screens should not speed up cooling or double CPU use.
                if (time - lastTick < 16_000_000L) continue
                lastTick = time
                val pin = dragged
                val frame = withContext(Dispatchers.Default) {
                    if (simulation.active || pin != null) simulation.tick(pin)
                    simulation.snapshot()
                }
                if (!simulation.active && pin == null) break
                positions = positions.filterKeys { it in nodeMap } + frame
                // A pointer update during a worker tick must not be overwritten by that old pin.
                dragged?.let { positions = positions + (it.id to it.point) }
            }
        } finally {
            layoutActive = false
        }
    }
    LaunchedEffect(positions, graph, viewport, autoFit, padding) {
        if (autoFit && viewport.width > 0 && viewport.height > 0) {
            camera = GraphCamera.fit(positions.filterKeys { it in visibleIds }.values,
                viewport.width.toFloat(), viewport.height.toFloat(), padding)
        }
    }

    fun zoom(factor: Float) {
        autoFit = false
        camera = camera.transform(GraphPoint(viewport.width / 2f, viewport.height / 2f), GraphPoint(0f, 0f),
            factor, viewport.width.toFloat(), viewport.height.toFloat())
    }
    fun browseSelect(id: String) {
        onSelect(id)
        positions[id]?.let { camera = camera.copy(center = it, scale = max(camera.scale, .9f)); autoFit = false }
        browserOpen = false
    }

    val latestGraph by rememberUpdatedState(graph)
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestNodeMap by rememberUpdatedState(nodeMap)
    Column(modifier.shadow(2.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(CloverSurface)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${graph.nodeIds.size} / ${topology.nodeIds.size} nodes · ${graph.links.size} links" +
                    if (truncated) " · partial vault" else "",
                    style = MaterialTheme.typography.labelMedium, color = CloverText2)
                localRoot?.let { root ->
                    Text("Around ${nodeMap[root]?.title ?: root}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall, color = CloverText3)
                }
            }
            TextButton(onClick = { browserOpen = true }, enabled = graph.nodeIds.isNotEmpty()) { Text("Notes") }
            TextButton(onClick = { optionsOpen = true }) { Text("Options") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { autoFit = true }) { Text("Fit") }
            TextButton(onClick = { zoom(.8f) }, modifier = Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
            TextButton(onClick = { zoom(1.25f) }, modifier = Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
            TextButton(onClick = { if (!paused && !layoutActive) reheat++ else paused = !paused }) {
                Text(if (paused) "Resume layout" else if (layoutActive) "Pause layout" else "Relax layout")
            }
            FilterChip(localRoot == null, { localRoot = null; autoFit = true }, label = { Text("All notes") })
            FilterChip(localRoot != null, { localRoot = selected; autoFit = true }, enabled = selected != null && selected in nodeMap,
                label = { Text(if (localRoot == null) "Around selected" else "$depth-hop local") })
        }
        if (graph.nodeIds.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (loading) "Loading memory graph…" else if (nodes.isEmpty()) "No linked memories yet"
                    else "No notes match these filters", color = CloverText3, style = MaterialTheme.typography.bodySmall)
            }
        } else Canvas(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewport = it }
            .semantics {
                contentDescription = "Memory graph, ${graph.nodeIds.size} nodes and ${graph.links.size} links. Use Notes to select a node without gestures."
                customActions = listOf(
                    CustomAccessibilityAction("Browse graph notes") { browserOpen = true; true },
                    CustomAccessibilityAction("Fit graph") { autoFit = true; true },
                )
            }
            .pointerInput(Unit) {
                fun world(point: Offset) = camera.toWorld(GraphPoint(point.x, point.y), size.width.toFloat(), size.height.toFloat())
                fun hit(point: Offset): String? = latestGraph.nodeIds.mapNotNull { id ->
                    positions[id]?.let { p ->
                        val screen = camera.toScreen(p, size.width.toFloat(), size.height.toFloat())
                        val distance = hypot(screen.x - point.x, screen.y - point.y)
                        val radius = ForceSimulation.nodeRadius(latestNodeMap[id]?.degree ?: 0) * camera.scale
                        if (distance <= max(radius, hitRadius)) id to distance else null
                    }
                }.minByOrNull { it.second }?.first
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hitId = hit(down.position)
                    val startPoint = hitId?.let { positions[it] }
                    val downWorld = world(down.position)
                    if (hitId != null) autoFit = false
                    var moved = false
                    var multiTouch = false
                    var canceled = false
                    var totalPan = Offset.Zero
                    var totalZoom = 1f
                    try {
                        do {
                            val event = awaitPointerEvent()
                            canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                totalPan += pan
                                totalZoom *= zoom
                                if (event.changes.count { it.pressed } > 1) {
                                    multiTouch = true
                                    dragged = null
                                }
                                if (!moved) moved = totalPan.getDistance() > viewConfiguration.touchSlop ||
                                    abs(1f - totalZoom) * event.calculateCentroidSize(false) > viewConfiguration.touchSlop
                                if (moved) {
                                    autoFit = false
                                    val pointer = event.changes.firstOrNull { it.id == down.id && it.pressed }
                                    if (!multiTouch && hitId != null && startPoint != null && pointer != null) {
                                        val point = world(pointer.position)
                                        val pin = GraphPoint(startPoint.x + point.x - downWorld.x, startPoint.y + point.y - downWorld.y)
                                        if (dragged == null) latestOnSelect(hitId)
                                        dragged = GraphPin(hitId, pin)
                                        positions = positions + (hitId to pin)
                                    } else {
                                        val centroid = event.calculateCentroid(false)
                                        if (centroid != Offset.Unspecified) camera = camera.transform(
                                            GraphPoint(centroid.x, centroid.y), GraphPoint(pan.x, pan.y), zoom,
                                            size.width.toFloat(), size.height.toFloat())
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                            if (!event.changes.any { it.pressed }) {
                                if (!canceled && !moved && !multiTouch) {
                                    latestOnSelect(if (hitId == latestSelected) null else hitId)
                                    event.changes.forEach { it.consume() }
                                }
                                break
                            }
                        } while (!canceled)
                    } finally {
                        dragged = null
                    }
                }
            }) {
            val width = size.width
            val height = size.height
            fun screen(point: GraphPoint): Offset = camera.toScreen(point, width, height).let { Offset(it.x, it.y) }
            graph.links.forEach { edge ->
                val from = positions[edge.source] ?: return@forEach
                val to = positions[edge.target] ?: return@forEach
                val a = screen(from)
                val b = screen(to)
                if ((a.x < 0 && b.x < 0) || (a.x > width && b.x > width) ||
                    (a.y < 0 && b.y < 0) || (a.y > height && b.y > height)) return@forEach
                val hop = revealedEdges["${edge.source}|${edge.target}"] ?: revealedEdges["${edge.target}|${edge.source}"]
                val color = when {
                    hop != null -> traceColor(hop)
                    revealed.isNotEmpty() -> CloverLine.copy(alpha = .25f)
                    selected == edge.source || selected == edge.target -> CloverAccent
                    selected != null -> CloverLine.copy(alpha = .25f)
                    else -> CloverLine
                }
                drawLine(color, a, b, strokeWidth = (edge.weight.coerceIn(1, 4) * .65f).dp.toPx())
            }
            labelPaint.textSize = labelSize
            graph.nodeIds.forEach { id ->
                val node = nodeMap[id] ?: return@forEach
                val point = positions[id] ?: return@forEach
                val p = screen(point)
                val radius = max(ForceSimulation.nodeRadius(node.degree ?: 0) * camera.scale, 4.dp.toPx())
                if (p.x + radius + 80.dp.toPx() < 0 || p.x - radius - 80.dp.toPx() > width ||
                    p.y + radius + labelSize * 2 < 0 || p.y - radius - labelSize * 2 > height) return@forEach
                val fill = revealed[id]?.let(::traceColor) ?: when {
                    (node.degree ?: 0) >= 12 -> Color(0xFF6E9EFF)
                    (node.degree ?: 0) >= 6 -> Color(0xFF55B8A0)
                    (node.degree ?: 0) >= 3 -> CloverAccent
                    else -> Color(0xFF74839B)
                }
                val alpha = if (focusIds.isNotEmpty() && id !in focusIds && id !in revealed) .3f else 1f
                if (node.resolved != false) drawCircle(fill.copy(alpha = alpha), radius, p)
                else drawCircle(CloverAccent.copy(alpha = alpha), radius, p, style = Stroke(2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                if (id == selected || id == localRoot) drawCircle(CloverAccent, radius + 4.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                if (id == selected || id == localRoot || id in revealed || id in standingLabels || camera.scale > 1.5f) {
                    labelPaint.color = CloverText2.copy(alpha = alpha).toArgb()
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(labels.getValue(id),
                            p.x, p.y + radius + labelSize + 4.dp.toPx(), labelPaint)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (revealed.isEmpty()) {
                LegendDot("0–2 links", Color(0xFF74839B))
                LegendDot("3–5", CloverAccent)
                LegendDot("6–11", Color(0xFF55B8A0))
                LegendDot("12+", Color(0xFF6E9EFF))
            } else (0..3).forEach { LegendDot(if (it == 0) "seed" else "hop $it", traceColor(it)) }
            LegendDot("unresolved", CloverAccent, hollow = true)
        }
    }

    if (browserOpen) AlertDialog(
        onDismissRequest = { browserOpen = false },
        title = { Text("Graph notes · ${graph.nodeIds.size}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(graph.nodeIds, key = { it }) { id ->
                    val node = nodeMap.getValue(id)
                    TextButton(onClick = { browseSelect(id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(node.title ?: id, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${node.degree ?: 0} links · ${if (node.resolved == false) "Unresolved" else node.category ?: "Note"}",
                                style = MaterialTheme.typography.labelSmall, color = CloverText3)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { browserOpen = false }) { Text("Close") } },
    )
    if (optionsOpen) AlertDialog(
        onDismissRequest = { optionsOpen = false },
        title = { Text("Graph options") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphToggle("Show unresolved links", showUnresolved) { showUnresolved = it }
                Text("Local graph", style = MaterialTheme.typography.titleSmall)
                Text("Select a note, then choose Around selected. The centre stays visible while filtering.",
                    style = MaterialTheme.typography.bodySmall, color = CloverText3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { hops -> FilterChip(depth == hops, { depth = hops }, label = { Text("$hops hop") }) }
                }
                GraphToggle("Incoming links", incoming) { incoming = it }
                GraphToggle("Outgoing links", outgoing) { outgoing = it }
                Text("Layout forces", style = MaterialTheme.typography.titleSmall)
                ForceSlider("Centre attraction", forces.center, 0f..0.2f) { forces = forces.copy(center = it) }
                ForceSlider("Repulsion", forces.repulsion, 0f..3000f) { forces = forces.copy(repulsion = it) }
                ForceSlider("Link attraction", forces.linkStrength, 0f..1f) { forces = forces.copy(linkStrength = it) }
                ForceSlider("Link distance", forces.linkDistance, 30f..400f) { forces = forces.copy(linkDistance = it) }
                Text("Drag a note to move it. Drag empty space to pan; pinch to zoom. Positions affect only this view, not your notes.",
                    style = MaterialTheme.typography.bodySmall, color = CloverText3)
            }
        },
        dismissButton = { TextButton(onClick = {
            forces = GraphForces(); showUnresolved = true; incoming = true; outgoing = true; depth = 1; localRoot = null; autoFit = true
        }) { Text("Restore defaults") } },
        confirmButton = { TextButton(onClick = { optionsOpen = false }) { Text("Done") } },
    )
}

@Composable
private fun GraphToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked, onCheckedChange = null)
    }
}

@Composable
private fun ForceSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
}

private fun traceColor(depth: Int) = when (depth) {
    0 -> Color(0xFF70D7BD)
    1 -> Color(0xFFFFC66D)
    2 -> Color(0xFFFF8E6E)
    else -> Color(0xFFD38BFF)
}

@Composable
private fun LegendDot(label: String, color: Color, hollow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).then(
            if (hollow) Modifier.border(1.dp, color, RoundedCornerShape(50)) else Modifier.background(color)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}
