package com.luckyagent.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.ui.util.MessageRow
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatFloatingDateInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun crossingRowsAndDatesDoesNotRecomposeTheParentChat() {
        val fixture = TimelineFixture()
        compose.setContent {
            MaterialTheme { TimelineWithFloatingDate(fixture) }
        }
        compose.waitForIdle()
        val initialCompositions = fixture.parentCompositions.get()

        // Every two rows have a different date. These jumps cross many row
        // boundaries without publishing the scroll position to the parent.
        for (index in listOf(8, 24, 48, 96, 128)) {
            compose.onNodeWithTag("dated-chat-list").performScrollToIndex(index)
            compose.runOnIdle {
                assertEquals(index, fixture.listState.firstVisibleItemIndex)
                assertEquals(initialCompositions, fixture.parentCompositions.get())
            }
        }

        // Exercise the real drag interactions too: they show, update and hide
        // the date overlay, and must still leave the parent composition alone.
        compose.onNodeWithTag("dated-chat-list").performScrollToIndex(24)
        compose.onNodeWithTag("dated-chat-list").performTouchInput {
            swipeUp(durationMillis = 600)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(fixture.listState.firstVisibleItemIndex > 24)
            assertEquals(initialCompositions, fixture.parentCompositions.get())
        }
    }

    @Composable
    private fun TimelineWithFloatingDate(fixture: TimelineFixture) {
        val listState = rememberLazyListState()
        val rows = remember { List(160) { MessageRow("message-$it") } }
        val dates = remember(rows) {
            rows.mapIndexed { index, row ->
                row.key to LocalDate.of(2026, 1, 1).plusDays((index / 2).toLong())
            }.toMap()
        }
        SideEffect {
            fixture.listState = listState
            fixture.parentCompositions.incrementAndGet()
        }
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("dated-chat-list"),
            ) {
                items(rows, key = { it.key }) { row ->
                    Text(row.key, Modifier.fillMaxWidth().height(60.dp))
                }
            }
            ChatFloatingDate(listState, rows, dates, Modifier.align(Alignment.TopCenter))
        }
    }

    private class TimelineFixture {
        val parentCompositions = AtomicInteger()
        lateinit var listState: LazyListState
    }
}
