package com.luckyagent.android.ui

import com.luckyagent.android.data.api.MediaAttachment

/**
 * The runtime serializes turns per session. This class keeps the client-side
 * FIFO state while the ViewModel dispatcher hands one item to the runtime at a
 * time.
 */
enum class OutboundQueueStatus {
    Queued,
    Sending,
    Failed,
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
)

/** Small synchronized store so ViewModel event callbacks can update queue state safely. */
class OutboundChatQueue {
    private val items = LinkedHashMap<String, OutboundQueueItem>()

    @Synchronized
    fun add(item: OutboundQueueItem) {
        items[item.id] = item
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

    /**
     * Next sendable message. A message already in flight blocks the rest.
     * A failed message stays until retry and does not block later queued messages.
     */
    @Synchronized
    fun firstQueued(sessionId: String): OutboundQueueItem? {
        val sessionItems = items.values.filter { it.sessionId == sessionId }
        if (sessionItems.any { it.status == OutboundQueueStatus.Sending }) return null
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
