package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadSeparatorTest {
    @Test
    fun separatorStaysAboveTheFirstUnreadMessage() {
        val ids = listOf("a", "b", "c")
        val rows = withUnreadSeparator(ids, "a")
        assertEquals("unread-b", rows[1].key)
        assertEquals("b", rows[2].key)
        val prepended = withUnreadSeparator(listOf("older", "a", "b", "c"), "a")
        val separator = prepended.filterIsInstance<UnreadSeparatorRow>().single()
        assertEquals("b", separator.beforeMessageId)
        assertEquals(2, separator.count)
        assertTrue(withUnreadSeparator(ids, "c").none { it is UnreadSeparatorRow })
        val withUnread = withUnreadSeparator(ids, "a")
        assertEquals(1, jumpTargetIndex(withUnread, seenUnread = false))
        assertEquals(withUnread.lastIndex, jumpTargetIndex(withUnread, seenUnread = true))
    }
}