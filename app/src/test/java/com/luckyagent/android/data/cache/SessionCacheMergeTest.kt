package com.luckyagent.android.data.cache

import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.data.api.RuntimeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCacheMergeTest {
    @Test
    fun unchangedCountAndUpdatedAtSkipsBodyFetch() {
        val cached = CachedSessionMeta(messageCount = 12, updatedAt = "t2", reachedOldest = false)
        val remote = RuntimeSession(id = "s", messageCount = 12, updatedAt = "t2")
        assertEquals(HistorySync.Unchanged, historySyncAction(cached, remote))
    }

    @Test
    fun grownCountMergesLatestPage() {
        val cached = CachedSessionMeta(messageCount = 12, updatedAt = "t1", reachedOldest = false)
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

        assertEquals(listOf("running", "after"), merged.page.messages.map { it.content })
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
    fun capKeepsTheNewest200AndMarksOldestMissing() {
        val page = page(
            start = 0,
            count = 250,
            messages = (0 until 250).map { msg("m$it") },
            reachedOldest = true,
        )

        val capped = capLatestMessages(page)

        assertEquals(200, capped.messages.size)
        assertEquals(50, capped.startIndex)
        assertEquals("m50", capped.messages.first().content)
        assertEquals("m249", capped.messages.last().content)
        assertEquals(false, capped.reachedOldest)
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
