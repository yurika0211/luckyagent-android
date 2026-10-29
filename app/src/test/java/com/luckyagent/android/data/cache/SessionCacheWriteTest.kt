package com.luckyagent.android.data.cache

import com.luckyagent.android.data.api.HistoryToolCall
import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.ui.OutboundQueueItem
import com.luckyagent.android.ui.confirmedOutboundIds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCacheWriteTest {
    @Test
    fun secondSaveOfTheSamePageDoesNotRewriteMessages() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val page = page(start = 3, count = 5, messages = listOf(msg("a"), msg("b")))

        cache.savePage("http://127.0.0.1:9090", "s", "title", "t1", null, page, opened = true, now = 10)
        val stored = dao.messages.single { it.ordinal == 3 }.rawJson
        dao.replaceCalls = 0
        dao.patchCalls = 0
        dao.upsertMessageCalls = 0

        cache.savePage("http://127.0.0.1:9090", "s", "title", "t1", null, page, opened = true, now = 20)

        assertEquals(0, dao.replaceCalls)
        assertEquals(0, dao.patchCalls)
        assertEquals(0, dao.upsertMessageCalls)
        assertSame(stored, dao.messages.single { it.ordinal == 3 }.rawJson)
        assertEquals(20, dao.sessions.single().lastOpenedAt)
    }

    @Test
    fun newTailInsertsOnlyTheNewRow() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            "title",
            "t1",
            null,
            page(start = 3, count = 5, messages = listOf(msg("a"), msg("b"))),
            opened = true,
            now = 10,
        )
        val kept = dao.messages.single { it.ordinal == 3 }
        dao.replaceCalls = 0
        dao.patchCalls = 0

        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            "title",
            "t2",
            null,
            page(start = 3, count = 6, messages = listOf(msg("a"), msg("b"), msg("c"))),
            opened = true,
            now = 20,
        )

        assertEquals(0, dao.replaceCalls)
        assertEquals(1, dao.patchCalls)
        assertSame(kept, dao.messages.single { it.ordinal == 3 })
        assertEquals("c", dao.messages.single { it.ordinal == 5 }.content)
        assertEquals(listOf(3, 4, 5), dao.messages.map { it.ordinal })
    }

    @Test
    fun callerSuppliedStoredPageIsNotReadAgain() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val first = page(start = 3, count = 5, messages = listOf(msg("a"), msg("b")))
        cache.savePage("http://127.0.0.1:9090", "s", "title", "t1", null, first, opened = true, now = 10)
        val stored = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("cache should be readable")
        dao.messageReads = 0

        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            "title",
            "t2",
            null,
            page(start = 3, count = 6, messages = listOf(msg("a"), msg("b"), msg("c"))),
            opened = true,
            now = 20,
            stored = stored,
        )

        // Refresh already decoded the cache once; the write must not decode it again.
        assertEquals(0, dao.messageReads)
        assertEquals(listOf(3, 4, 5), dao.messages.map { it.ordinal })
    }

    @Test
    fun disjointPageStillReplaces() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 2, messages = listOf(msg("a"), msg("b"))),
            opened = false,
            now = 10,
        )
        dao.replaceCalls = 0

        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 8, count = 10, messages = listOf(msg("x"), msg("y"))),
            opened = false,
            now = 20,
        )

        assertEquals(1, dao.replaceCalls)
        assertEquals(listOf(8, 9), dao.messages.map { it.ordinal })
    }

    @Test
    fun truncatedPreviewIsNotStoredAsTheFullBody() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val preview = "x".repeat(4000) + "…[truncated]"
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 1, messages = listOf(msg(preview).copy(contentTruncated = true))),
            opened = true,
            now = 10,
        )

        val row = dao.messages.single()
        assertEquals(false, row.rawJson.contains("\"content\""))
        assertEquals(preview, row.content)

        val loaded = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("preview should still open")
        assertTrue(loaded.messages.single().contentTruncated)
        assertEquals(preview, loaded.messages.single().content)
    }

    @Test
    fun fullHistoryIsStoredWithoutATruncationWindow() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)

        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            "title",
            "t1",
            null,
            page(start = 0, count = 250, messages = (0 until 250).map { msg("m$it") }),
            opened = true,
            now = 10,
        )

        assertEquals((0 until 250).toList(), dao.messages.map { it.ordinal })
        assertEquals("m0", dao.messages.first().content)
        assertEquals(250, dao.sessions.single().messageCount)
    }

    @Test
    fun truncatedRowKeepsAttachmentsAndToolCalls() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val preview = "body\n...[truncated]"
        val message = msg(preview).copy(
            role = "assistant",
            contentTruncated = true,
            attachments = listOf(MediaAttachment(type = "file", fileName = "a.txt")),
            toolCalls = listOf(HistoryToolCall(id = "call-1", name = "file_read", arguments = "{}")),
        )
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 1, messages = listOf(message)),
            opened = true,
            now = 10,
        )

        val loaded = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("truncated row should open")
        val row = loaded.messages.single()
        assertTrue(row.contentTruncated)
        assertEquals(preview, row.content)
        assertEquals("a.txt", row.attachments.single().fileName)
        assertEquals("call-1", row.toolCalls.single().id)
        assertEquals(false, dao.messages.single().rawJson.contains("\"content\""))
    }

    @Test
    fun previewWordInsideToolArgumentsDoesNotMarkAFullRow() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val message = msg("saved body").copy(
            role = "assistant",
            toolCalls = listOf(
                HistoryToolCall(id = "call-1", name = "file_read", arguments = "{\"path\":\"preview.html\"}"),
            ),
        )
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 1, messages = listOf(message)),
            opened = true,
            now = 10,
        )

        val loaded = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("full row should open")
        val row = loaded.messages.single()
        assertEquals(false, row.contentTruncated)
        assertEquals("saved body", row.content)
        assertEquals("{\"path\":\"preview.html\"}", row.toolCalls.single().arguments)
    }

    @Test
    fun olderPreviewStoredAsCompleteJsonIsMarkedTruncatedOnOpen() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val preview = "old body\n...[truncated]"
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 1, messages = listOf(msg(preview).copy(role = "assistant"))),
            opened = true,
            now = 10,
        )

        val loaded = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("old preview should open")
        assertTrue(loaded.messages.single().contentTruncated)
        assertEquals(preview, loaded.messages.single().content)
    }

    @Test
    fun outboundRowsRoundTripAttachmentsAndCanBeRemoved() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val row = CachedOutboundMessage(
            sessionId = "s",
            clientMessageId = "outbound-1",
            preview = "hello",
            message = "hello",
            attachments = listOf(MediaAttachment(type = "image", fileName = "a.png")),
            status = "Sending",
            requestId = "outbound-1",
            error = null,
            createdAt = 10,
            updatedAt = 11,
        )

        cache.saveOutbound("http://127.0.0.1:9090", row)

        assertEquals(listOf(row), cache.listOutbound("http://127.0.0.1:9090"))
        cache.removeOutbound("http://127.0.0.1:9090", "s", "outbound-1")
        assertEquals(emptyList<CachedOutboundMessage>(), cache.listOutbound("http://127.0.0.1:9090"))
    }

    @Test
    fun plainCachedMessageOpensWithoutDecodingRawJson() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 1, messages = listOf(msg("hello"))),
            opened = true,
            now = 10,
        )
        dao.messages[0] = dao.messages[0].copy(rawJson = "{not-json")

        val loaded = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("plain row should open from columns")
        assertEquals("hello", loaded.messages.single().content)
        assertEquals(false, loaded.messages.single().contentTruncated)
    }

    @Test
    fun attachmentRowStillDecodesRawJson() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val attached = msg("see file").copy(
            attachments = listOf(MediaAttachment(type = "file", fileName = "a.txt")),
        )
        cache.savePage(
            "http://127.0.0.1:9090",
            "s",
            null,
            null,
            null,
            page(start = 0, count = 1, messages = listOf(attached)),
            opened = true,
            now = 10,
        )

        val loaded = cache.loadPage("http://127.0.0.1:9090", "s") ?: error("attachment row should open")
        assertEquals("a.txt", loaded.messages.single().attachments.single().fileName)
    }

    @Test
    fun staleSessionDroppedFromTheRemoteListDeletesItsOutbox() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090",
            "gone",
            "old",
            "t1",
            null,
            page(start = 0, count = 1, messages = listOf(msg("kept"))),
            opened = true,
            now = 10,
        )
        cache.savePage(
            "http://127.0.0.1:9090",
            "live",
            "current",
            "t1",
            null,
            page(start = 0, count = 1, messages = listOf(msg("stay"))),
            opened = true,
            now = 10,
        )
        cache.saveOutbound("http://127.0.0.1:9090", outbound("gone", "outbound-gone"))
        cache.saveOutbound("http://127.0.0.1:9090", outbound("live", "outbound-live"))

        cache.saveSessionList(
            "http://127.0.0.1:9090",
            listOf(RuntimeSession(id = "live", title = "current")),
            now = 20,
        )

        assertEquals(listOf("live"), dao.sessions.map { it.sessionId })
        assertEquals(listOf("live"), dao.messages.map { it.sessionId }.distinct())
        assertEquals(listOf("outbound-live"), cache.listOutbound("http://127.0.0.1:9090").map { it.clientMessageId })
    }

    @Test
    fun sessionListGrowthDoesNotConfirmHistoryThatWasNeverFetched() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090", "s", "old title", "t1", null,
            page(0, 2, listOf(msg("a"), msg("b"))), opened = true, now = 10,
        )
        val remote = RuntimeSession(id = "s", title = "new title", messageCount = 4, updatedAt = "t2")

        cache.saveSessionList("http://127.0.0.1:9090", listOf(remote), now = 20)

        val meta = cache.sessionMeta("http://127.0.0.1:9090", "s")
        assertEquals(CachedSessionMeta(2, "t1", reachedOldest = true), meta)
        assertEquals(HistorySync.MergeLatest, historySyncAction(meta, remote))
        assertEquals(2, cache.loadPage("http://127.0.0.1:9090", "s")?.messageCount)
        assertEquals(4, dao.sessions.single().messageCount)
        assertEquals("new title", dao.sessions.single().title)
    }

    @Test
    fun sessionListEditWithTheSameCountStillInvalidatesHistory() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090", "s", null, "t1", null,
            page(0, 1, listOf(msg("old answer"))), opened = true, now = 10,
        )
        val remote = RuntimeSession(id = "s", messageCount = 1, updatedAt = "t2")

        cache.saveSessionList("http://127.0.0.1:9090", listOf(remote), now = 20)

        assertEquals(HistorySync.Invalidate, historySyncAction(cache.sessionMeta("http://127.0.0.1:9090", "s"), remote))
    }

    @Test
    fun plainMessageRetainsClientIdAcrossCacheReopen() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val message = msg("hello").copy(clientMessageId = "outbound-1")
        cache(dao).savePage(
            "http://127.0.0.1:9090", "s", null, "t1", null,
            page(0, 1, listOf(message)), opened = true, now = 10,
        )

        val loaded = cache(dao).loadPage("http://127.0.0.1:9090", "s") ?: error("stored history should reopen")

        assertEquals(message, loaded.messages.single())
    }

    @Test
    fun unchangedHistoryWithClientIdsDoesNotRewriteOnEveryRefresh() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val page = page(0, 250, (0 until 250).map { msg("m$it").copy(clientMessageId = "request-$it") })
        cache.savePage("http://127.0.0.1:9090", "s", null, "t1", null, page, opened = true, now = 10)
        dao.replaceCalls = 0
        dao.patchCalls = 0
        dao.upsertMessageCalls = 0

        cache.savePage("http://127.0.0.1:9090", "s", null, "t1", null, page, opened = true, now = 20)

        assertEquals(0, dao.replaceCalls)
        assertEquals(0, dao.patchCalls)
        assertEquals(0, dao.upsertMessageCalls)
    }

    @Test
    fun blobSizeWithoutAHashSurvivesCacheReopen() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val message = msg("body").copy(blobBytes = 123)
        cache(dao).savePage(
            "http://127.0.0.1:9090", "s", null, "t1", null,
            page(0, 1, listOf(message)), opened = true, now = 10,
        )

        assertEquals(message, cache(dao).loadPage("http://127.0.0.1:9090", "s")?.messages?.single())
    }

    @Test
    fun legacySnapshotNeedsVerificationWithoutLosingHistoryOrOutbox() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val page = page(0, 2, listOf(msg("a"), msg("b")))
        cache.savePage("http://127.0.0.1:9090", "s", null, "t1", null, page, opened = true, now = 10)
        cache.saveOutbound("http://127.0.0.1:9090", outbound("s", "pending"))
        // v3 stored a newer listing in the same columns as the older history.
        dao.sessions[0] = dao.sessions[0].copy(
            messageCount = 7,
            updatedAt = "t2",
            historyMessageCount = null,
            historyUpdatedAt = null,
        )
        val reopened = cache(dao)

        assertNull(reopened.sessionMeta("http://127.0.0.1:9090", "s"))
        assertEquals(page, reopened.loadPage("http://127.0.0.1:9090", "s"))
        assertEquals(listOf("pending"), reopened.listOutbound("http://127.0.0.1:9090").map { it.clientMessageId })

        reopened.savePage("http://127.0.0.1:9090", "s", null, "t1", null, page, opened = true, now = 20)

        assertEquals(CachedSessionMeta(2, "t1", reachedOldest = true), reopened.sessionMeta("http://127.0.0.1:9090", "s"))
    }

    @Test
    fun cachedKnownIdDoesNotConfirmADifferentQueuedMessageWithTheSameText() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val history = msg("hello").copy(createdAt = null, clientMessageId = "already-sent")
        cache(dao).savePage(
            "http://127.0.0.1:9090", "s", null, "t1", null,
            page(0, 1, listOf(history)), opened = true, now = 10,
        )
        cache(dao).saveOutbound(
            "http://127.0.0.1:9090",
            outbound("s", "not-sent").copy(preview = "hello", message = "hello"),
        )
        val reopened = cache(dao)
        val pending = reopened.listOutbound("http://127.0.0.1:9090").map { row ->
            OutboundQueueItem(
                id = row.clientMessageId,
                sessionId = row.sessionId,
                preview = row.preview,
                message = row.message,
                createdAt = row.createdAt,
            )
        }
        val messages = reopened.loadPage("http://127.0.0.1:9090", "s")!!.messages

        assertEquals(emptySet<String>(), confirmedOutboundIds(pending, messages))
        assertEquals(setOf("already-sent"), confirmedOutboundIds(pending + pending.single().copy(id = "already-sent"), messages))
    }

    @Test
    fun tailWithClientIdsInsertsOnlyTheNewRow() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val messages = (0 until 250).map { msg("m$it").copy(clientMessageId = "request-$it") }
        cache.savePage(
            "http://127.0.0.1:9090", "s", null, "t1", null,
            page(0, 250, messages), opened = true, now = 10,
        )
        val kept = dao.messages.toList()
        dao.replaceCalls = 0
        dao.patchCalls = 0
        dao.upsertMessageCalls = 0

        cache.savePage(
            "http://127.0.0.1:9090", "s", null, "t2", null,
            page(0, 251, messages + msg("new").copy(clientMessageId = "request-new")),
            opened = true, now = 20,
        )

        assertEquals(0, dao.replaceCalls)
        assertEquals(1, dao.patchCalls)
        assertEquals(1, dao.upsertMessageCalls)
        kept.forEachIndexed { index, row -> assertSame(row, dao.messages[index]) }
        assertEquals("new", dao.messages.single { it.ordinal == 250 }.content)
    }

    @Test
    fun historyWithoutTimestampDoesNotBorrowTheListingValidator() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        val remote = RuntimeSession(id = "s", messageCount = 1, updatedAt = "t2")
        cache.saveSessionList("http://127.0.0.1:9090", listOf(remote), now = 10)

        cache.savePage(
            "http://127.0.0.1:9090", "s", null, null, null,
            page(0, 1, listOf(msg("a"))), opened = true, now = 20,
        )

        val meta = cache.sessionMeta("http://127.0.0.1:9090", "s")
        assertNull(meta?.updatedAt)
        assertEquals(HistorySync.Invalidate, historySyncAction(meta, remote))
        assertEquals("t2", dao.sessions.single().updatedAt)
    }

    @Test
    fun openingASessionChangesOnlyItsOpenTime() = kotlinx.coroutines.runBlocking {
        val dao = FakeDao()
        val cache = cache(dao)
        cache.savePage(
            "http://127.0.0.1:9090", "s", null, "t1", null,
            page(0, 1, listOf(msg("a"))), opened = true, now = 10,
        )
        val before = dao.sessions.single()

        cache.touchOpened("http://127.0.0.1:9090", "s", openedAt = 30)

        assertEquals(before.copy(lastOpenedAt = 30), dao.sessions.single())
    }

    private fun cache(dao: FakeDao) = SessionCacheRepository(
        dao,
        Json { ignoreUnknownKeys = true; explicitNulls = false },
    )

    private fun msg(content: String) = ProviderMessage(role = "user", content = content, createdAt = "c-$content")

    private fun outbound(sessionId: String, clientMessageId: String) = CachedOutboundMessage(
        sessionId = sessionId,
        clientMessageId = clientMessageId,
        preview = "pending",
        message = "pending",
        attachments = emptyList(),
        status = "Queued",
        requestId = null,
        error = null,
        createdAt = 10,
        updatedAt = 11,
    )

    private fun page(start: Int, count: Int, messages: List<ProviderMessage>) = MessagePage(
        messages = messages,
        startIndex = start,
        messageCount = count,
        reachedOldest = start == 0,
    )
}

private class FakeDao : SessionCacheDao {
    val sessions = mutableListOf<CachedSessionEntity>()
    val messages = mutableListOf<CachedMessageEntity>()
    val outbound = mutableListOf<CachedOutboundEntity>()
    var replaceCalls = 0
    var patchCalls = 0
    var upsertMessageCalls = 0
    var messageReads = 0

    override suspend fun listSessions(endpoint: String): List<CachedSessionRow> = emptyList()

    override suspend fun session(endpoint: String, sessionId: String): CachedSessionEntity? =
        sessions.firstOrNull { it.endpoint == endpoint && it.sessionId == sessionId }

    override suspend fun messages(endpoint: String, sessionId: String): List<CachedMessageEntity> {
        messageReads += 1
        return messages.filter { it.endpoint == endpoint && it.sessionId == sessionId }.sortedBy { it.ordinal }
    }

    override suspend fun outbound(endpoint: String): List<CachedOutboundEntity> =
        outbound.filter { it.endpoint == endpoint }.sortedWith(compareBy({ it.createdAt }, { it.clientMessageId }))

    override suspend fun upsertSession(entity: CachedSessionEntity) {
        sessions.removeAll { it.endpoint == entity.endpoint && it.sessionId == entity.sessionId }
        sessions += entity
    }

    override suspend fun touchOpened(endpoint: String, sessionId: String, openedAt: Long) {
        val index = sessions.indexOfFirst { it.endpoint == endpoint && it.sessionId == sessionId }
        if (index >= 0) sessions[index] = sessions[index].copy(lastOpenedAt = openedAt)
    }

    override suspend fun upsertMessages(entities: List<CachedMessageEntity>) {
        upsertMessageCalls += 1
        entities.forEach { entity ->
            messages.removeAll {
                it.endpoint == entity.endpoint && it.sessionId == entity.sessionId && it.ordinal == entity.ordinal
            }
            messages += entity
        }
    }

    override suspend fun upsertOutbound(entity: CachedOutboundEntity) {
        outbound.removeAll {
            it.endpoint == entity.endpoint &&
                it.sessionId == entity.sessionId &&
                it.clientMessageId == entity.clientMessageId
        }
        outbound += entity
    }

    override suspend fun deleteOutbound(endpoint: String, sessionId: String, clientMessageId: String) {
        outbound.removeAll {
            it.endpoint == endpoint && it.sessionId == sessionId && it.clientMessageId == clientMessageId
        }
    }

    override suspend fun deleteOutboundSession(endpoint: String, sessionId: String) {
        outbound.removeAll { it.endpoint == endpoint && it.sessionId == sessionId }
    }

    override suspend fun deleteMessages(endpoint: String, sessionId: String) {
        messages.removeAll { it.endpoint == endpoint && it.sessionId == sessionId }
    }

    override suspend fun deleteSession(endpoint: String, sessionId: String) {
        sessions.removeAll { it.endpoint == endpoint && it.sessionId == sessionId }
    }

    override suspend fun deleteMessagesBefore(endpoint: String, sessionId: String, minOrdinal: Int) {
        messages.removeAll { it.endpoint == endpoint && it.sessionId == sessionId && it.ordinal < minOrdinal }
    }

    override suspend fun deleteMessagesFrom(endpoint: String, sessionId: String, fromOrdinal: Int) {
        messages.removeAll { it.endpoint == endpoint && it.sessionId == sessionId && it.ordinal >= fromOrdinal }
    }

    override suspend fun sessionIdsByRecentOpen(endpoint: String): List<String> =
        sessions.filter { it.endpoint == endpoint }.map { it.sessionId }

    override suspend fun replaceMessages(
        session: CachedSessionEntity,
        messages: List<CachedMessageEntity>,
        minOrdinal: Int,
    ) {
        replaceCalls += 1
        upsertSession(session)
        deleteMessages(session.endpoint, session.sessionId)
        if (messages.isNotEmpty()) upsertMessages(messages)
        deleteMessagesBefore(session.endpoint, session.sessionId, minOrdinal)
    }

    override suspend fun patchMessages(
        session: CachedSessionEntity,
        inserts: List<CachedMessageEntity>,
        deleteBefore: Int,
        deleteFrom: Int,
    ) {
        patchCalls += 1
        upsertSession(session)
        deleteMessagesFrom(session.endpoint, session.sessionId, deleteFrom)
        deleteMessagesBefore(session.endpoint, session.sessionId, deleteBefore)
        if (inserts.isNotEmpty()) upsertMessages(inserts)
    }
}
