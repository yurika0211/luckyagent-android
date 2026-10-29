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
    const val OFFLINE_LABEL = "离线，显示上次内容"
    const val REFRESH_FAILED_LABEL = "刷新失败，显示上次内容"
}

data class CachedSessionMeta(
    val messageCount: Int,
    val updatedAt: String?,
    val reachedOldest: Boolean,
)

enum class HistorySync {
    /** updated_at and message_count are unchanged. Skip the body fetch. */
    Unchanged,
    /** Fetch the latest full-content page and append the non-overlapping tail. */
    MergeLatest,
    /** Overlap missing, count shrank, or the cache is partial. Fetch the full history. */
    Invalidate,
}

fun historySyncAction(cached: CachedSessionMeta?, remote: RuntimeSession): HistorySync {
    if (cached == null) return HistorySync.Invalidate
    val remoteCount = remote.messageCount
    if (remoteCount == null || remote.updatedAt.isNullOrBlank() || cached.updatedAt.isNullOrBlank()) {
        return HistorySync.Invalidate
    }
    if (remoteCount < cached.messageCount) return HistorySync.Invalidate
    if (!cached.reachedOldest) return HistorySync.Invalidate
    if (remoteCount == cached.messageCount && remote.updatedAt == cached.updatedAt) {
        return HistorySync.Unchanged
    }
    if (remoteCount == cached.messageCount) return HistorySync.Invalidate
    return HistorySync.MergeLatest
}

data class MessagePage(
    val messages: List<ProviderMessage>,
    /** Server index of messages.first, oldest-first across the whole session. */
    val startIndex: Int,
    val messageCount: Int,
    val reachedOldest: Boolean,
    /** True when this page's bodies came from the default preview cap. */
    val contentTruncated: Boolean = false,
)

/**
 * Server history truncation marker. Only a body that actually ends with this
 * suffix was capped. A page-level flag must not blank every other row.
 */
internal const val HISTORY_TRUNCATION_MARKER = "\n...[truncated]"

internal fun ProviderMessage.withPreviewFlag(pageTruncated: Boolean): ProviderMessage {
    // A later full page must not clear an older cached preview that was not
    // fetched again. The marker is the only proof that this body is still capped.
    if (!pageTruncated && content?.endsWith(HISTORY_TRUNCATION_MARKER) == true) {
        return if (contentTruncated) this else copy(contentTruncated = true)
    }
    if (!pageTruncated) {
        return if (contentTruncated) copy(contentTruncated = false) else this
    }
    val capped = content?.endsWith(HISTORY_TRUNCATION_MARKER) == true
    return if (contentTruncated == capped) this else copy(contentTruncated = capped)
}

/** Mark only the bodies this preview page actually capped. A full fetch clears the flag. */
fun MessagePage.withContentTruncation(truncated: Boolean): MessagePage {
    val marked = messages.map { it.withPreviewFlag(truncated) }
    if (marked == messages && contentTruncated == truncated) return this
    return copy(contentTruncated = truncated, messages = marked)
}

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
    // A finished tool result replaces the running row. An already expanded body
    // stays when the refresh only brought its preview back.
    val kept = cached.messages.dropLast(overlap) +
        remoteMessages.take(overlap).mapIndexed { index, remote ->
            preferStoredBody(cached.messages[cached.messages.size - overlap + index], remote)
        }
    val merged = kept + incoming
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
 * History offset skips the newest N messages. The next older page therefore
 * uses how many messages are already shown, not the oldest row's session index.
 * Showing 60–99 of 100 requests offset=40 and receives 20–59.
 */
fun olderHistoryOffset(shownCount: Int): Int = shownCount.coerceAtLeast(0)

/**
 * Prepend an older page. The page must end exactly where the cache begins.
 * A gap or overlap means the offset drifted and the caller should invalidate.
 */
fun prependOlderPage(
    cached: MessagePage,
    older: List<ProviderMessage>,
    remoteCount: Int,
    serverHasMore: Boolean,
    requestedOffset: Int = cached.messages.size,
): LatestMerge {
    if (older.isEmpty()) {
        return LatestMerge.Appended(cached.copy(messageCount = remoteCount, reachedOldest = true))
    }
    if (remoteCount < cached.messageCount) return LatestMerge.Invalidate
    // The API offset skips the newest messages. Derive the returned page's
    // actual server index from the remote total instead of assuming the
    // cached count is still current; new messages may have arrived since the
    // cached page was painted.
    val olderStart = remoteCount - requestedOffset.coerceAtLeast(0) - older.size
    if (olderStart < 0) return LatestMerge.Invalidate
    if (olderStart + older.size != cached.startIndex) return LatestMerge.Invalidate
    // A touching identity means the offset drifted into rows we already stored.
    // An expanded body and its own preview are the same row, not drift.
    val driftedIntoCache = older.any { olderMessage ->
        cached.messages.any { cachedMessage -> sameDistinctRow(olderMessage, cachedMessage) }
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

/**
 * How a refreshed page should be written.
 *
 * [Unchanged] means every stored message still matches, so Room must not
 * delete and rewrite `cached_messages`. [Patch] inserts ordinals that are
 * new and deletes rows that fell outside the window. A changed overlapping
 * row, a gap, or an unaligned cache uses [Replace].
 */
sealed class HistoryCacheWrite {
    data object Unchanged : HistoryCacheWrite()
    data class Patch(
        /** New ordinals, plus overlap rows whose stored body must be rewritten. */
        val inserts: List<IndexedMessage>,
        val deleteBefore: Int,
        val deleteFrom: Int,
    ) : HistoryCacheWrite()
    data object Replace : HistoryCacheWrite()
}

data class IndexedMessage(
    val ordinal: Int,
    val message: ProviderMessage,
)

fun historyCacheWrite(stored: MessagePage?, incoming: MessagePage): HistoryCacheWrite {
    if (stored == null || stored.messages.isEmpty() || stored.startIndex < 0) return HistoryCacheWrite.Replace
    if (pagesMatch(stored, incoming)) return HistoryCacheWrite.Unchanged

    val storedStart = stored.startIndex
    val storedEnd = storedStart + stored.messages.size
    val nextStart = incoming.startIndex
    val nextEnd = nextStart + incoming.messages.size
    if (nextStart < 0 || nextEnd < nextStart) return HistoryCacheWrite.Replace
    // Replace deletes every cached row for this session and writes only the
    // incoming page, so a start index past the stored prefix drops that prefix.
    // Callers must not hand a narrower page here unless losing the prefix is
    // acceptable. There is no "keep the prefix" variant.
    if (nextStart > storedStart) {
        return HistoryCacheWrite.Replace
    }
    val overlapStart = maxOf(storedStart, nextStart)
    val overlapEnd = minOf(storedEnd, nextEnd)
    if (overlapStart >= overlapEnd) return HistoryCacheWrite.Replace
    val updates = mutableListOf<IndexedMessage>()
    for (ordinal in overlapStart until overlapEnd) {
        val previous = stored.messages[ordinal - storedStart]
        val next = incoming.messages[ordinal - nextStart]
        if (!sameHistoryRow(previous, next)) return HistoryCacheWrite.Replace
        val kept = preferStoredBody(previous, next)
        // Attachments, tool arguments, and other fields are not part of row
        // identity. A change there replaces the page. Keeping an expanded body
        // only ignores the preview suffix and the truncation flag.
        val sameFields = previous.copy(
            content = kept.content,
            contentTruncated = kept.contentTruncated,
        ) == kept.copy(
            content = kept.content,
            contentTruncated = kept.contentTruncated,
        )
        if (!sameFields) return HistoryCacheWrite.Replace
        if (kept != previous) updates += IndexedMessage(ordinal, kept)
    }
    val inserts = incoming.messages.mapIndexedNotNull { index, message ->
        val ordinal = nextStart + index
        if (ordinal < storedStart || ordinal >= storedEnd) IndexedMessage(ordinal, message) else null
    } + updates
    if (inserts.isEmpty() && nextStart == storedStart && nextEnd == storedEnd) {
        return HistoryCacheWrite.Unchanged
    }
    return HistoryCacheWrite.Patch(
        inserts = inserts,
        deleteBefore = nextStart,
        deleteFrom = nextEnd,
    )
}

private fun pagesMatch(stored: MessagePage, incoming: MessagePage): Boolean =
    stored.startIndex == incoming.startIndex &&
        stored.messageCount == incoming.messageCount &&
        stored.reachedOldest == incoming.reachedOldest &&
        stored.messages.size == incoming.messages.size &&
        // Structural equality: an attachment, content part, or blob field that
        // changed on an otherwise identical message still has to be written.
        // Identity equality is only for locating overlap below.
        stored.messages == incoming.messages

fun messageIdentity(message: ProviderMessage): String {
    message.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { return "client:$it" }
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

/** Same row for a cache rewrite. A stored full body still matches its own preview, and the reverse. */
internal fun sameHistoryRow(stored: ProviderMessage, incoming: ProviderMessage): Boolean {
    if (messagesEquivalent(stored, incoming)) return true
    if (stored.createdAt != incoming.createdAt || stored.role != incoming.role) return false
    if (stored.name != incoming.name || stored.toolCallId != incoming.toolCallId) return false
    return previewOf(stored.content, incoming.content, incoming.contentTruncated) ||
        previewOf(incoming.content, stored.content, stored.contentTruncated)
}

/** [preview] is the capped copy of [full]. Either side may be the one already stored. */
private fun previewOf(full: String?, preview: String?, previewTruncated: Boolean): Boolean {
    val capped = preview.orEmpty()
    if (!previewTruncated || !capped.endsWith(HISTORY_TRUNCATION_MARKER)) return false
    return full.orEmpty().startsWith(capped.removeSuffix(HISTORY_TRUNCATION_MARKER))
}

/** Same identity, but not merely a preview of a body we already expanded. */
private fun sameDistinctRow(left: ProviderMessage, right: ProviderMessage): Boolean {
    if (!messagesEquivalent(left, right)) return false
    val leftPreview = left.content?.endsWith(HISTORY_TRUNCATION_MARKER) == true
    val rightPreview = right.content?.endsWith(HISTORY_TRUNCATION_MARKER) == true
    if (leftPreview == rightPreview) return true
    val full = if (leftPreview) right.content.orEmpty() else left.content.orEmpty()
    val preview = if (leftPreview) left.content.orEmpty() else right.content.orEmpty()
    return !full.startsWith(preview.dropLast(HISTORY_TRUNCATION_MARKER.length))
}

/**
 * The fetched latest page is already present at the tail of [stored].
 * Older cached rows outside that page are left untouched. Identity overlap is
 * not enough: attachments and other fields must also be unchanged.
 */
internal fun latestPageAlreadyStored(
    stored: MessagePage,
    latest: List<ProviderMessage>,
    remoteCount: Int,
): Boolean {
    if (latest.isEmpty() || stored.messages.isEmpty() || remoteCount < latest.size) return false
    if (stored.startIndex + stored.messages.size != remoteCount) return false
    if (stored.messages.size < latest.size) return false
    val tail = stored.messages.takeLast(latest.size)
    return tail.indices.all { index -> sameStoredTailRow(tail[index], latest[index]) }
}

/** Full text already stored for a row still matches the preview the server just sent. */
private fun sameStoredTailRow(stored: ProviderMessage, remote: ProviderMessage): Boolean {
    // A preview already on disk is not the stored body, even when the text matches.
    if (stored.contentTruncated) return false
    if (stored == remote) return true
    if (!sameHistoryRow(stored, remote)) return false
    // Attachments, tool arguments, or a finished tool body still need a rewrite.
    // The preview suffix and the truncation flag do not.
    return stored.copy(content = remote.content, contentTruncated = remote.contentTruncated) == remote
}

/** Keep an expanded body when the refresh only brought back its preview. */
internal fun preferStoredBody(stored: ProviderMessage, remote: ProviderMessage): ProviderMessage {
    if (!remote.contentTruncated || stored.contentTruncated) return remote
    if (!sameHistoryRow(stored, remote)) return remote
    return stored
}

/**
 * Length of the prefix of [incoming] that already sits at the tail of [cached].
 * Returns -1 when the pages do not touch.
 */
internal fun overlapLength(cached: List<ProviderMessage>, incoming: List<ProviderMessage>): Int {
    if (cached.isEmpty() || incoming.isEmpty()) return -1
    val max = minOf(cached.size, incoming.size)
    for (len in max downTo 1) {
        if (rowsMatch(cached.takeLast(len), incoming.take(len))) return len
    }
    val incomingStart = incoming.first()
    val cachedPos = cached.indexOfLast { sameHistoryRow(it, incomingStart) }
    if (cachedPos >= 0 && rowsMatch(cached.drop(cachedPos), incoming.take(cached.size - cachedPos))) {
        return cached.size - cachedPos
    }
    return -1
}

private fun rowsMatch(left: List<ProviderMessage>, right: List<ProviderMessage>): Boolean =
    left.size == right.size && left.indices.all { index -> sameHistoryRow(left[index], right[index]) }
