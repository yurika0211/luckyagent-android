package com.luckyagent.android.ui

import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ProviderMessage
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
    fun firstQueuedSkipsFailedAndAllowsTheRuntimeQueueToFill() {
        val queue = OutboundChatQueue()
        queue.add(item("failed", status = OutboundQueueStatus.Failed))
        queue.add(item("next"))
        queue.add(item("after"))

        assertEquals("next", queue.firstQueued("s1")!!.id)
        queue.update("next", status = OutboundQueueStatus.Sending)
        assertEquals("after", queue.firstQueued("s1")!!.id)
        queue.update("after", status = OutboundQueueStatus.Sending)
        assertNull(queue.firstQueued("s1"))
        queue.remove("next")
        queue.remove("after")
        assertNull(queue.firstQueued("s1"))
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

    @Test
    fun storedRequestIdRetiresTheOutboundRow() {
        val pending = listOf(item("outbound-1", message = "hi"))
        val history = listOf(userMessage("hi", clientMessageId = "outbound-1"))

        assertEquals(setOf("outbound-1"), confirmedOutboundIds(pending, history))
    }

    @Test
    fun identicalStoredTextRetiresOneOutstandingRowOldestFirst() {
        val pending = listOf(
            item("first", message = "status?", createdAt = 1),
            item("second", message = "status?", createdAt = 2),
        )
        val history = listOf(userMessage("status?", createdAt = "1970-01-01T00:00:00.001Z"))

        assertEquals(setOf("first"), confirmedOutboundIds(pending, history))
    }

    @Test
    fun earlierIdenticalHistoryDoesNotRetireALaterUnsentRow() {
        val pending = listOf(item("later", message = "好", createdAt = 2_000))
        val history = listOf(userMessage("好", createdAt = "1970-01-01T00:00:00.001Z"))

        assertTrue(confirmedOutboundIds(pending, history).isEmpty())
    }

    @Test
    fun storedTextWithoutATimestampStillConfirmsUntilTheServerEchoesIds() {
        val pending = listOf(item("outbound-1", message = "status?", createdAt = 5_000))
        val history = listOf(userMessage("status?"))

        assertEquals(setOf("outbound-1"), confirmedOutboundIds(pending, history))
    }

    @Test
    fun anIdClaimedHistoryLineCannotAlsoConfirmAnotherRowByText() {
        val pending = listOf(
            item("claimed", message = "好", createdAt = 1),
            item("later", message = "好", createdAt = 2),
        )
        val history = listOf(userMessage("好", clientMessageId = "claimed", createdAt = "1970-01-01T00:00:02Z"))

        assertEquals(setOf("claimed"), confirmedOutboundIds(pending, history))
    }

    @Test
    fun unconfirmedRowsStayPending() {
        val pending = listOf(item("outbound-1", message = "never stored"))
        val history = listOf(userMessage("something else"), userMessage("and another"))

        assertTrue(confirmedOutboundIds(pending, history).isEmpty())
    }

    @Test
    fun aPendingCopySitsBeforeItsOwnAnswerNotAfterIt() {
        val stored = listOf(
            bubble("u-1", "user", "question", "2026-10-29T10:00:00Z"),
            bubble("a-1", "assistant", "answer", "2026-10-29T10:00:05Z"),
        )
        val pending = listOf(bubble("out-1", "user", "question", "2026-10-29T10:00:00Z"))

        val shown = interleavePendingOutbound(stored, pending)

        assertEquals(listOf("u-1", "out-1", "a-1"), shown.map { it.id })
    }

    @Test
    fun aPendingRowNewerThanEveryStoredRowStaysAtTheEnd() {
        val stored = listOf(bubble("a-1", "assistant", "old answer", "2026-10-29T10:00:05Z"))
        val pending = listOf(bubble("out-1", "user", "next question", "2026-10-29T10:01:00Z"))

        val shown = interleavePendingOutbound(stored, pending)

        assertEquals(listOf("a-1", "out-1"), shown.map { it.id })
    }

    @Test
    fun aPendingRowWithoutATimestampIsNotGuessedIntoTheHistory() {
        val stored = listOf(
            bubble("u-1", "user", "question", "2026-10-29T10:00:00Z"),
            bubble("a-1", "assistant", "answer", "2026-10-29T10:00:05Z"),
        )
        val pending = listOf(bubble("out-1", "user", "question", null))

        val shown = interleavePendingOutbound(stored, pending)

        assertEquals(listOf("u-1", "a-1", "out-1"), shown.map { it.id })
    }

    @Test
    fun assistantTextNeverConfirmsAUserRow() {
        val pending = listOf(item("outbound-1", message = "hi"))
        val history = listOf(ProviderMessage(role = "assistant", content = "hi"))

        assertTrue(confirmedOutboundIds(pending, history).isEmpty())
    }

    private fun bubble(
        id: String,
        role: String,
        content: String,
        createdAt: String?,
    ) = ChatBubble(id = id, role = role, content = content, createdAt = createdAt)

    private fun userMessage(
        content: String,
        clientMessageId: String? = null,
        createdAt: String? = null,
    ) = ProviderMessage(
        role = "user",
        content = content,
        clientMessageId = clientMessageId,
        createdAt = createdAt,
    )

    private fun item(
        id: String,
        sessionId: String = "s1",
        status: OutboundQueueStatus = OutboundQueueStatus.Queued,
        error: String? = null,
        attachments: List<MediaAttachment> = emptyList(),
        message: String = "message-$id",
        createdAt: Long = 0,
    ) = OutboundQueueItem(
        id = id,
        sessionId = sessionId,
        preview = id,
        message = message,
        attachments = attachments,
        status = status,
        error = error,
        createdAt = createdAt,
    )
}
