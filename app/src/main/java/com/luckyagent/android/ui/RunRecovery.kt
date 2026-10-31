package com.luckyagent.android.ui

/**
 * Select the run a terminal frame should close when the runtime omitted its
 * request id. An explicit id wins only when it still belongs to this session;
 * otherwise a single active run is unambiguous, and a foreground run is the
 * safe fallback when several requests share the session queue.
 */
internal fun selectTerminalRunId(
    explicitId: String?,
    activeRunIds: Collection<String>,
    foregroundRunId: String?,
): String? {
    val ids = activeRunIds.toList()
    if (explicitId != null && explicitId in ids) return explicitId
    if (ids.size == 1) return ids.single()
    return foregroundRunId?.takeIf { it in ids }
}

/** A terminal frame may still settle visible UI after its run was replayed away. */
internal fun terminalFrameShouldSettle(
    hasVisibleTurn: Boolean,
    selectedRunId: String?,
): Boolean = hasVisibleTurn || selectedRunId != null

/** Return runs that have not produced an event within the recovery window. */
internal fun staleRunIds(
    lastEventAt: Map<String, Long>,
    now: Long,
    timeoutMs: Long,
): Set<String> = lastEventAt.filterValues { now - it >= timeoutMs }.keys
