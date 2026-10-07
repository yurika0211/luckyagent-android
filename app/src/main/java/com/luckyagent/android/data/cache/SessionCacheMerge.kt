package com.luckyagent.android.data.cache

import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession

/**
 * Local session/history cache rules.
 *
 * The history API offset skips the newest N messages, so the same offset
 * changes meaning when new messages arrive. Pages are never stored by number.
 * A latest page is appended only when its oldest row overlaps the cached tail.
 */
object SessionCachePolicy {
    const val LATEST_PAGE = 40
    const val MAX_MESSAGES = 200
    const val MAX_SESSIONS = 30
    const val OFFLINE_LABEL = "离线，显示上次内容"
}

data class CachedSessionMeta(
    val messageCount: Int,
    val updatedAt: String?,
    val reachedOldest: Boolean,
)

enum class HistorySync {
    /** updated_at and message_count are unchanged. Skip the body fetch. */
    Unchanged,
    /** Fetch limit=40&offset=0 and append the non-overlapping tail. */
    MergeLatest,
    /** Overlap missing, count shrank, or the cache was never aligned. Refetch. */
    Invalidate,
}

fun historySyncAction(cached: CachedSessionMeta?, remote: RuntimeSession): HistorySync {
    if (cached == null) return HistorySync.Invalidate
    val remoteCount = remote.messageCount
    if (remoteCount == null || remote.updatedAt.isNullOrBlank() || cached.updatedAt.isNullOrBlank()) {
        return HistorySync.Invalidate
    }
    if (remoteCount < cached.messageCount) return HistorySync.Invalidate
    if (remoteCount == cached.messageCount && remote.updatedAt == cached.updatedAt) {
        return HistorySync.Unchanged
    }
    return HistorySync.MergeLatest
}

data class MessagePage(
    val messages: List<ProviderMessage>,
    /** Server index of messages.first, oldest-first across the whole session. */
    val startIndex: Int,
    val messageCount: Int,
    val reachedOldest: Boolean,
)

sealed class LatestMerge {
    data class Appended(val page: MessagePage) : LatestMerge()
    data object Invalidate : LatestMerge()
}

fun mergeLatestPage(
    cached: MessagePage?,
    remoteMessages: List<ProviderMessage>,
    remoteCount: Int,
): LatestMerge {
    if (remoteCount < 0) return LatestMerge.Invalidate
    if (remoteMessages.isEmpty()) {
        return if (remoteCount == 0) {
            LatestMerge.Appended(MessagePage(emptyList(), 0, 0, reachedOldest = true))
        } else {
            LatestMerge.Invalidate
        }
    }
    val remoteStart = (remoteCount - remoteMessages.size).coerceAtLeast(0)
    if (cached == null || cached.messages.isEmpty()) {
        return LatestMerge.Appended(
            MessagePage(
                messages = remoteMessages,
                startIndex = remoteStart,
                messageCount = remoteCount,
                reachedOldest = remoteStart == 0,
            ),
        )
    }
    if (remoteCount < cached.messageCount) return LatestMerge.Invalidate
    val overlap = overlapLength(cached.messages, remoteMessages)
    if (overlap < 0) return LatestMerge.Invalidate
    val incoming = remoteMessages.drop(overlap)
    val expectedStart = remoteCount - remoteMessages.size
    if (cached.startIndex + cached.messages.size != expectedStart + overlap) {
        return LatestMerge.Invalidate
    }
    val merged = cached.messages + incoming
    return LatestMerge.Appended(
        MessagePage(
            messages = merged,
            startIndex = cached.startIndex,
            messageCount = remoteCount,
            reachedOldest = cached.reachedOldest || cached.startIndex == 0,
        ),
    )
}

/**
 * Prepend an older page. The page must end exactly where the cache begins.
 * A gap or overlap means the offset drifted and the caller should invalidate.
 */
fun prependOlderPage(
    cached: MessagePage,
    older: List<ProviderMessage>,
    remoteCount: Int,
    serverHasMore: Boolean,
): LatestMerge {
    if (older.isEmpty()) {
        return LatestMerge.Appended(cached.copy(messageCount = remoteCount, reachedOldest = true))
    }
    if (remoteCount < cached.messageCount) return LatestMerge.Invalidate
    val olderStart = cached.startIndex - older.size
    if (olderStart < 0) return LatestMerge.Invalidate
    // A touching identity means the offset drifted into rows we already stored.
    val driftedIntoCache = older.any { olderMessage ->
        cached.messages.any { cachedMessage -> messagesEquivalent(olderMessage, cachedMessage) }
    }
    if (driftedIntoCache) return LatestMerge.Invalidate
    return LatestMerge.Appended(
        MessagePage(
            messages = older + cached.messages,
            startIndex = olderStart,
            messageCount = remoteCount,
            reachedOldest = !serverHasMore || olderStart == 0,
        ),
    )
}

fun capLatestMessages(page: MessagePage, max: Int = SessionCachePolicy.MAX_MESSAGES): MessagePage {
    if (page.messages.size <= max) return page
    val dropped = page.messages.size - max
    return page.copy(
        messages = page.messages.takeLast(max),
        startIndex = page.startIndex + dropped,
        reachedOldest = false,
    )
}

fun messageIdentity(message: ProviderMessage): String {
    val toolId = message.toolCallId?.trim().orEmpty()
    if (toolId.isNotEmpty()) return "tool:$toolId"
    return listOf(
        message.createdAt.orEmpty(),
        message.role.orEmpty(),
        message.content.orEmpty(),
        message.name.orEmpty(),
    ).joinToString("\u0001")
}

private fun messagesEquivalent(left: ProviderMessage, right: ProviderMessage): Boolean =
    messageIdentity(left) == messageIdentity(right)

/**
 * Length of the prefix of [incoming] that already sits at the tail of [cached].
 * Returns -1 when the pages do not touch.
 */
internal fun overlapLength(cached: List<ProviderMessage>, incoming: List<ProviderMessage>): Int {
    if (cached.isEmpty() || incoming.isEmpty()) return -1
    val cachedIds = cached.map(::messageIdentity)
    val incomingIds = incoming.map(::messageIdentity)
    val max = minOf(cachedIds.size, incomingIds.size)
    for (len in max downTo 1) {
        if (cachedIds.takeLast(len) == incomingIds.take(len)) return len
    }
    val incomingStart = incomingIds.first()
    val cachedPos = cachedIds.indexOfLast { it == incomingStart }
    if (cachedPos >= 0 && cachedIds.drop(cachedPos) == incomingIds.take(cachedIds.size - cachedPos)) {
        return cachedIds.size - cachedPos
    }
    return -1
}
