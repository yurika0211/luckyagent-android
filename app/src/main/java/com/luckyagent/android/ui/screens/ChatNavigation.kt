package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.executionSession
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.ui.AppDestination

/**
 * Tasks that belong to the open chat, so the session card does not become a
 * second copy of the global task list.
 */
internal fun tasksForSession(tasks: List<TaskSummary>, sessionId: String): List<TaskSummary> {
    val owner = sessionId.trim()
    if (owner.isEmpty()) return emptyList()
    return tasks.filter { task -> taskBelongsToSession(owner, task.parentSessionId, task.sessionId, task.executionSession(), task.metadata) }
}

/**
 * Background board items for the same chat. Session fields match the autonomy
 * payload; missing fields stay off the current-session card.
 */
internal fun backgroundForSession(tasks: List<AutonomyTaskSummary>, sessionId: String): List<AutonomyTaskSummary> {
    val owner = sessionId.trim()
    if (owner.isEmpty()) return emptyList()
    return tasks.filter { task ->
        taskBelongsToSession(owner, task.parentSessionId, task.sessionId, task.executionSession(), task.metadata)
    }
}

private fun taskBelongsToSession(
    owner: String,
    parentSessionId: String?,
    sessionId: String?,
    executionSession: String?,
    metadata: Map<String, String>,
): Boolean =
    parentSessionId?.trim() == owner ||
        metadata["parent_session_id"]?.trim() == owner ||
        metadata["owner_session_id"]?.trim() == owner ||
        sessionId?.trim() == owner ||
        executionSession?.trim() == owner

internal fun sessionNavigationTask(tasks: List<TaskSummary>): TaskSummary? =
    tasks.firstOrNull { it.status.taskNeedsAttention() }
        ?: tasks.firstOrNull { it.status.taskIsActive() && !it.status.isTerminalTaskStatus() }
        ?: tasks.maxByOrNull { it.lastActivityAt ?: it.completedAt ?: it.createdAt ?: "" }

internal fun sessionNavigationBackground(tasks: List<AutonomyTaskSummary>): AutonomyTaskSummary? =
    tasks.firstOrNull { it.state.taskNeedsAttention() }
        ?: tasks.firstOrNull { it.state.taskIsActive() }
        ?: tasks.maxByOrNull { it.lastActivityAt ?: it.completedAt ?: it.updatedAt ?: it.createdAt ?: "" }

/** Local hide keys. Deleting a card here does not delete the computer's record. */
enum class SessionCardKind(val key: String) {
    Task("task"),
    Background("background"),
    Trace("trace"),
    Memory("memory"),
    Panel("panel"),
}

/** Stable id for the whole current-session card. Hiding it does not hide the pages. */
internal const val SESSION_PANEL_CARD_ID = "panel"

internal fun sessionCardKey(sessionId: String, kind: SessionCardKind, id: String): String {
    val owner = sessionId.trim()
    val item = id.trim()
    if (owner.isEmpty() || item.isEmpty()) return ""
    return "$owner|${kind.key}|$item"
}

internal fun hiddenSessionCard(
    hidden: Set<String>,
    sessionId: String,
    kind: SessionCardKind,
    id: String?,
): Boolean {
    val key = sessionCardKey(sessionId, kind, id.orEmpty())
    return key.isNotEmpty() && key in hidden
}

/**
 * Phone drawer and wide rail share this list. Session progress is a card on
 * chat, so tasks, traces, memory, and the background board are not peers here.
 *
 * Keyed by [AppDestination], not by label: labels are UI copy and change often,
 * and a lookup by text would either crash or silently pick the wrong page.
 */
internal val computerNavDestinations = listOf(
    AppDestination.Cron,
    AppDestination.Gateways,
    AppDestination.Plugins,
    AppDestination.Skills,
    AppDestination.RuntimeConfig,
)
