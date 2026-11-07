package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSearchTest {
    @Test
    fun tabsFilterMessagesMediaAndLinks() {
        val messages = listOf(
            SearchableMessage("m1", "see https://example.com/a", fileNames = listOf("photo.png"), links = listOf("https://example.com/a")),
            SearchableMessage("m2", "plain note"),
        )
        assertEquals(listOf("m1"), sessionSearchHits(messages, "example", SessionSearchTab.Messages).map { it.messageId })
        assertEquals(listOf("m1"), sessionSearchHits(messages, "photo", SessionSearchTab.Media).map { it.messageId })
        assertEquals(emptyList<String>(), sessionSearchHits(messages, "missing", SessionSearchTab.Links).map { it.messageId })
        assertEquals(listOf("https://example.com/a"), searchableLinks("see https://example.com/a"))
    }
}