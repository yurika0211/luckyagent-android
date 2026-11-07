package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChatDayTest {
    @Test
    fun dateRowOnlyOnADayChange() {
        val today = LocalDate.of(2026, 11, 7)
        val yesterday = today.minusDays(1)
        val rows = dateRows(listOf("a", "b", "c"), listOf(yesterday, yesterday, today), today)
        assertEquals(2, rows.size)
        assertEquals("昨天", rows[0].label)
        assertEquals("今天", rows[1].label)
        assertEquals("c", rows[1].beforeMessageId)
        assertTrue(bubbleSharesEdge("assistant", "assistant", separated = false))
        assertFalse(bubbleSharesEdge("assistant", "assistant", separated = true))
        assertFalse(bubbleSharesEdge("user", "assistant", separated = false))
    }

    @Test
    fun visibleDayFollowsTheMessageAfterASeparator() {
        val today = LocalDate.of(2026, 11, 7)
        val yesterday = today.minusDays(1)
        val rows = listOf(
            DateSeparatorRow("day", "昨天"),
            MessageRow("a"),
            UnreadSeparatorRow("unread-b", 1, "b"),
            MessageRow("b"),
        )
        val days = mapOf("a" to yesterday, "b" to today)
        assertEquals(yesterday, visibleMessageDay(rows, 0, days))
        assertEquals(today, visibleMessageDay(rows, 2, days))
        assertTrue(separatorBetween(rows, "a", "b"))
        assertFalse(separatorBetween(listOf(MessageRow("a"), MessageRow("b")), "a", "b"))
    }
}