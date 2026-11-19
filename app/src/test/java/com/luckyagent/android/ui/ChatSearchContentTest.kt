package com.luckyagent.android.ui

import com.luckyagent.android.data.api.HistoryToolCall
import com.luckyagent.android.data.api.ProviderMessage
import com.luckyagent.android.ui.util.SearchableMessage
import com.luckyagent.android.ui.util.SessionSearchTab
import com.luckyagent.android.ui.util.searchableLinks
import com.luckyagent.android.ui.util.sessionSearchHits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchContentTest {
    @Test fun findsMessageAndLinkPastTheDisplayCapWithoutExpandingTheRenderedBody() {
        val full = "prefix ".repeat(6_000) + "TAIL_NEEDLE https://example.com/tail-reference"
        val bubble = ProviderMessage(role = "assistant", content = full).toHistoryBubbles(42).single()

        assertEquals(MAX_UI_MESSAGE_CHARS, bubble.content.length)
        assertFalse(bubble.content.contains("TAIL_NEEDLE"))
        assertSame(full, bubble.searchText)
        assertEquals(listOf(bubble.id), search(listOf(bubble), "TAIL_NEEDLE", SessionSearchTab.Messages))
        assertEquals(listOf(bubble.id), search(listOf(bubble), "tail-reference", SessionSearchTab.Links))
        assertEquals("h-42-${"assistant".hashCode()}", bubble.id)
        assertEquals(42, bubble.historyIndex)
    }

    @Test fun searchableBodiesKeepClientMessageAndReasoningIdentity() {
        val full = "x".repeat(MAX_UI_MESSAGE_CHARS + 1) + "USER_TAIL"
        val bubbles = projectHistoryBubbles(listOf(
            ProviderMessage(role = "user", content = full, clientMessageId = "request-42"),
            ProviderMessage(role = "assistant", content = "answer", reasoningContent = "reasoning needle"),
        ), startIndex = 42)

        assertEquals(3, bubbles.size)
        assertEquals(listOf("request-42"), search(bubbles, "USER_TAIL", SessionSearchTab.Messages))
        assertEquals(listOf("h-43-reasoning"), search(bubbles, "reasoning needle", SessionSearchTab.Messages))
        assertEquals(3, bubbles.map { it.id }.distinct().size)
    }

    @Test fun fullToolResultIsSearchedOnceUnderTheExistingCallIdentity() {
        val full = "o".repeat(MAX_UI_MESSAGE_CHARS + 1) + "TOOL_TAIL"
        val bubbles = projectHistoryBubbles(listOf(
            ProviderMessage(role = "assistant", toolCalls = listOf(
                HistoryToolCall(id = "call-42", name = "file_read", arguments = "{}"),
            )),
            ProviderMessage(role = "tool", toolCallId = "call-42", content = full),
        ), startIndex = 42)

        assertEquals(1, bubbles.size)
        assertEquals("h-42-tool-0", bubbles.single().id)
        assertSame(full, bubbles.single().toolOutput)
        assertEquals(listOf("h-42-tool-0"), search(bubbles, "TOOL_TAIL", SessionSearchTab.Messages))
    }

    @Test fun reprojectingShorterContentDoesNotRetainThePreviousSearchTail() {
        val long = ProviderMessage(role = "user", clientMessageId = "request-42", content = "x".repeat(40_000) + "OLD_TAIL")
        val before = long.toHistoryBubbles(42).single()
        val after = long.copy(content = "replacement body").toHistoryBubbles(42).single()

        assertEquals(before.id, after.id)
        assertEquals(listOf(before.id), search(listOf(before), "OLD_TAIL", SessionSearchTab.Messages))
        assertNull(after.searchText)
        assertTrue(search(listOf(after), "OLD_TAIL", SessionSearchTab.Messages).isEmpty())
        assertEquals(listOf(after.id), search(listOf(after), "replacement", SessionSearchTab.Messages))
    }

    @Test fun aCachedPreviewStaysExplicitlyIncompleteUntilTheFullBodyArrives() {
        val preview = ProviderMessage(role = "user", content = "preview\n...[truncated]", contentTruncated = true)
            .toHistoryBubbles(42).single()
        val complete = ProviderMessage(role = "user", content = "preview and COMPLETE_TAIL")
            .toHistoryBubbles(42).single()

        assertTrue(preview.contentTruncated)
        assertNull(preview.searchText)
        assertTrue(search(listOf(preview), "COMPLETE_TAIL", SessionSearchTab.Messages).isEmpty())
        assertEquals(preview.id, complete.id)
        assertFalse(complete.contentTruncated)
        assertEquals(listOf(complete.id), search(listOf(complete), "COMPLETE_TAIL", SessionSearchTab.Messages))
    }

    @Test fun paginationOnlyContinuesWhenTheOldestOrdinalMovesBack() {
        assertEquals(true, searchHistoryPageProgress(80, 40).getOrThrow())
        assertEquals(false, searchHistoryPageProgress(40, 0).getOrThrow())
        assertTrue(searchHistoryPageProgress(40, 40).isFailure)
        assertTrue(searchHistoryPageProgress(40, 80).isFailure)
        assertTrue(searchHistoryPageProgress(40, -1).isFailure)
    }

    private fun search(bubbles: List<ChatBubble>, query: String, tab: SessionSearchTab): List<String> =
        sessionSearchHits(bubbles.map { bubble ->
            val text = bubble.searchText ?: bubble.content
            SearchableMessage(id = bubble.id, text = text, links = searchableLinks(text))
        }, query, tab).map { it.rowKey }
}
