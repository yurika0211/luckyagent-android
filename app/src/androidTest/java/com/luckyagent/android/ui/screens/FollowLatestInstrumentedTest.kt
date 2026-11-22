package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FollowLatestInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialLayoutFollowsTheTailAfterTheApprovalErrorRow() {
        val timeline = TimelineFixture().apply { approvalError = true }
        showTimeline(timeline)

        awaitTail(timeline)
        compose.runOnIdle { assertEquals(32, timeline.listState.layoutInfo.totalItemsCount) }
        compose.onNodeWithText("审批加载失败").assertIsDisplayed()
    }

    @Test fun growingTheLastMessageKeepsTheTailVisibleWithoutAddingRows() {
        val timeline = TimelineFixture()
        showTimeline(timeline)
        awaitTail(timeline)
        compose.runOnIdle { timeline.lastMessageHeight = 700.dp }

        awaitTail(timeline)
        compose.runOnIdle { assertEquals(31, timeline.listState.layoutInfo.totalItemsCount) }
    }

    @Test fun appendingMessagesKeepsFollowingTheSameTailKey() {
        val timeline = TimelineFixture()
        showTimeline(timeline)
        awaitTail(timeline)
        compose.runOnIdle { timeline.messageCount += 6 }

        awaitTail(timeline)
        compose.runOnIdle { assertEquals(37, timeline.listState.layoutInfo.totalItemsCount) }
        compose.onNodeWithText("message-35").assertIsDisplayed()
    }

    @Test fun historyModeKeepsTheReadersPositionWhenMessagesAppendAndGrow() {
        val timeline = TimelineFixture()
        showTimeline(timeline)
        awaitTail(timeline)
        compose.runOnIdle { timeline.followLatest = false }
        compose.onNodeWithTag("chat-list").performScrollToIndex(0)
        compose.runOnIdle {
            timeline.messageCount += 3
            timeline.lastMessageHeight = 700.dp
        }
        compose.waitForIdle()

        compose.runOnIdle { assertEquals(0, timeline.listState.firstVisibleItemIndex) }
        compose.onNodeWithText("message-0").assertIsDisplayed()
    }

    @Test fun switchingToALatestSessionResumesFollowingAfterAHistorySession() {
        val timeline = TimelineFixture()
        showTimeline(timeline)
        awaitTail(timeline)
        compose.runOnIdle { timeline.followLatest = false }
        compose.onNodeWithTag("chat-list").performScrollToIndex(0)
        compose.runOnIdle {
            timeline.sessionId = "latest-session"
            timeline.messageCount = 40
            timeline.followLatest = true
        }

        awaitTail(timeline)
        compose.onNodeWithText("message-39").assertIsDisplayed()
    }

    @Test fun aRealUpwardHistoryGestureStopsFollowingUntilTheReaderReturnsToTheTail() {
        val timeline = TimelineFixture()
        showTimeline(timeline)
        awaitTail(timeline)
        compose.onNodeWithTag("chat-list").performTouchInput { swipeDown(durationMillis = 600) }
        compose.waitForIdle()
        var anchor = 0
        compose.runOnIdle {
            assertFalse(timeline.followLatest)
            assertTrue(timeline.listState.canScrollForward)
            anchor = timeline.listState.firstVisibleItemIndex
            timeline.messageCount += 3
            timeline.lastMessageHeight = 700.dp
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(anchor, timeline.listState.firstVisibleItemIndex) }
        // Place the history reader close to the tail, then use a real downward-reading gesture.
        compose.onNodeWithTag("chat-list").performScrollToIndex(timeline.messageCount - 1)
        repeat(5) {
            if (timeline.listState.canScrollForward) {
                compose.onNodeWithTag("chat-list").performTouchInput { swipeUp(durationMillis = 600) }
            }
        }
        compose.waitUntil(5_000) { timeline.followLatest && !timeline.listState.canScrollForward }
        compose.runOnIdle { timeline.lastMessageHeight = 900.dp }
        awaitTail(timeline)
    }

    @Test fun aSingleOversizedReplyAndAShrinkingViewportStillExposeTheTail() {
        val timeline = TimelineFixture().apply {
            messageCount = 1
            lastMessageHeight = 700.dp
        }
        showTimeline(timeline)
        awaitTail(timeline)
        compose.runOnIdle { timeline.viewportHeight = 120.dp }
        awaitTail(timeline)
        compose.runOnIdle { timeline.approvalError = true }
        awaitTail(timeline)
        compose.onNodeWithText("审批加载失败").assertIsDisplayed()
    }

    private fun showTimeline(timeline: TimelineFixture) {
        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                timeline.listState = listState
                val expectedItemCount = timeline.messageCount + 1 + if (timeline.approvalError) 1 else 0
                UserChatScrollEffect(listState) { timeline.followLatest = it }
                FollowLatestEffect(listState, timeline.sessionId, expectedItemCount, timeline.followLatest)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(timeline.viewportHeight).testTag("chat-list"),
                    state = listState,
                ) {
                    items((0 until timeline.messageCount).toList(), key = { "message-$it" }) { index ->
                        Text(
                            "message-$index",
                            modifier = Modifier.fillMaxWidth().height(
                                if (index == timeline.messageCount - 1) timeline.lastMessageHeight else 60.dp,
                            ),
                        )
                    }
                    if (timeline.approvalError) {
                        item(key = "approvals-error") { Text("审批加载失败", Modifier.height(48.dp)) }
                    }
                    item(key = "chat-tail") {
                        Spacer(Modifier.fillMaxWidth().height(2.dp).testTag("chat-tail"))
                    }
                }
            }
        }
    }

    private fun awaitTail(timeline: TimelineFixture) {
        compose.waitUntil(5_000) {
            val state = timeline.listState
            state.layoutInfo.totalItemsCount == timeline.messageCount + 1 + (if (timeline.approvalError) 1 else 0) &&
                !state.isScrollInProgress && !state.canScrollForward
        }
        compose.onNodeWithTag("chat-tail").assertIsDisplayed()
    }

    private class TimelineFixture {
        var sessionId by mutableStateOf("first-session")
        var messageCount by mutableStateOf(30)
        var followLatest by mutableStateOf(true)
        var approvalError by mutableStateOf(false)
        var lastMessageHeight by mutableStateOf<Dp>(60.dp)
        var viewportHeight by mutableStateOf<Dp>(240.dp)
        lateinit var listState: LazyListState
    }
}
