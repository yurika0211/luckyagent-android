package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSearchTest {
    @Test
    fun tabsFilterMessagesMediaAndLinks() {
        val messages = listOf(
            SearchableMessage("timeline:m1", "see https://example.com/a", fileNames = listOf("photo.png")),
            SearchableMessage("timeline:m2", "plain note"),
        )
        assertEquals(listOf("timeline:m1"), sessionSearchHits(messages, " EXAMPLE ", SessionSearchTab.Messages).map { it.rowKey })
        assertEquals(listOf("timeline:m1"), sessionSearchHits(messages, "PHOTO", SessionSearchTab.Media).map { it.rowKey })
        assertEquals(emptyList<String>(), sessionSearchHits(messages, "missing", SessionSearchTab.Links).map { it.rowKey })
        assertEquals(listOf("https://example.com/a"), searchableLinks("see https://example.com/a"))
        assertTrue(sessionSearchHits(messages, " ", SessionSearchTab.Messages).isEmpty())
    }

    @Test
    fun aFoldedRowSearchesEveryBodyAndReturnsTheRealRowKeyOnce() {
        val rows = listOf(SearchableMessage(
            id = "timeline:tool-first",
            text = "first step",
            additionalTexts = listOf("second needle", "third needle at https://example.com/older"),
        ))
        assertEquals(listOf("timeline:tool-first"), sessionSearchHits(rows, "needle", SessionSearchTab.Messages).map { it.rowKey })
        assertEquals(listOf("timeline:tool-first"), sessionSearchHits(rows, "older", SessionSearchTab.Links).map { it.rowKey })
    }

    @Test
    fun longBodyResultShowsTheMatchingContextInsteadOfTheFirstLine() {
        val body = "unrelated first line\n" + "a".repeat(40_000) + " UNIQUE-TAIL " + "z".repeat(100)
        val hit = sessionSearchHits(listOf(SearchableMessage("timeline:long", body)), "unique-tail", SessionSearchTab.Messages).single()
        assertEquals("timeline:long", hit.rowKey)
        assertTrue(hit.label.contains("UNIQUE-TAIL"))
        assertTrue(hit.label.startsWith("…"))
        assertTrue(hit.label.length <= 82)
    }

    @Test
    fun messagesSharingAnOriginalIdStillHaveIndependentPaintedRowHits() {
        val rows = listOf(
            SearchableMessage("timeline:reused", "first needle"),
            SearchableMessage("timeline:reused-1", "second needle"),
        )
        assertEquals(listOf("timeline:reused", "timeline:reused-1"), sessionSearchHits(rows, "needle", SessionSearchTab.Messages).map { it.rowKey })
        assertEquals(listOf("timeline:reused-1"), sessionSearchHits(rows, "second", SessionSearchTab.Messages).map { it.rowKey })
    }
}
