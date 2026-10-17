package com.luckyagent.android.ui.graph

import com.luckyagent.android.data.api.AutonomyDashboardResponse
import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.AutonomyWorker
import org.junit.Assert.*
import org.junit.Test

class BackgroundBoardGraphTest {
    @Test fun assignmentsAndCompletedTasksUseReportedWorker() {
        val graph = buildBackgroundBoardGraph(
            AutonomyDashboardResponse(started = true, workers = listOf(AutonomyWorker(id = "worker-1", state = "idle"))),
            listOf(AutonomyTaskSummary(id = "done", state = "done", assignedTo = "worker-1"),
                AutonomyTaskSummary(id = "ready", state = "ready")),
        )
        assertEquals(4, graph.nodes.size)
        assertTrue(graph.edges.contains(BackgroundBoardEdge("blackboard", "worker:worker-1")))
        assertTrue(graph.edges.contains(BackgroundBoardEdge("worker:worker-1", "task:done", historical = true)))
        assertTrue(graph.edges.contains(BackgroundBoardEdge("blackboard", "task:ready")))
        assertFalse(graph.edges.any { it.from.startsWith("task:") })
    }

    @Test fun missingAssignmentCanUseCurrentWorkerButExplicitAssignmentWins() {
        val workers = listOf(
            AutonomyWorker(id = "current", currentTaskId = "task", state = "busy"),
            AutonomyWorker(id = "reported", state = "idle"),
        )
        val inferred = buildBackgroundBoardGraph(AutonomyDashboardResponse(workers = workers),
            listOf(AutonomyTaskSummary(id = "task", state = "in_progress")))
        assertTrue(inferred.edges.any { it.from == "worker:current" && it.to == "task:task" })
        assertEquals("current", inferred.nodes.single { it.task != null }.workerId)
        val explicit = buildBackgroundBoardGraph(AutonomyDashboardResponse(workers = workers),
            listOf(AutonomyTaskSummary(id = "task", state = "in_progress", assignedTo = "reported")))
        assertTrue(explicit.edges.any { it.from == "worker:reported" && it.to == "task:task" })
        assertFalse(explicit.edges.any { it.from == "worker:current" && it.to == "task:task" })
        assertEquals("reported", explicit.nodes.single { it.task != null }.workerId)
    }

    @Test fun unreportedWorkerDoesNotBecomeAnIdleOrOnlineWorker() {
        val graph = buildBackgroundBoardGraph(AutonomyDashboardResponse(),
            listOf(AutonomyTaskSummary(id = "old", assignedTo = "retired", state = "done")))
        val worker = graph.nodes.single { it.kind == BackgroundNodeKind.Worker }
        assertEquals("unknown", worker.status)
        assertNull(worker.worker)
        assertTrue(graph.edges.all { edge -> graph.nodes.any { it.id == edge.from } && graph.nodes.any { it.id == edge.to } })
    }

    @Test fun filteredTasksDoNotInventOtherTaskNodesOrLoseWorkers() {
        val task = AutonomyTaskSummary(id = "blocked", state = "blocked", assignedTo = "b")
        val graph = buildBackgroundBoardGraph(AutonomyDashboardResponse(workers = listOf(
            AutonomyWorker(id = "a"), AutonomyWorker(id = "b"))), listOf(task))
        assertEquals(listOf("blocked"), graph.nodes.mapNotNull { it.task?.id })
        assertEquals(2, graph.nodes.count { it.kind == BackgroundNodeKind.Worker })
        assertTrue(graph.nodes.all { node -> node.position.isFinite && node.width > 0 && node.height > 0 })
    }

    @Test fun nodeIdentityAndLayoutRemainStableAcrossPollingOrder() {
        val tasks = listOf(AutonomyTaskSummary(id = "blackboard"), AutonomyTaskSummary(id = "worker:a"))
        val dashboard = AutonomyDashboardResponse(workers = listOf(AutonomyWorker(id = "a"), AutonomyWorker(id = "a")))
        val first = buildBackgroundBoardGraph(dashboard, tasks + tasks.first() + AutonomyTaskSummary())
        val next = buildBackgroundBoardGraph(dashboard, tasks.reversed())
        assertEquals(first, next)
        assertEquals(first.nodes.size, first.nodes.map { it.id }.distinct().size)
        first.nodes.forEach { a -> first.nodes.filter { it.id != a.id }.forEach { b ->
            val overlaps = a.position.x < b.position.x + b.width && a.position.x + a.width > b.position.x &&
                a.position.y < b.position.y + b.height && a.position.y + a.height > b.position.y
            assertFalse("${a.id} overlaps ${b.id}", overlaps)
        } }
    }

    @Test fun emptyAndUnavailableRuntimeAreDifferent() {
        val unknown = buildBackgroundBoardGraph(null, emptyList()).nodes.single()
        assertEquals("unknown", unknown.status)
        val stopped = buildBackgroundBoardGraph(AutonomyDashboardResponse(started = false), emptyList()).nodes.single()
        assertEquals("stopped", stopped.status)
        assertTrue(stopped.contains(GraphPoint(stopped.position.x + 1, stopped.position.y + 1)))
        assertFalse(stopped.contains(GraphPoint(-1f, -1f)))
    }
}
