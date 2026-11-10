package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {
    @Test
    fun draftSummaryReplacesRecapWhenNewer() {
        val draft = SessionDraft(sessionId = "a", text = "还没发出去", savedAtMs = 20)
        assertEquals("还没发出去", sessionListSummary("旧摘要", draft, draftNewer = true))
        assertEquals("旧摘要", sessionListSummary("旧摘要", draft, draftNewer = false))
        assertTrue(SessionDraft(sessionId = "a").isEmpty())
    }

    @Test
    fun readCursorCountsMessagesAfterTheLastReadId() {
        val ids = listOf("m1", "m2", "m3", "m4")
        assertEquals(0, unreadAfter(ids, "m4"))
        assertEquals(2, unreadAfter(ids, "m2"))
        assertEquals(3, firstUnreadIndex(ids, "m3"))
        assertEquals(-1, firstUnreadIndex(ids, "m4"))
        assertEquals("m4", advanceReadCursor("m2", "m4", atBottom = true, ids))
        assertEquals("m2", advanceReadCursor("m2", "m4", atBottom = false, ids))
    }

    @Test
    fun scrollRestorePrefersMessageId() {
        val ids = listOf("old", "anchor", "new")
        val byId = restoreScrollIndex(ids, ScrollAnchor(messageId = "anchor", index = 0, offset = 12), 2)
        assertEquals(1 to 12, byId)
        val fallback = restoreScrollIndex(ids, ScrollAnchor(messageId = "gone", index = 2, offset = 4), 2)
        assertEquals(2 to 4, fallback)
        // v0.1.64: index 0 is the first LazyList row, not a jump to the tail.
        val start = restoreScrollIndex(ids, ScrollAnchor(messageId = "gone", index = 0, offset = 9), 2)
        assertEquals(0 to 9, start)
        assertEquals(0 to 0, restoreScrollIndex(ids, null, 2))
    }
}