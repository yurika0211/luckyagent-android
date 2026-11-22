package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.util.DateSeparatorRow
import com.luckyagent.android.ui.util.MessageRow
import com.luckyagent.android.ui.util.SessionSearchTab
import com.luckyagent.android.ui.util.TimelineRow
import com.luckyagent.android.ui.util.UnreadSeparatorRow
import com.luckyagent.android.ui.util.paintedChatRows
import com.luckyagent.android.ui.util.sessionSearchHits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ChatSearchNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ordinarySearchHitUsesItsPrefixedPaintedKeyAndHighlightsTheVisibleBubble() {
        val target = ChatBubble("ordinary-target", "assistant", "needle in the visible answer")
        val fixture = showTimeline(prefix() + target + suffix(), footer = true)

        assertEquals("timeline:ordinary-target", fixture.targetKey)
        awaitTarget(fixture)

        compose.onNodeWithText(target.content).assertIsDisplayed()
        assertSelectedTarget(fixture)
    }

    @Test fun aHitInsideTheSecondToolScrollsToAndHighlightsItsCollapsedProcessRow() {
        val first = ChatBubble("first-tool", "tool", "first file", toolName = "file_read", toolDone = true)
        val second = ChatBubble(
            "second-tool", "tool", "needle in the second tool's output",
            toolName = "file_read", toolDone = true,
        )
        val fixture = showTimeline(prefix() + listOf(first, second) + suffix())
        val fold = fixture.timeline.filterIsInstance<ChatTimelineItem.Process>().single()

        assertEquals(fold.key, fixture.targetKey)
        assertEquals(listOf(first, second), fold.steps)
        awaitTarget(fixture)

        compose.onNodeWithText("思考了 2 步").assertIsDisplayed()
        compose.onNodeWithText(second.content).assertDoesNotExist()
        assertSelectedTarget(fixture)
    }

    private fun showTimeline(bubbles: List<ChatBubble>, footer: Boolean = false): NavigationFixture {
        val timeline = buildTimeline(bubbles)
        val hit = sessionSearchHits(searchableTimelineRows(timeline), "needle", SessionSearchTab.Messages).single()
        val rows = paintedChatRows(
            timeline.map { it.key },
            timeline.mapIndexed { index, _ -> LocalDate.of(2026, 11, 1).plusDays((index / 8).toLong()) },
            timeline[3].key,
        )
        val fixture = NavigationFixture(timeline, rows, hit.rowKey)
        // Keep the process indicator's perpetual animation outside automatic idle advancement.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                fixture.listState = listState
                SearchHitNavigationEffect(
                    listState, rows, hit.rowKey, enabled = true,
                    expectedItemCount = rows.size + if (footer) 1 else 0,
                )
                LazyColumn(Modifier.fillMaxWidth().height(240.dp), state = listState) {
                    items(rows, key = { it.key }) { row ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(row.key)) {
                            when (row) {
                                is DateSeparatorRow -> Text(row.label)
                                is UnreadSeparatorRow -> Text("${row.count} 条未读")
                                is MessageRow -> when (val item = timeline.single { it.key == row.key }) {
                                    is ChatTimelineItem.Message -> BubbleRow(
                                        bubble = item.bubble,
                                        avatarFile = "",
                                        bubbleOpacity = 100,
                                        imageHeaders = emptyMap(),
                                        imageBaseUrl = "",
                                        onDownload = {},
                                        onQuote = {},
                                        onCopy = {},
                                        highlighted = item.key == hit.rowKey,
                                    )
                                    is ChatTimelineItem.Process -> ProcessTimeline(
                                        steps = item.steps,
                                        isResponding = false,
                                        imageHeaders = emptyMap(),
                                        imageBaseUrl = "",
                                        onDownload = {},
                                        onQuote = {},
                                        onCopy = {},
                                        onOpenThinking = {},
                                        highlighted = item.key == hit.rowKey,
                                    )
                                }
                            }
                        }
                    }
                    if (footer) item(key = "chat-tail") { Box(Modifier.height(1.dp)) }
                }
            }
        }
        return fixture
    }

    private fun awaitTarget(fixture: NavigationFixture) {
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            fixture.listState.layoutInfo.visibleItemsInfo.any { it.key == fixture.targetKey } &&
                !fixture.listState.isScrollInProgress
        }
        compose.onNodeWithTag(fixture.targetKey).assertIsDisplayed()
        assertTrue(fixture.listState.firstVisibleItemIndex > 0)
    }

    private fun assertSelectedTarget(fixture: NavigationFixture) {
        compose.onNode(
            isSelected() and hasAnyAncestor(hasTestTag(fixture.targetKey)),
            useUnmergedTree = true,
        ).assertIsDisplayed().assertIsSelected()
    }

    private fun prefix() = (0 until 28).map { ChatBubble("before-$it", "user", "earlier message $it") }

    private fun suffix() = (0 until 6).map { ChatBubble("after-$it", "user", "later message $it") }

    private class NavigationFixture(
        val timeline: List<ChatTimelineItem>,
        val rows: List<TimelineRow>,
        val targetKey: String,
    ) {
        lateinit var listState: LazyListState
    }
}
