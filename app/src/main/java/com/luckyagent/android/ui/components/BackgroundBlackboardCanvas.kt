package com.luckyagent.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.luckyagent.android.data.api.AutonomyDashboardResponse
import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.executionSession
import com.luckyagent.android.ui.graph.*
import kotlin.math.max

/** A read-only projection of the existing autonomy dashboard, with the shared graph camera. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundBlackboardCanvas(
    dashboard: AutonomyDashboardResponse?,
    tasks: List<AutonomyTaskSummary>,
    loading: Boolean,
    onOpenTask: (String) -> Unit,
    onOpenSession: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = remember(dashboard, tasks) { buildBackgroundBoardGraph(dashboard, tasks) }
    var camera by remember { mutableStateOf(GraphCamera()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var autoFit by remember { mutableStateOf(true) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var browserOpen by remember { mutableStateOf(false) }
    val selected = graph.nodes.firstOrNull { it.id == selectedId }
    val density = LocalDensity.current
    val padding = with(density) { 18.dp.toPx() }
    val latestGraph by rememberUpdatedState(graph)
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val titleStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface)
    val secondaryStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)

    LaunchedEffect(graph.bounds, viewport, autoFit, padding) {
        if (autoFit && viewport.width > 0 && viewport.height > 0) {
            camera = GraphCamera.fit(graph.bounds, viewport.width.toFloat(), viewport.height.toFloat(), padding)
        }
    }
    LaunchedEffect(graph.nodes.map { it.id }) {
        autoFit = true
        if (selectedId != null && selected == null) selectedId = null
    }
    fun zoom(factor: Float) {
        autoFit = false
        camera = camera.transform(GraphPoint(viewport.width / 2f, viewport.height / 2f), GraphPoint(0f, 0f),
            factor, viewport.width.toFloat(), viewport.height.toFloat())
    }
    fun browse(node: BackgroundBoardNode) {
        browserOpen = false
        selectedId = node.id
        autoFit = false
        camera = GraphCamera(GraphPoint(node.position.x + node.width / 2, node.position.y + node.height / 2),
            max(camera.scale, density.density))
    }

    Column(modifier.background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("协作关系 · ${tasks.size} 任务", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { autoFit = true }) {
                Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "适应黑板画布")
            }
            IconButton(onClick = { zoom(.8f) }) {
                Icon(Icons.Outlined.ZoomOut, contentDescription = "缩小黑板画布")
            }
            IconButton(onClick = { zoom(1.25f) }) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = "放大黑板画布")
            }
            IconButton(onClick = { browserOpen = true }) {
                Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = "浏览黑板节点")
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
                .semantics {
                    contentDescription = "后台协作黑板，${graph.nodes.size} 个节点，${graph.edges.size} 条关系。可通过浏览黑板节点选择任务或 Worker。"
                    customActions = listOf(
                        CustomAccessibilityAction("浏览黑板节点") { browserOpen = true; true },
                        CustomAccessibilityAction("适应黑板画布") { autoFit = true; true },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val world = camera.toWorld(GraphPoint(position.x, position.y),
                            size.width.toFloat(), size.height.toFloat())
                        selectedId = latestGraph.nodes.lastOrNull { it.contains(world) }?.id
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        autoFit = false
                        camera = camera.transform(GraphPoint(centroid.x, centroid.y), GraphPoint(pan.x, pan.y), zoom,
                            size.width.toFloat(), size.height.toFloat())
                    }
                }) {
                fun screen(point: GraphPoint): Offset = camera.toScreen(point, size.width, size.height).let { Offset(it.x, it.y) }
                val grid = (24f * camera.scale).coerceAtLeast(12.dp.toPx())
                val origin = screen(GraphPoint(0f, 0f))
                var x = ((origin.x % grid) + grid) % grid
                while (x < size.width) {
                    var y = ((origin.y % grid) + grid) % grid
                    while (y < size.height) {
                        drawCircle(colors.outlineVariant.copy(alpha = .65f), .8.dp.toPx(), Offset(x, y))
                        y += grid
                    }
                    x += grid
                }
                val nodeMap = graph.nodes.associateBy { it.id }
                graph.edges.forEach { edge ->
                    val from = nodeMap[edge.from] ?: return@forEach
                    val to = nodeMap[edge.to] ?: return@forEach
                    val start = screen(GraphPoint(from.position.x + from.width / 2, from.position.y + from.height))
                    val isTask = to.kind == BackgroundNodeKind.Task
                    val unassigned = isTask && from.kind == BackgroundNodeKind.Blackboard
                    val left = to.position.x + to.width / 2 < 276f
                    val end = if (isTask) screen(GraphPoint(
                        if (unassigned == left) to.position.x else to.position.x + to.width,
                        to.position.y + to.height / 2,
                    )) else screen(GraphPoint(to.position.x + to.width / 2, to.position.y))
                    val middle = (start.y + end.y) / 2
                    val color = boardStatusColor(to.status, colors).copy(alpha = if (edge.historical) .5f else .7f)
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        if (isTask) {
                            // Route through the gap (or outer margin for unclaimed work), never through another card.
                            val bus = screen(GraphPoint(if (unassigned) { if (left) 0f else 552f } else 276f, 0f)).x
                            val departure = start.y + 12f * camera.scale
                            cubicTo(start.x, departure, bus, departure, bus, departure)
                            lineTo(bus, end.y)
                            lineTo(end.x, end.y)
                        } else cubicTo(start.x, middle, end.x, middle, end.x, end.y)
                    }
                    drawPath(path,
                        color, style = Stroke(1.3.dp.toPx(), pathEffect = if (edge.historical)
                            PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null))
                    val arrow = 4.dp.toPx()
                    if (isTask) {
                        val direction = if (unassigned == left) -1 else 1
                        drawLine(color, end, Offset(end.x + direction * arrow * 1.5f, end.y - arrow), 1.3.dp.toPx())
                        drawLine(color, end, Offset(end.x + direction * arrow * 1.5f, end.y + arrow), 1.3.dp.toPx())
                    } else {
                        drawLine(color, end, Offset(end.x - arrow, end.y - arrow * 1.5f), 1.3.dp.toPx())
                        drawLine(color, end, Offset(end.x + arrow, end.y - arrow * 1.5f), 1.3.dp.toPx())
                    }
                }
                graph.nodes.forEach { node ->
                    val top = screen(node.position)
                    val width = node.width * camera.scale
                    val height = node.height * camera.scale
                    if (top.x + width < 0 || top.y + height < 0 || top.x > size.width || top.y > size.height) return@forEach
                    val color = boardStatusColor(node.status, colors)
                    val radius = CornerRadius(minOf(10.dp.toPx(), width / 8))
                    drawRoundRect(colors.surface, top, Size(width, height), radius)
                    if (node.kind == BackgroundNodeKind.Blackboard) {
                        drawRoundRect(color.copy(alpha = .07f), top, Size(width, height), radius)
                    }
                    drawRoundRect(color.copy(alpha = if (node.id == selectedId) 1f else .45f), top, Size(width, height),
                        radius, style = Stroke(if (node.id == selectedId) 2.dp.toPx() else 1.dp.toPx()))
                    if (width < 72.dp.toPx() || height < 38.dp.toPx()) return@forEach
                    val inset = 8.dp.toPx()
                    val textWidth = (width - inset * 2).toInt().coerceAtLeast(1)
                    val label = when (node.kind) {
                        BackgroundNodeKind.Blackboard -> "BLACKBOARD"
                        BackgroundNodeKind.Worker -> "WORKER · ${backgroundStatusLabel(node.status)}"
                        BackgroundNodeKind.Task -> backgroundStatusLabel(node.status)
                    }
                    val badge = textMeasurer.measure(label, secondaryStyle.copy(color = color), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = textWidth))
                    var textY = top.y + inset
                    drawText(badge, topLeft = Offset(top.x + inset, textY))
                    textY += badge.size.height + 2.dp.toPx()
                    val remaining = height - (textY - top.y) - inset
                    if (remaining < 12.dp.toPx()) return@forEach
                    val title = textMeasurer.measure(node.title, titleStyle, maxLines = if (node.kind == BackgroundNodeKind.Task) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        constraints = Constraints(maxWidth = textWidth, maxHeight = remaining.toInt().coerceAtLeast(1)))
                    drawText(title, topLeft = Offset(top.x + inset, textY))
                    textY += title.size.height + 2.dp.toPx()
                    val bodyHeight = top.y + height - inset - textY
                    if (bodyHeight >= with(density) { secondaryStyle.lineHeight.toPx() }) {
                        val body = textMeasurer.measure(node.subtitle, secondaryStyle,
                            maxLines = if (node.kind == BackgroundNodeKind.Blackboard) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = Constraints(maxWidth = textWidth, maxHeight = bodyHeight.toInt().coerceAtLeast(1)))
                        drawText(body, topLeft = Offset(top.x + inset, textY))
                    }
                }
            }
            if (loading && dashboard == null && tasks.isEmpty()) {
                Column(Modifier.align(Alignment.Center).background(colors.surface).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("正在加载黑板…", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        Text(if (tasks.isEmpty()) "当前范围没有任务 · 点击黑板或 Worker 查看状态" else "拖动画布 · 双指缩放 · 点击节点查看详情", style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }

    if (browserOpen) {
        ModalBottomSheet(onDismissRequest = { browserOpen = false }) {
            Text("节点列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(graph.nodes, key = { it.id }) { node ->
                    Row(Modifier.fillMaxWidth().clickable { browse(node) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(node.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when (node.kind) { BackgroundNodeKind.Blackboard -> "共享黑板"; BackgroundNodeKind.Worker -> "Worker"; BackgroundNodeKind.Task -> node.task?.id.orEmpty() },
                                style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                        Text(backgroundStatusLabel(node.status), style = MaterialTheme.typography.labelSmall, color = boardStatusColor(node.status, colors))
                    }
                }
            }
        }
    }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedId = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selectedId = null }) { Icon(Icons.Outlined.Close, contentDescription = "关闭节点详情") }
                }
                Text(backgroundStatusLabel(selected.status), color = boardStatusColor(selected.status, colors), style = MaterialTheme.typography.labelLarge)
                when (selected.kind) {
                    BackgroundNodeKind.Blackboard -> {
                        Text(selected.subtitle)
                        dashboard?.let { runtime ->
                            Text("Worker ${runtime.pool.workerCount} · 忙碌 ${runtime.pool.busyWorkers} · 空闲 ${runtime.pool.idleWorkers}")
                            Text("最近心跳：${runtime.lastHeartbeat ?: "尚未收到"}", style = MaterialTheme.typography.bodySmall)
                            runtime.heartbeatEvents.takeLast(3).asReversed().forEach { event ->
                                Text("${event.mode} · 领取 ${event.tasksPulled} · 完成 ${event.tasksDone} · 失败 ${event.tasksFailed}", style = MaterialTheme.typography.bodySmall)
                                event.actions.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                            }
                        }
                        if (tasks.isEmpty()) Text("当前筛选范围内没有任务。", color = colors.onSurfaceVariant)
                        Text("黑板连接 Worker；任务按服务器上报的归属连线。虚线表示已完成任务或未上报的 Worker。",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    BackgroundNodeKind.Worker -> {
                        Text(selected.subtitle)
                        selected.worker?.let { worker ->
                            Text("累计处理 ${worker.taskCount} 个任务", style = MaterialTheme.typography.bodySmall)
                            worker.currentTaskId?.takeIf(String::isNotBlank)?.let { taskId ->
                                TextButton(onClick = { selectedId = null; onOpenTask(taskId) }) { Text("查看当前任务") }
                            }
                            worker.sessionId?.takeIf(String::isNotBlank)?.let { sessionId ->
                                Button(onClick = { selectedId = null; onOpenSession(sessionId) }) { Text("打开 Worker 对话") }
                            }
                        }
                    }
                    BackgroundNodeKind.Task -> selected.task?.let { task ->
                        Text("${task.id} · ${task.priority.ifBlank { "normal" }} · ${selected.workerId ?: "待领取"}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { selectedId = null; onOpenTask(task.id) }) { Text("查看任务详情") }
                            task.executionSession()?.let { sessionId ->
                                TextButton(onClick = { selectedId = null; onOpenSession(sessionId) }) { Text("打开对话") }
                            }
                        }
                        task.description.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                        (task.blockReason ?: task.error ?: task.resultPreview)?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun boardStatusColor(status: String, colors: ColorScheme): Color = when (status.lowercase()) {
    "blocked", "failed", "error" -> colors.error
    "ready" -> if (colors.surface.luminance() < .5f) Color(0xFFF0C265) else Color(0xFF896100)
    "idle", "stopped", "unknown", "" -> colors.onSurfaceVariant
    else -> colors.primary
}
