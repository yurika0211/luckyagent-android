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
