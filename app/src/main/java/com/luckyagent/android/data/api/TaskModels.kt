package com.luckyagent.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskListResponse(
    val tasks: List<TaskRecord> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class TaskRecord(
    val id: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    val source: String = "",
    val mode: String = "",
    val status: String = "",
    val description: String = "",
    val input: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val budget: TaskBudget? = null,
    val outcome: TaskOutcome? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class TaskBudget(
    @SerialName("max_children") val maxChildren: Int = 0,
    @SerialName("max_concurrent") val maxConcurrent: Int = 0,
    @SerialName("max_debate_rounds") val maxDebateRounds: Int = 0,
    @SerialName("max_tokens") val maxTokens: Int = 0,
    @SerialName("max_tool_calls") val maxToolCalls: Int = 0,
    val timeout: Long = 0,
    @SerialName("allow_recursive") val allowRecursive: Boolean = false,
    @SerialName("require_verifier") val requireVerifier: Boolean = false,
)

@Serializable
data class TaskOutcome(
    val status: String = "",
    val verified: Boolean = false,
    val verifier: String? = null,
    @SerialName("user_feedback") val userFeedback: String? = null,
    val score: Double = 0.0,
    val cost: TaskCostSnapshot = TaskCostSnapshot(),
    @SerialName("recommended_next") val recommendedNext: String? = null,
)

@Serializable
data class TaskCostSnapshot(
    @SerialName("token_estimate") val tokenEstimate: Int = 0,
    @SerialName("tool_calls") val toolCalls: Int = 0,
    val elapsed: Long = 0,
    @SerialName("child_count") val childCount: Int = 0,
    @SerialName("retry_count") val retryCount: Int = 0,
)

@Serializable
data class TaskObservation(
    @SerialName("task_id") val taskId: String = "",
    val status: String = "",
    val mode: String = "",
    val progress: Double = 0.0,
    @SerialName("running_children") val runningChildren: Int = 0,
    @SerialName("completed_children") val completedChildren: Int = 0,
    @SerialName("failed_children") val failedChildren: Int = 0,
    val blockers: List<String> = emptyList(),
    @SerialName("fresh_evidence") val freshEvidence: List<String> = emptyList(),
    @SerialName("files_changed") val filesChanged: List<String> = emptyList(),
    @SerialName("tests_run") val testsRun: List<String> = emptyList(),
    @SerialName("verifier_status") val verifierStatus: String? = null,
    val cost: TaskCostSnapshot = TaskCostSnapshot(),
    @SerialName("recommended_next") val recommendedNext: String = "finalize",
)

@Serializable
data class TaskTreeNode(
    val task: TaskRecord = TaskRecord(),
    val observation: TaskObservation = TaskObservation(),
    val children: List<TaskTreeNode> = emptyList(),
)

@Serializable
data class TaskEventsResponse(
    @SerialName("task_id") val taskId: String = "",
    val events: List<TaskEvent> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class TaskEvent(
    val type: String = "",
    @SerialName("task_id") val taskId: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    val time: String? = null,
    val message: String? = null,
    val status: String? = null,
    val mode: String? = null,
    val progress: Double? = null,
    @SerialName("child_id") val childId: String? = null,
    val error: String? = null,
    val evidence: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val tests: List<String> = emptyList(),
    val cost: TaskCostSnapshot? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class TaskResultResponse(
    @SerialName("task_id") val taskId: String = "",
    val result: String = "",
)

@Serializable
data class LegacyTasksResponse(
    val tasks: List<LegacyCollabTask> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class LegacyCollabTask(
    val id: String = "",
    val mode: String = "",
    val description: String = "",
    val input: String = "",
    @SerialName("sub_tasks") val subTasks: List<LegacySubTask> = emptyList(),
    val state: String = "",
    val result: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val timeout: Long = 0,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class LegacySubTask(
    val id: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val description: String = "",
    val input: String = "",
    val output: String? = null,
    val state: String = "",
    val error: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    val timeout: Long = 0,
)

enum class TaskOrigin {
    Unified,
    Legacy,
}

data class TaskSummary(
    val id: String,
    val parentId: String? = null,
    val source: String,
    val mode: String,
    val status: String,
    val description: String,
    val input: String? = null,
    val result: String? = null,
    val error: String? = null,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val lastActivityAt: String? = null,
    val progress: Double = 0.0,
    val runningChildren: Int = 0,
    val completedChildren: Int = 0,
    val failedChildren: Int = 0,
    val childCount: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
    val origin: TaskOrigin = TaskOrigin.Unified,
)

data class TaskNode(
    val summary: TaskSummary,
    val children: List<TaskNode> = emptyList(),
)

data class TaskDetail(
    val root: TaskNode,
    val events: List<TaskEvent> = emptyList(),
    val result: String? = null,
    val origin: TaskOrigin = TaskOrigin.Unified,
)

fun TaskRecord.toTaskSummary(
    observation: TaskObservation? = null,
    childCount: Int = 0,
    result: String? = null,
): TaskSummary {
    val obs = observation ?: TaskObservation(
        taskId = id,
        status = status,
        mode = mode,
        progress = statusProgress(status),
    )
    return TaskSummary(
        id = id,
        parentId = parentId,
        source = source.ifBlank { "task" },
        mode = mode.ifBlank { "single" },
        status = status.ifBlank { obs.status },
        description = description.ifBlank { id },
        input = input,
        result = result,
        error = obs.blockers.firstOrNull(),
        createdAt = createdAt,
        startedAt = startedAt,
        completedAt = completedAt,
        lastActivityAt = completedAt ?: startedAt ?: createdAt,
        progress = obs.progress.coerceIn(0.0, 1.0),
        runningChildren = obs.runningChildren,
        completedChildren = obs.completedChildren,
        failedChildren = obs.failedChildren,
        childCount = childCount,
        metadata = metadata,
        origin = TaskOrigin.Unified,
    )
}

fun TaskTreeNode.toTaskNode(result: String? = null): TaskNode =
    TaskNode(
        summary = task.toTaskSummary(observation, children.size, result),
        children = children.map { it.toTaskNode() },
    )

fun LegacyCollabTask.toTaskNode(): TaskNode {
    val children = subTasks.map { it.toTaskNode() }
    val completed = subTasks.count { it.state.equals("completed", ignoreCase = true) }
    val failed = subTasks.count {
        it.state.equals("failed", ignoreCase = true) || it.state.equals("timeout", ignoreCase = true)
    }
    val running = subTasks.size - completed - failed
    val progress = when {
        state.equals("completed", ignoreCase = true) || state.equals("cancelled", ignoreCase = true) -> 1.0
        subTasks.isNotEmpty() -> ((completed + failed).toDouble() / subTasks.size).coerceIn(0.0, 1.0)
        else -> statusProgress(state)
    }
    return TaskNode(
        summary = TaskSummary(
            id = id,
            source = "agents",
            mode = mode.ifBlank { "parallel" },
            status = state,
            description = description.ifBlank { id },
            input = input,
            result = result,
            error = subTasks.firstOrNull { !it.error.isNullOrBlank() }?.error,
            createdAt = createdAt,
            completedAt = completedAt,
            lastActivityAt = completedAt ?: createdAt,
            progress = progress,
            runningChildren = running.coerceAtLeast(0),
            completedChildren = completed,
            failedChildren = failed,
            childCount = children.size,
            metadata = metadata,
            origin = TaskOrigin.Legacy,
        ),
        children = children,
    )
}

private fun LegacySubTask.toTaskNode(): TaskNode =
    TaskNode(
        summary = TaskSummary(
            id = id,
            parentId = parentId,
            source = agentId?.takeIf { it.isNotBlank() } ?: "agent",
            mode = "agent",
            status = state,
            description = description.ifBlank { id },
            input = input,
            result = output,
            error = error,
            createdAt = startedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            lastActivityAt = completedAt ?: startedAt,
            progress = statusProgress(state),
            metadata = agentId?.let { mapOf("agent_id" to it) } ?: emptyMap(),
            origin = TaskOrigin.Legacy,
        ),
    )

private fun statusProgress(status: String): Double = when (status.lowercase()) {
    "completed", "cancelled" -> 1.0
    "running" -> 0.5
    else -> 0.0
}

fun String.isTerminalTaskStatus(): Boolean = when (lowercase()) {
    "completed", "failed", "cancelled", "timeout" -> true
    else -> false
}
