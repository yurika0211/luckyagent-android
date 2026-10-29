package com.luckyagent.android.data.cache

import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCacheMergeTest {
    @Test
    fun unchangedCountAndUpdatedAtSkipsBodyFetch() {
        val cached = CachedSessionMeta(messageCount = 12, updatedAt = "t2", reachedOldest = true)
        val remote = RuntimeSession(id = "s", messageCount = 12, updatedAt = "t2")
        assertEquals(HistorySync.Unchanged, historySyncAction(cached, remote))
    }

    @Test
    fun partialCacheForcesFullReplacementEvenWhenMetadataIsUnchanged() {
        val cached = CachedSessionMeta(messageCount = 12, updatedAt = "t2", reachedOldest = false)
        val remote = RuntimeSession(id = "s", messageCount = 12, updatedAt = "t2")
        assertEquals(HistorySync.Invalidate, historySyncAction(cached, remote))
    }

    @Test
    fun grownCountMergesLatestPage() {
        val cached = CachedSessionMeta(messageCount = 12, updatedAt = "t1", reachedOldest = true)
        val remote = RuntimeSession(id = "s", messageCount = 14, updatedAt = "t2")
        assertEquals(HistorySync.MergeLatest, historySyncAction(cached, remote))
    }

    @Test
    fun shrunkCountInvalidates() {
        val cached = CachedSessionMeta(messageCount = 12, updatedAt = "t2", reachedOldest = true)
        val remote = RuntimeSession(id = "s", messageCount = 3, updatedAt = "t3")
        assertEquals(HistorySync.Invalidate, historySyncAction(cached, remote))
    }

    @Test
    fun missingUpdatedAtInvalidates() {
        val cached = CachedSessionMeta(messageCount = 2, updatedAt = null, reachedOldest = true)
        val remote = RuntimeSession(id = "s", messageCount = 2, updatedAt = "t")
        assertEquals(HistorySync.Invalidate, historySyncAction(cached, remote))
    }

    @Test
    fun latestPageAppendsOnlyTheNewTail() {
        val cached = page(
            start = 80,
            count = 120,
            messages = (80 until 120).map { msg("m$it", created = "c$it") },
        )
        val remote = (100 until 140).map { msg("m$it", created = "c$it") }

        val merged = mergeLatestPage(cached, remote, remoteCount = 140) as LatestMerge.Appended

        assertEquals(80, merged.page.startIndex)
        assertEquals(140, merged.page.messageCount)
        assertEquals((80 until 140).map { "m$it" }, merged.page.messages.map { it.content })
        assertEquals(false, merged.page.reachedOldest)
    }

    @Test
    fun latestPageWithoutOverlapInvalidates() {
        val cached = page(start = 0, count = 2, messages = listOf(msg("old-a"), msg("old-b")))
        val remote = listOf(msg("new-a"), msg("new-b"))
        assertTrue(mergeLatestPage(cached, remote, remoteCount = 4) is LatestMerge.Invalidate)
    }

    @Test
    fun toolCallIdMatchesWhenContentDiffers() {
        val cached = page(
            start = 0,
            count = 1,
            messages = listOf(msg("running", toolCallId = "call-1")),
            reachedOldest = true,
        )
        val remote = listOf(
            msg("done", toolCallId = "call-1"),
            msg("after"),
        )

        val merged = mergeLatestPage(cached, remote, remoteCount = 2) as LatestMerge.Appended

        // Same tool call, newer body. The finished result replaces the running row.
        assertEquals(listOf("done", "after"), merged.page.messages.map { it.content })
    }

    @Test
    fun olderOffsetSkipsShownMessagesNotTheOldestIndex() {
        // 100 messages, screen shows 60–99. offset=60 would skip 40–59.
        assertEquals(40, olderHistoryOffset(shownCount = 40))
        assertEquals(0, olderHistoryOffset(shownCount = 0))
    }

    @Test
    fun olderPagePrependsWhenItEndsAtTheCacheStart() {
        val cached = page(
            start = 40,
            count = 80,
            messages = (40 until 80).map { msg("m$it") },
        )
        val older = (0 until 40).map { msg("m$it") }

        val merged = prependOlderPage(cached, older, remoteCount = 80, serverHasMore = false) as LatestMerge.Appended

        assertEquals(0, merged.page.startIndex)
        assertEquals(80, merged.page.messages.size)
        assertEquals(true, merged.page.reachedOldest)
    }

    @Test
    fun olderPageThatOverlapsCacheInvalidates() {
        val cached = page(start = 40, count = 80, messages = (40 until 80).map { msg("m$it") })
        val older = (20 until 50).map { msg("m$it") }
        assertTrue(prependOlderPage(cached, older, remoteCount = 80, serverHasMore = true) is LatestMerge.Invalidate)
    }

    @Test
    fun olderPageInvalidatesWhenNewMessagesShiftTheOffsetPastTheCachedWindow() {
        val cached = page(start = 60, count = 100, messages = (60 until 100).map { msg("m$it") })
        // 100 new messages arrived after the cached page was painted. The
        // same offset now points at 120–159, which is not older than 60–99.
        val shifted = (120 until 160).map { msg("m$it") }

        assertTrue(
            prependOlderPage(
                cached = cached,
                older = shifted,
                remoteCount = 200,
                serverHasMore = true,
                requestedOffset = 40,
            ) is LatestMerge.Invalidate,
        )
    }

    @Test
    fun latestPageAlreadyAtTheTailSkipsRewrite() {
        val stored = page(start = 0, count = 80, messages = (0 until 80).map { msg("m$it", created = "c$it") })
        val latest = (40 until 80).map { msg("m$it", created = "c$it") }
        assertTrue(latestPageAlreadyStored(stored, latest, remoteCount = 80))
    }

    @Test
    fun storedPreviewOfTheSameRemoteTailStillNeedsARewrite() {
        val stored = page(
            start = 0,
            count = 1,
            messages = listOf(msg("hello\n...[truncated]", created = "c0").copy(contentTruncated = true)),
        )
        val latest = listOf(msg("hello\n...[truncated]", created = "c0").copy(contentTruncated = true))

        // Same text is not enough. The disk row is still a preview, so refresh must not skip it.
        assertEquals(false, latestPageAlreadyStored(stored, latest, remoteCount = 1))
    }

    @Test
    fun attachmentChangeOnTheTailRequiresRewrite() {
        val stored = page(start = 0, count = 1, messages = listOf(msg("a")))
        val latest = listOf(msg("a").copy(attachments = listOf(
            com.luckyagent.android.data.api.MediaAttachment(fileName = "note.txt"),
        )))
        assertEquals(false, latestPageAlreadyStored(stored, latest, remoteCount = 1))
    }

    @Test
    fun unchangedPageDoesNotRewriteCachedMessages() {
        val stored = page(start = 10, count = 14, messages = (10 until 14).map { msg("m$it", created = "c$it") })
        assertEquals(HistoryCacheWrite.Unchanged, historyCacheWrite(stored, stored.copy()))
    }

    @Test
    fun grownTailInsertsOnlyNewOrdinals() {
        val stored = page(start = 10, count = 14, messages = (10 until 14).map { msg("m$it", created = "c$it") })
        val incoming = page(start = 10, count = 16, messages = (10 until 16).map { msg("m$it", created = "c$it") })

        val write = historyCacheWrite(stored, incoming) as HistoryCacheWrite.Patch

        assertEquals(listOf(14, 15), write.inserts.map { it.ordinal })
        assertEquals(listOf("m14", "m15"), write.inserts.map { it.message.content })
        assertEquals(10, write.deleteBefore)
        assertEquals(16, write.deleteFrom)
    }

    @Test
    fun narrowerWindowReplacesInsteadOfDroppingThePrefix() {
        val stored = page(start = 0, count = 4, messages = (0 until 4).map { msg("m$it", created = "c$it") })
        val incoming = page(start = 2, count = 6, messages = (2 until 6).map { msg("m$it", created = "c$it") })

        // Replace drops the stored prefix. Callers must not pass a narrower page
        // unless losing rows 0 and 1 is acceptable.
        assertEquals(HistoryCacheWrite.Replace, historyCacheWrite(stored, incoming))
    }

    @Test
    fun aNarrowerWindowReplacesInsteadOfDroppingUnboundedHistory() {
        val stored = page(
            start = 0,
            count = 200,
            messages = (0 until 200).map { msg("m$it", created = "c$it") },
        )
        val incoming = page(
            start = 1,
            count = 201,
            messages = (1 until 201).map { msg("m$it", created = "c$it") },
        )

        assertEquals(HistoryCacheWrite.Replace, historyCacheWrite(stored, incoming))
    }

    @Test
    fun expandingAStoredPreviewPatchesThatRowInsteadOfReplacingThePage() {
        val preview = "hello\n...[truncated]"
        val full = "hello world, now stored in full"
        val stored = page(
            start = 0,
            count = 2,
            messages = listOf(
                msg(preview, created = "c0").copy(contentTruncated = true),
                msg("next", created = "c1"),
            ),
        )
        val incoming = page(
            start = 0,
            count = 2,
            messages = listOf(msg(full, created = "c0"), msg("next", created = "c1")),
        )

        val write = historyCacheWrite(stored, incoming) as HistoryCacheWrite.Patch

        assertEquals(listOf(0), write.inserts.map { it.ordinal })
        assertEquals(full, write.inserts.single().message.content)
        assertEquals(false, write.inserts.single().message.contentTruncated)
    }

    @Test
    fun previewSuffixDoesNotMakeAnExpandedRowLookNew() {
        val full = "hello world, this is the stored body"
        val preview = "hello\n...[truncated]"
        val stored = page(start = 0, count = 1, messages = listOf(msg(full, created = "c0")))
        val incoming = page(
            start = 0,
            count = 1,
            messages = listOf(msg(preview, created = "c0").copy(contentTruncated = true)),
        )

        // Same window: leave the stored full body alone. Rewriting it would put the preview back.
        assertEquals(HistoryCacheWrite.Unchanged, historyCacheWrite(stored, incoming))

        val grown = page(
            start = 0,
            count = 2,
            messages = listOf(
                msg(preview, created = "c0").copy(contentTruncated = true),
                msg("newer", created = "c1"),
            ),
        )
        val write = historyCacheWrite(stored, grown) as HistoryCacheWrite.Patch
        assertEquals(listOf(1), write.inserts.map { it.ordinal })
        assertEquals("newer", write.inserts.single().message.content)
    }

    @Test
    fun expandedBodySurvivesARefreshThatBringsThePreviewBack() {
        val cached = page(
            start = 0,
            count = 1,
            messages = listOf(msg("hello world, stored in full", created = "c0")),
            reachedOldest = true,
        )
        val remote = listOf(msg("hello\n...[truncated]", created = "c0").copy(contentTruncated = true))

        val merged = mergeLatestPage(cached, remote, remoteCount = 1) as LatestMerge.Appended

        assertEquals("hello world, stored in full", merged.page.messages.single().content)
        assertEquals(false, merged.page.messages.single().contentTruncated)
    }

    @Test
    fun fullPageKeepsAnOlderPreviewMarker() {
        val marked = page(
            start = 0,
            count = 2,
            messages = listOf(
                msg("older preview\n...[truncated]").copy(contentTruncated = true),
                msg("fresh full body"),
            ),
        ).withContentTruncation(false)

        assertEquals(true, marked.messages[0].contentTruncated)
        assertEquals(false, marked.messages[1].contentTruncated)
    }

    @Test
    fun pageFlagOnlyMarksBodiesThatCarryTheServerMarker() {
        val marked = page(
            start = 0,
            count = 2,
            messages = listOf(msg("short"), msg("long body\n...[truncated]")),
        ).withContentTruncation(true)

        assertEquals(false, marked.messages[0].contentTruncated)
        assertEquals(true, marked.messages[1].contentTruncated)
        assertEquals(false, marked.messages[0].content?.endsWith("\n...[truncated]"))
    }

    @Test
    fun changedOverlapStillReplacesThePage() {
        val stored = page(start = 0, count = 2, messages = listOf(msg("old-a"), msg("old-b")))
        val incoming = page(start = 0, count = 2, messages = listOf(msg("old-a"), msg("edited")))
        assertEquals(HistoryCacheWrite.Replace, historyCacheWrite(stored, incoming))
    }

    @Test
    fun attachmentOnlyChangeStillRewritesTheRow() {
        // Identity is unchanged (same role/content/created_at); only the
        // attachment list differs. The stored raw_json must not survive that.
        val stored = page(start = 0, count = 1, messages = listOf(msg("a", created = "c0")))
        val incoming = page(
            start = 0,
            count = 1,
            messages = listOf(
                msg("a", created = "c0").copy(
                    attachments = listOf(com.luckyagent.android.data.api.MediaAttachment(fileName = "note.txt")),
                ),
            ),
        )
        assertEquals(HistoryCacheWrite.Replace, historyCacheWrite(stored, incoming))
    }

    @Test
    fun disjointWindowReplacesInsteadOfAppendingAGap() {
        val stored = page(start = 0, count = 2, messages = listOf(msg("old-a"), msg("old-b")))
        val incoming = page(start = 4, count = 6, messages = listOf(msg("new-a"), msg("new-b")))
        assertEquals(HistoryCacheWrite.Replace, historyCacheWrite(stored, incoming))
    }

    private fun msg(
        content: String,
        created: String? = null,
        role: String = "user",
        toolCallId: String? = null,
    ) = ProviderMessage(
        role = role,
        content = content,
        createdAt = created,
        toolCallId = toolCallId,
    )

    private fun page(
        start: Int,
        count: Int,
        messages: List<ProviderMessage>,
        reachedOldest: Boolean = start == 0,
    ) = MessagePage(
        messages = messages,
        startIndex = start,
        messageCount = count,
        reachedOldest = reachedOldest,
    )
}
