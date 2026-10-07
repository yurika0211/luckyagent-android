package com.luckyagent.android.ui

import com.luckyagent.android.data.api.MediaAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSendQueueTest {
    @Test
    fun itemsKeepInsertionOrderAndAreIsolatedBySession() {
        val queue = OutboundChatQueue()
        queue.add(item("first", sessionId = "s1"))
        queue.add(item("second", sessionId = "s1"))
        queue.add(item("other-session", sessionId = "s2"))

        assertEquals(listOf("first", "second"), queue.items("s1").map { it.id })
        assertEquals(listOf("other-session"), queue.items("s2").map { it.id })
        assertTrue(queue.items("missing").isEmpty())
    }

    @Test
    fun updateAndFindByRequestTrackSendingState() {
        val queue = OutboundChatQueue()
        queue.add(item("message", sessionId = "s1"))

        val updated = queue.update(
            id = "message",
            status = OutboundQueueStatus.Sending,
            requestId = "request-1",
        )

        assertEquals(OutboundQueueStatus.Sending, updated!!.status)
        assertEquals("request-1", updated.requestId)
        assertEquals("message", queue.findByRequest("s1", "request-1")!!.id)
        assertNull(queue.findByRequest("s2", "request-1"))
    }

    @Test
    fun firstQueuedSkipsFailedAndBlocksWhileSending() {
        val queue = OutboundChatQueue()
        queue.add(item("failed", status = OutboundQueueStatus.Failed))
        queue.add(item("next"))
        queue.add(item("after"))

        assertEquals("next", queue.firstQueued("s1")!!.id)
        queue.update("next", status = OutboundQueueStatus.Sending)
        assertNull(queue.firstQueued("s1"))
        queue.remove("next")
        assertEquals("after", queue.firstQueued("s1")!!.id)
    }

    @Test
    fun sessionIdsIncludeEveryQueuedSession() {
        val queue = OutboundChatQueue()
        queue.add(item("one", sessionId = "s1"))
        queue.add(item("two", sessionId = "s2"))
        assertEquals(setOf("s1", "s2"), queue.sessionIds())
    }

    @Test
    fun retryCanMoveFailedItemBackToQueueAndClearError() {
        val queue = OutboundChatQueue()
        queue.add(item("retry", status = OutboundQueueStatus.Failed, error = "offline"))

        val retried = queue.update(
            id = "retry",
            status = OutboundQueueStatus.Queued,
            clearError = true,
        )

        assertEquals(OutboundQueueStatus.Queued, retried!!.status)
        assertNull(retried.error)
    }

    @Test
    fun removeReturnsItemAndDeletesIt() {
        val queue = OutboundChatQueue()
        val attachment = MediaAttachment(type = "image", fileName = "a.png")
        queue.add(item("with-media", attachments = listOf(attachment)))

        val removed = queue.remove("with-media")

        assertEquals(listOf(attachment), removed!!.attachments)
        assertNull(queue.remove("with-media"))
        assertTrue(queue.items("s1").isEmpty())
    }

    @Test
    fun removeSessionClearsOnlyThatSessionsItems() {
        val queue = OutboundChatQueue()
        queue.add(item("one", sessionId = "s1"))
        queue.add(item("two", sessionId = "s2"))

        queue.removeSession("s1")

        assertTrue(queue.items("s1").isEmpty())
        assertEquals(listOf("two"), queue.items("s2").map { it.id })
    }

    private fun item(
        id: String,
        sessionId: String = "s1",
        status: OutboundQueueStatus = OutboundQueueStatus.Queued,
        error: String? = null,
        attachments: List<MediaAttachment> = emptyList(),
    ) = OutboundQueueItem(
        id = id,
        sessionId = sessionId,
        preview = id,
        message = "message-$id",
        attachments = attachments,
        status = status,
        error = error,
    )
}
