package com.luckyagent.android.ui

import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ProviderMessage
import java.time.Instant

/**
 * The runtime serializes turns per session. This class keeps the client-side
 * FIFO state while the ViewModel dispatcher hands one item to the runtime at a
 * time.
 */
enum class OutboundQueueStatus {
    Queued,
    Sending,
    Accepted,
    ServerQueued,
    Running,
    Failed,
}

internal fun OutboundQueueStatus.isInFlight(): Boolean = when (this) {
    OutboundQueueStatus.Sending,
    OutboundQueueStatus.Accepted,
    OutboundQueueStatus.ServerQueued,
    OutboundQueueStatus.Running,
    -> true
    OutboundQueueStatus.Queued,
    OutboundQueueStatus.Failed,
    -> false
}

data class OutboundQueueItem(
    val id: String,
    val sessionId: String,
    val preview: String,
    val message: String,
    val attachments: List<MediaAttachment> = emptyList(),
    val requestId: String? = null,
    val status: OutboundQueueStatus = OutboundQueueStatus.Queued,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Small synchronized store so ViewModel event callbacks can update queue state safely. */
class OutboundChatQueue {
    private val items = LinkedHashMap<String, OutboundQueueItem>()

    @Synchronized
    fun add(item: OutboundQueueItem) {
        items[item.id] = item
    }

    @Synchronized
    fun replace(nextItems: List<OutboundQueueItem>) {
        items.clear()
        nextItems.forEach { items[it.id] = it }
    }

    @Synchronized
    fun clear() {
        items.clear()
    }

    @Synchronized
    fun update(
        id: String,
        status: OutboundQueueStatus? = null,
        requestId: String? = null,
        error: String? = null,
        clearError: Boolean = false,
    ): OutboundQueueItem? {
        val current = items[id] ?: return null
        val next = current.copy(
            status = status ?: current.status,
            requestId = requestId ?: current.requestId,
            error = if (clearError) null else error ?: current.error,
        )
        items[id] = next
        return next
    }

    @Synchronized
    fun findByRequest(sessionId: String, requestId: String): OutboundQueueItem? =
        items.values.firstOrNull { it.sessionId == sessionId && it.requestId == requestId }

    /** Oldest request that reached the socket and can be closed by a frame without an id. */
    @Synchronized
    fun firstInFlight(sessionId: String): OutboundQueueItem? =
        items.values.firstOrNull { it.sessionId == sessionId && it.status.isInFlight() }

    /**
     * Next frame that has not reached the WebSocket yet. Multiple frames may be
     * in flight: the runtime owns the server-side turn queue and reports whether
     * each request is accepted or waiting behind another run.
     */
    @Synchronized
    fun firstQueued(sessionId: String): OutboundQueueItem? {
        val sessionItems = items.values.filter { it.sessionId == sessionId }
        return sessionItems.firstOrNull { it.status == OutboundQueueStatus.Queued }
    }

    @Synchronized
    fun sessionIds(): Set<String> = items.values.map { it.sessionId }.toSet()

    @Synchronized
    fun remove(id: String): OutboundQueueItem? = items.remove(id)

    @Synchronized
    fun removeSession(sessionId: String) {
        items.entries.removeIf { it.value.sessionId == sessionId }
    }

    @Synchronized
    fun items(sessionId: String): List<OutboundQueueItem> =
        items.values.filter { it.sessionId == sessionId }
}

/**
 * Outbox rows the server already stored.
 *
 * A request id is authoritative when the server echoes it back as
 * `client_message_id`. Until then, identical user text can confirm one row,
 * but only a stored line that is not already claimed by its own id and is not
 * older than the outbox row. An earlier "好" must not retire a later unsent "好".
 */
internal fun confirmedOutboundIds(
    items: List<OutboundQueueItem>,
    messages: List<ProviderMessage>,
): Set<String> {
    if (items.isEmpty()) return emptySet()
    val storedIds = messages.mapNotNull { it.clientMessageId?.trim()?.takeIf(String::isNotBlank) }.toSet()
    val textCandidates = messages.mapIndexedNotNull { index, message ->
        if (!message.role.equals("user", ignoreCase = true)) return@mapIndexedNotNull null
        val id = message.clientMessageId?.trim()?.takeIf(String::isNotBlank)
        if (id != null) return@mapIndexedNotNull null
        val text = message.content?.trim().orEmpty()
        if (text.isEmpty()) return@mapIndexedNotNull null
        TextCandidate(index, text, message.createdAt.toEpochMilliOrNull())
    }.toMutableList()
    val confirmed = mutableSetOf<String>()
    items.sortedBy(OutboundQueueItem::createdAt).forEach { item ->
        if (item.id in storedIds) {
            confirmed += item.id
            return@forEach
        }
        val text = item.message.trim()
        val match = textCandidates.indexOfFirst { candidate ->
            candidate.text == text && candidate.createdAtMillis?.let { it >= item.createdAt } != false
        }
        if (match >= 0) {
            textCandidates.removeAt(match)
            confirmed += item.id
        }
    }
    return confirmed
}

/**
 * Put local outbox rows back where they belong in the stored conversation.
 *
 * They used to be appended after every stored row. When the runtime had already
 * written the message but its outbox row was still alive (a slow acknowledgement,
 * a reconnect that painted history first), the local copy appeared *under its own
 * answer*. A row is placed in front of the first stored row that is newer than
 * it; rows newer than everything stay at the end.
 *
 * A row without a comparable timestamp is left at the end rather than guessed
 * into the middle of the history.
 */
internal fun interleavePendingOutbound(
    stored: List<ChatBubble>,
    pending: List<ChatBubble>,
): List<ChatBubble> {
    if (pending.isEmpty()) return stored
    if (stored.isEmpty()) return pending
    val merged = stored.toMutableList()
    pending.forEach { bubble ->
        val at = bubble.createdAt.toEpochMilliOrNull()?.let { sentAt ->
            merged.indexOfFirst { existing ->
                val existingAt = existing.createdAt.toEpochMilliOrNull() ?: return@indexOfFirst false
                existingAt > sentAt
            }
        } ?: -1
        if (at < 0) merged += bubble else merged.add(at, bubble)
    }
    return merged
}

private data class TextCandidate(
    val index: Int,
    val text: String,
    val createdAtMillis: Long?,
)

private fun String?.toEpochMilliOrNull(): Long? {
    val raw = this?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
}
