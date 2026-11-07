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
}