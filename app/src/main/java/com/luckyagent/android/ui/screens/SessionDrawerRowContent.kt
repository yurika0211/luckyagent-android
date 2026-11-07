package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.ui.isFixedRoleSession

internal data class SessionDrawerRowContent(
    val id: String,
    val title: String,
    val summary: String,
    val meta: String,
    val pinned: Boolean,
    val fixedRole: Boolean,
    val unread: Boolean = false,
)

internal fun clipSessionLine(value: String, limit: Int): String {
    val text = value.trim()
    if (text.length <= limit) return text
    return text.take(limit - 1) + "…"
}

/** Title, one-line summary, and meta are fixed before the row is composed. */
internal fun sessionDrawerRowContent(
    session: RuntimeSession,
    draftSummary: String? = null,
    unread: Boolean = false,
): SessionDrawerRowContent {
    val title = session.title?.trim()?.takeIf { it.isNotEmpty() } ?: session.id
    val summary = draftSummary?.takeIf { it.isNotEmpty() }
        ?: session.recap.orEmpty().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
    val meta = buildList {
        (session.updatedAt ?: session.createdAt)?.let { add(formatSessionRowTime(it)) }
        session.messageCount?.let { add("$it 条消息") }
    }.joinToString(" · ")
    return SessionDrawerRowContent(
        id = session.id,
        title = clipSessionLine(title, 48),
        summary = clipSessionLine(summary, 80),
        meta = meta,
        pinned = session.pinned,
        fixedRole = isFixedRoleSession(session),
        unread = unread,
    )
}

internal fun formatSessionRowTime(raw: String): String {
    val clock = raw.substringAfter('T', "").take(5)
    return if (clock.length == 5 && clock[2] == ':') clock else raw.take(16)
}
