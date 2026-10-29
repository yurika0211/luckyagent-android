package com.luckyagent.android.ui

/** An in-memory view of server metadata, never a locally generated recap. */
data class SessionRecapPreview(val sessionId: String, val endpoint: String, val text: String?)

internal fun recapLabel(text: String): String = when {
    text.any { it in '\u3040'..'\u30ff' } -> "振り返り"
    text.any { it in '\uac00'..'\ud7af' } -> "돌아보기"
    text.any { it in '\u4e00'..'\u9fff' } -> "回顾"
    else -> "Recap"
}
