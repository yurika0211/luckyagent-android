package com.luckyagent.android.ui.graph

import com.luckyagent.android.data.api.AutonomyDashboardResponse
import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.AutonomyWorker

enum class BackgroundNodeKind { Blackboard, Worker, Task }

data class BackgroundBoardNode(
    val id: String,
    val kind: BackgroundNodeKind,
    val title: String,
    val status: String,
    val subtitle: String,
    val position: GraphPoint,
    val width: Float,
    val height: Float,
    val worker: AutonomyWorker? = null,
    val task: AutonomyTaskSummary? = null,
    val workerId: String? = null,
) {
    fun contains(point: GraphPoint): Boolean = point.x >= position.x && point.x <= position.x + width &&
        point.y >= position.y && point.y <= position.y + height
}

data class BackgroundBoardEdge(val from: String, val to: String, val historical: Boolean = false)

data class BackgroundBoardGraph(
    val nodes: List<BackgroundBoardNode>,
    val edges: List<BackgroundBoardEdge>,
) {
    val bounds: List<GraphPoint> get() = nodes.flatMap {
        listOf(it.position, GraphPoint(it.position.x + it.width, it.position.y + it.height))
    }
}

/** Render reported assignments only; this queue does not expose agent-to-agent message edges. */
fun buildBackgroundBoardGraph(
    dashboard: AutonomyDashboardResponse?,
    tasks: List<AutonomyTaskSummary>,
): BackgroundBoardGraph {
    val visibleTasks = tasks.filter { it.id.isNotBlank() }.distinctBy { it.id }.sortedBy { it.id }
    val workers = dashboard?.workers.orEmpty().filter { it.id.isNotBlank() }.distinctBy { it.id }.sortedBy { it.id }
    val ownerByTask = visibleTasks.associate { task ->
        task.id to (task.assignedTo?.trim()?.takeIf { it.isNotEmpty() }
            ?: workers.firstOrNull { it.currentTaskId == task.id }?.id)
    }
    val workerIds = (workers.map { it.id } + ownerByTask.values.filterNotNull()).distinct().sorted()
    val workerMap = workers.associateBy { it.id }
    val nodes = mutableListOf<BackgroundBoardNode>()
    val edges = mutableListOf<BackgroundBoardEdge>()
    nodes += BackgroundBoardNode(
        id = "blackboard",
        kind = BackgroundNodeKind.Blackboard,
        title = "共享任务黑板",
        status = when (dashboard?.started) { true -> "running"; false -> "stopped"; null -> "unknown" },
        subtitle = dashboard?.queue?.let { "待处理 ${it.ready} · 运行 ${it.inProgress}\n阻塞 ${it.blocked} · 完成 ${it.done}" }
            ?: "等待服务器运行状态",
        position = GraphPoint(144f, 16f), width = 264f, height = 148f,
    )
    workerIds.forEachIndexed { index, id ->
        val worker = workerMap[id]
        nodes += BackgroundBoardNode(
            id = "worker:$id", kind = BackgroundNodeKind.Worker, title = id,
            status = worker?.state ?: "unknown",
            subtitle = worker?.let {
                it.currentTaskTitle?.takeIf(String::isNotBlank) ?: it.currentTaskId?.takeIf(String::isNotBlank)
                    ?: "${backgroundStatusLabel(it.state)} · 已处理 ${it.taskCount} 项"
            } ?: "此 Worker 当前未上报状态",
            position = GraphPoint(if (workerIds.size == 1) 154f else 20f + (index % 2) * 268f,
                228f + (index / 2) * 140f),
            width = 244f, height = 116f, worker = worker, workerId = id,
        )
        edges += BackgroundBoardEdge("blackboard", "worker:$id", historical = worker == null)
    }
    val taskTop = if (workerIds.isEmpty()) 228f else 228f + ((workerIds.size + 1) / 2) * 140f + 48f
    visibleTasks.forEachIndexed { index, task ->
        val nodeId = "task:${task.id}"
        nodes += BackgroundBoardNode(
            id = nodeId, kind = BackgroundNodeKind.Task, title = task.title.ifBlank { task.id },
            status = task.state, subtitle = "${task.id} · ${task.priority.ifBlank { "normal" }}",
            position = GraphPoint(if (visibleTasks.size == 1) 154f else 20f + (index % 2) * 268f,
                taskTop + (index / 2) * 172f),
            width = 244f, height = 148f, task = task, workerId = ownerByTask[task.id],
        )
        val owner = ownerByTask[task.id]
        edges += BackgroundBoardEdge(owner?.let { "worker:$it" } ?: "blackboard", nodeId,
            historical = task.state.equals("done", ignoreCase = true))
    }
    return BackgroundBoardGraph(nodes, edges)
}

fun backgroundStatusLabel(status: String): String = when (status.lowercase()) {
    "ready" -> "待处理"
    "in_progress", "busy" -> "执行中"
    "running" -> "运行中"
    "blocked" -> "阻塞"
    "done" -> "已完成"
    "idle" -> "空闲"
    "stopped" -> "已停止"
    "failed", "error" -> "失败"
    "unknown", "" -> "未上报"
    else -> status
}
