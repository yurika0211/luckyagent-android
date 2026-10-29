package com.luckyagent.android.ui

import com.luckyagent.android.data.api.RuntimeSession
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Keep untrusted runtime payloads from becoming unbounded Compose state. */
internal const val MAX_UI_MESSAGE_CHARS = 32_000
internal const val MAX_UI_REASONING_CHARS = 16_000
internal const val MAX_UI_TOOL_ARGS_CHARS = 8_000
internal const val MAX_UI_TOOL_OUTPUT_CHARS = 16_000
internal const val MAX_UI_ATTACHMENTS = 32
private const val UI_TRUNCATION_MARKER = "\n…内容已截断…"

internal fun capUiText(value: String, maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (value.length <= maxChars) return value
    val marker = UI_TRUNCATION_MARKER
    if (maxChars <= marker.length) return marker.take(maxChars)
    var keep = maxChars - marker.length
    // Do not leave half of a UTF-16 surrogate pair at the cut.
    if (keep > 0 && value[keep - 1].isHighSurrogate() && value[keep].isLowSurrogate()) keep--
    return value.take(keep) + marker
}

/** Bound the combined stream without allocating the entire incoming chunk. */
internal fun appendUiText(current: String, piece: String, maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (current.endsWith(UI_TRUNCATION_MARKER)) return capUiText(current, maxChars)
    val remaining = (maxChars - current.length).coerceAtLeast(0)
    return capUiText(current + piece.take(remaining + 1), maxChars)
}

/** Read only primitive JSON values; object/array values must never throw here. */
internal fun JsonElement?.textOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.longOrNullSafe(): Long? =
    (this as? JsonPrimitive)?.longOrNull

internal fun JsonElement?.intOrNullSafe(): Int? =
    (this as? JsonPrimitive)?.intOrNull

/** API data can contain duplicate rows during reconnects; Compose keys cannot. */
internal fun dedupeSessions(sessions: List<RuntimeSession>): List<RuntimeSession> {
    val seen = HashSet<String>(sessions.size)
    return sessions.filter { session ->
        val id = session.id.trim()
        id.isNotEmpty() && seen.add(id)
    }
}

/** Fixed drawer categories. Membership comes only from agent_role, never from project or title. */
internal const val SESSION_GROUP_PINNED = "置顶"
internal const val SESSION_GROUP_DELEGATED = "Delegated"
internal const val SESSION_GROUP_WORKER = "Worker"
internal const val SESSION_GROUP_UNSORTED = "未分类"

internal enum class FixedSessionRole {
    Delegated,
    Worker,
}

/**
 * Runtime sessions the desktop opens for itself.
 * Delegated is `subagent` or `subagent:<id>`. Worker is `worker`, `worker-task`, or `worker-…`.
 * A role that only shares a prefix with neither stays in the user's project groups.
 */
internal fun fixedSessionRole(agentRole: String?): FixedSessionRole? {
    val role = agentRole?.trim().orEmpty()
    if (role.isEmpty()) return null
    return when {
        role == "subagent" || role.startsWith("subagent:") -> FixedSessionRole.Delegated
        role == "worker" || role == "worker-task" || role.startsWith("worker-") -> FixedSessionRole.Worker
        else -> null
    }
}

internal fun isFixedRoleSession(session: RuntimeSession): Boolean = fixedSessionRole(session.agentRole) != null

internal data class SessionGroup(val name: String, val sessions: List<RuntimeSession>)

/**
 * One session, one group. Order: pinned ordinary sessions, Delegated, Worker, then project names.
 * Pinning does not pull a delegated or worker session out of its fixed group. Empty groups are omitted.
 * Project is ignored for fixed roles, so renaming a project cannot move them.
 */
internal fun sessionGroups(sessions: List<RuntimeSession>): List<SessionGroup> {
    val pinned = ArrayList<RuntimeSession>()
    val delegated = ArrayList<RuntimeSession>()
    val worker = ArrayList<RuntimeSession>()
    val rest = ArrayList<RuntimeSession>()
    for (session in sessions) {
        when (fixedSessionRole(session.agentRole)) {
            FixedSessionRole.Delegated -> delegated += session
            FixedSessionRole.Worker -> worker += session
            null -> if (session.pinned) pinned += session else rest += session
        }
    }
    val groups = mutableListOf<SessionGroup>()
    if (pinned.isNotEmpty()) groups += SessionGroup(SESSION_GROUP_PINNED, pinned)
    if (delegated.isNotEmpty()) groups += SessionGroup(SESSION_GROUP_DELEGATED, delegated)
    if (worker.isNotEmpty()) groups += SessionGroup(SESSION_GROUP_WORKER, worker)
    rest.groupBy { it.project?.trim().orEmpty() }
        .toSortedMap(compareBy { if (it.isEmpty()) "\uFFFF" else it.lowercase() })
        .forEach { (project, rows) ->
            groups += SessionGroup(project.ifBlank { SESSION_GROUP_UNSORTED }, rows)
        }
    return groups
}
