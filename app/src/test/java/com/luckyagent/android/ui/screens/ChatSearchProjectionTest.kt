package com.luckyagent.android.ui.screens

import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.util.SessionSearchTab
import com.luckyagent.android.ui.util.sessionSearchHits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchProjectionTest {
    @Test fun duplicateMessageIdsProduceTwoIndependentlyAddressableSearchHits() {
        val timeline = buildTimeline(
            listOf(
                ChatBubble("same", "user", "needle first"),
                ChatBubble("same", "user", "needle second"),
            ),
        )

        val hits = sessionSearchHits(searchableTimelineRows(timeline), "needle", SessionSearchTab.Messages)

        assertEquals(2, hits.size)
        assertEquals(2, hits.map { it.rowKey }.distinct().size)
        assertEquals(timeline.map { it.key }, hits.map { it.rowKey })
        assertTrue(hits.all { it.rowKey.startsWith("timeline:") })
    }

    @Test fun aNonFirstToolStepTargetsTheContainingProcessRow() {
        val timeline = buildTimeline(
            listOf(
                ChatBubble("question", "user", "read the files"),
                ChatBubble("first-tool", "tool", "first file", toolName = "file_read", toolDone = true),
                ChatBubble("second-tool", "tool", "needle in the second file", toolName = "file_read", toolDone = true),
                ChatBubble("answer", "assistant", "finished"),
            ),
        )
        val process = timeline.filterIsInstance<ChatTimelineItem.Process>().single()

        val hits = sessionSearchHits(searchableTimelineRows(timeline), "needle", SessionSearchTab.Messages)

        assertEquals(2, process.steps.size)
        assertEquals(listOf(process.key), hits.map { it.rowKey })
    }

    @Test fun liveToolArgumentsAndOutputRemainSearchableBeyondTheirVisibleSummaries() {
        val timeline = buildTimeline(
            listOf(
                ChatBubble("question", "user", "inspect the files"),
                ChatBubble("first-tool", "tool", "first file", toolName = "file_read", toolDone = true),
                ChatBubble(
                    "live-tool", "tool", "file_read · running",
                    toolName = "file_read",
                    toolArgs = "a".repeat(32_001) + "arguments-only-needle",
                    toolOutput = "o".repeat(32_001) + "output-only-needle",
                ),
            ),
        )
        val process = timeline.filterIsInstance<ChatTimelineItem.Process>().single()
        val rows = searchableTimelineRows(timeline)

        for (needle in listOf("arguments-only-needle", "output-only-needle")) {
            assertEquals(
                listOf(process.key),
                sessionSearchHits(rows, needle, SessionSearchTab.Messages).map { it.rowKey },
            )
        }
    }

    @Test fun exposedReasoningIsCountedAtItsVisibleMessageRatherThanTwice() {
        val earlier = ChatBubble(
            "same-reasoning-id", "reasoning", "earlier-only marker",
            reasoningHasContent = true, reasoningRound = 1,
        )
        val latest = ChatBubble(
            "same-reasoning-id", "reasoning", "latest-only needle",
            reasoningHasContent = true, reasoningRound = 2,
        )
        val timeline = buildTimeline(
            listOf(
                ChatBubble("question", "user", "keep working"),
                earlier,
                ChatBubble("tool", "tool", "file read", toolName = "file_read", toolDone = true),
                latest,
            ),
        )
        val process = timeline.filterIsInstance<ChatTimelineItem.Process>().single()
        val exposed = timeline.filterIsInstance<ChatTimelineItem.Message>().single { it.bubble === latest }
        val rows = searchableTimelineRows(timeline)

        assertEquals(
            listOf(exposed.key),
            sessionSearchHits(rows, "latest-only", SessionSearchTab.Messages).map { it.rowKey },
        )
        // An older object with the same server id must remain searchable in the fold.
        assertEquals(
            listOf(process.key),
            sessionSearchHits(rows, "earlier-only", SessionSearchTab.Messages).map { it.rowKey },
        )
    }
}
