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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.ui.util.MessageRow
import com.luckyagent.android.ui.util.ScrollAnchor
import com.luckyagent.android.ui.util.TimelineRow
import com.luckyagent.android.ui.util.paintedChatRows
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScrollRestoreInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingRowsWaitForHistoryAndRestoreTheSavedPaintedKeyExactlyOnce() {
        val saved = ScrollAnchor(messageId = "timeline:h-42", index = 3, offset = 17, followLatest = false)
        val fixture = RestoreFixture(saved)
        showList(fixture)

        compose.runOnIdle {
            assertEquals(0, fixture.restores)
            assertFalse(fixture.restored)
            assertEquals(saved, fixture.anchor)
            assertEquals(0, fixture.listState.firstVisibleItemIndex)
            fixture.rows = fullHistoryRows()
            fixture.historyLoading = false
            fixture.historyReady = true
        }
        compose.waitUntil(5_000) { fixture.restored }
        compose.runOnIdle {
            val expectedIndex = fixture.rows.indexOfFirst { it.key == saved.messageId }
            assertEquals(expectedIndex, fixture.listState.firstVisibleItemIndex)
            assertEquals(saved.offset, fixture.listState.firstVisibleItemScrollOffset)
            assertEquals(saved.messageId, fixture.listState.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(1, fixture.restores)
            // Another painted row changes the effect keys but must not replay restoration.
            fixture.rows = fixture.rows + MessageRow("timeline:later-message")
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, fixture.restores) }
    }

    @Test fun defaultFollowModeBecomesReadyOnlyAfterHistoryLoadsAndThenFollowsTheTail() {
        val fixture = RestoreFixture(anchor = null)
        showList(fixture)

        compose.runOnIdle {
            assertEquals(0, fixture.restores)
            assertFalse(fixture.restored)
            fixture.rows = fullHistoryRows()
            fixture.historyLoading = false
            fixture.historyReady = true
        }
        compose.waitUntil(5_000) { fixture.restored && !fixture.listState.canScrollForward }
        compose.runOnIdle {
            assertEquals(1, fixture.restores)
            assertEquals(fixture.rows.size + 1, fixture.listState.layoutInfo.totalItemsCount)
        }
        compose.onNodeWithTag("restore-tail").assertIsDisplayed()
    }

    @Test fun aFailedInitialLoadWithPendingRowsDoesNotConsumeTheHistoryAnchor() {
        val saved = ScrollAnchor(messageId = "timeline:h-42", index = 3, offset = 17, followLatest = false)
        val fixture = RestoreFixture(saved).apply { historyLoading = false }
        showList(fixture)

        compose.runOnIdle {
            assertFalse(fixture.historyReady)
            assertFalse(fixture.restored)
            assertEquals(0, fixture.restores)
            assertEquals(saved, fixture.anchor)
            // A retry that obtains history can now restore the untouched anchor.
            fixture.rows = fullHistoryRows()
            fixture.historyReady = true
        }
        compose.waitUntil(5_000) { fixture.restored }
        compose.runOnIdle {
            assertEquals(saved.messageId, fixture.listState.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(saved.offset, fixture.listState.firstVisibleItemScrollOffset)
            assertEquals(1, fixture.restores)
        }
    }

    @Test fun aSuccessfulEmptyHistoryCanBecomeReadyWithOnlyAPendingMessage() {
        val fixture = RestoreFixture(anchor = null).apply { historyLoading = false }
        showList(fixture)

        compose.runOnIdle {
            assertFalse(fixture.restored)
            assertEquals(0, fixture.restores)
            // The server confirmed an empty history; the optimistic row remains.
            fixture.historyReady = true
        }
        compose.waitUntil(5_000) { fixture.restored }
        compose.runOnIdle {
            assertEquals(listOf(MessageRow("timeline:pending-message")), fixture.rows)
            assertEquals(1, fixture.restores)
        }
        compose.onNodeWithTag("restore-tail").assertIsDisplayed()
    }

    private fun showList(fixture: RestoreFixture) {
        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                fixture.listState = listState
                val expectedItemCount = fixture.rows.size + 1
                RestoreChatScrollEffect(
                    listState = listState,
                    sessionId = "session-with-pending-message",
                    rows = fixture.rows,
                    expectedItemCount = expectedItemCount,
                    historyLoading = fixture.historyLoading,
                    historyReady = fixture.historyReady,
                    hasMessages = fixture.rows.isNotEmpty(),
                    anchor = fixture.anchor,
                    followLatest = fixture.followLatest,
                    restored = fixture.restored,
                    onRestored = {
                        fixture.restores += 1
                        fixture.restored = true
                    },
                )
                FollowLatestEffect(listState, "session-with-pending-message", expectedItemCount,
                    fixture.followLatest && fixture.restored)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    state = listState,
                ) {
                    items(fixture.rows, key = { it.key }) { row ->
                        Text(row.key, Modifier.fillMaxWidth().height(60.dp))
                    }
                    item(key = "chat-tail") {
                        Spacer(Modifier.fillMaxWidth().height(2.dp).testTag("restore-tail"))
                    }
                }
            }
        }
    }

    private fun fullHistoryRows(): List<TimelineRow> = paintedChatRows(
        messageIds = (0 until 80).map { "timeline:h-$it" },
        days = (0 until 80).map { LocalDate.of(2026, 11, 1).plusDays((it / 10).toLong()) },
        readMessageId = null,
    )

    private class RestoreFixture(val anchor: ScrollAnchor?) {
        var rows by mutableStateOf<List<TimelineRow>>(listOf(MessageRow("timeline:pending-message")))
        var historyLoading by mutableStateOf(true)
        var historyReady by mutableStateOf(false)
        var followLatest by mutableStateOf(anchor?.followLatest != false)
        var restored by mutableStateOf(false)
        var restores = 0
        lateinit var listState: LazyListState
    }
}
