package com.luckyagent.android.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AutonomyDashboardResponse(
    @SerialName("generated_at") val generatedAt: String? = null,
    val started: Boolean = false,
    val queue: AutonomyQueueCounts = AutonomyQueueCounts(),
    val pool: AutonomyPoolStats = AutonomyPoolStats(),
    val workers: List<AutonomyWorker> = emptyList(),
    @SerialName("last_heartbeat") val lastHeartbeat: String? = null,
    @SerialName("heartbeat_events") val heartbeatEvents: List<AutonomyHeartbeatEvent> = emptyList(),
    val tasks: List<AutonomyTaskSummary> = emptyList(),
    val count: Int = 0,
)

@Serializable
data class AutonomyQueueCounts(
    val ready: Int = 0,
    @SerialName("in_progress") val inProgress: Int = 0,
    val blocked: Int = 0,
    val done: Int = 0,
)

@Serializable
data class AutonomyPoolStats(
    @SerialName("worker_count") val workerCount: Int = 0,
    @SerialName("idle_workers") val idleWorkers: Int = 0,
    @SerialName("busy_workers") val busyWorkers: Int = 0,
    @SerialName("stopped_workers") val stoppedWorkers: Int = 0,
    @SerialName("total_tasks") val totalTasks: Long = 0,
    @SerialName("failed_tasks") val failedTasks: Long = 0,
    @SerialName("average_duration_ms") val averageDurationMs: Long = 0,
    val running: Boolean = false,
)

@Serializable
data class AutonomyWorker(
    val id: String = "",
    val state: String = "",
    @SerialName("current_task_id") val currentTaskId: String? = null,
    @SerialName("current_task_title") val currentTaskTitle: String? = null,
    @SerialName("task_count") val taskCount: Long = 0,
    @SerialName("started_at") val startedAt: String? = null,
)

@Serializable
data class AutonomyHeartbeatEvent(
    val timestamp: String? = null,
    val mode: String = "",
    @SerialName("tasks_pulled") val tasksPulled: Int = 0,
    @SerialName("tasks_done") val tasksDone: Int = 0,
    @SerialName("tasks_failed") val tasksFailed: Int = 0,
    val actions: List<String> = emptyList(),
)

@Serializable
data class AutonomyTaskSummary(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val priority: String = "",
    val state: String = "",
    @SerialName("assigned_to") val assignedTo: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("session_id") val sessionId: String? = null,
    val attempts: Int = 0,
    val retries: Int = 0,
    val continuations: Int = 0,
    val verified: Boolean = false,
    val verification: String? = null,
    @SerialName("block_reason") val blockReason: String? = null,
    @SerialName("result_preview") val resultPreview: String? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("checkpoint_at") val checkpointAt: String? = null,
    @SerialName("checkpoint_present") val checkpointPresent: Boolean = false,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
)

@Serializable
data class AutonomyOperation(
    val id: String = "",
    val name: String = "",
    val state: String = "",
    val failed: Boolean = false,
    @SerialName("resolved_by") val resolvedBy: String? = null,
)

@Serializable
data class AutonomyTaskDetail(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val priority: String = "",
    val state: String = "",
    @SerialName("assigned_to") val assignedTo: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("session_id") val sessionId: String? = null,
    val attempts: Int = 0,
    val retries: Int = 0,
    val continuations: Int = 0,
    val verified: Boolean = false,
    val verification: String? = null,
    @SerialName("block_reason") val blockReason: String? = null,
    @SerialName("result_preview") val resultPreview: String? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("checkpoint_at") val checkpointAt: String? = null,
    @SerialName("checkpoint_present") val checkpointPresent: Boolean = false,
    @SerialName("last_activity_at") val lastActivityAt: String? = null,
    @SerialName("acceptance_criteria") val acceptanceCriteria: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val result: String? = null,
    val operations: List<AutonomyOperation> = emptyList(),
)

@Serializable
data class AutonomyTaskDetailResponse(
    val task: AutonomyTaskDetail = AutonomyTaskDetail(),
    val worker: AutonomyWorker? = null,
)
