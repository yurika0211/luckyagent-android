package com.luckyagent.android.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.ui.util.SearchableMessage
import com.luckyagent.android.ui.util.SessionSearchTab
import com.luckyagent.android.ui.util.sessionSearchHits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class SearchHistoryInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val fixtures = mutableListOf<PagingFixture>()

    @After fun releasePendingResponses() {
        fixtures.flatMap { it.requests }.forEach { it.response.complete(Result.failure(IOException("test finished"))) }
    }

    @Test fun anExistingHitDoesNotPreventScanningTwoOlderPages() {
        val fixture = showSearch(appendSuccessfulPages = true)
        compose.onNodeWithTag("hit-count").assertTextEquals("1")
        awaitRequests(fixture, 1)

        fixture.requests[0].response.complete(Result.success(true))
        awaitRequests(fixture, 2)
        fixture.requests[1].response.complete(Result.success(false))

        awaitStatus(SearchHistoryStatus.Complete)
        compose.onNodeWithTag("hit-count").assertTextEquals("3")
        settle()
        assertEquals(2, fixture.requests.size)
    }

    @Test fun aFailedPageStopsRequestsAndExplicitRetryContinuesTheSearch() {
        val fixture = showSearch(appendSuccessfulPages = true)
        awaitRequests(fixture, 1)
        fixture.requests[0].response.complete(Result.failure(IOException("offline")))
        awaitStatus(SearchHistoryStatus.Incomplete)

        settle()
        assertEquals(1, fixture.requests.size)
        compose.onNodeWithTag("hit-count").assertTextEquals("1")
        compose.runOnIdle { fixture.retry++ }
        awaitRequests(fixture, 2)
        fixture.requests[1].response.complete(Result.success(false))

        awaitStatus(SearchHistoryStatus.Complete)
        compose.onNodeWithTag("hit-count").assertTextEquals("2")
        assertEquals(listOf("needle", "needle"), fixture.requests.map { it.query })
    }

    @Test fun closingSearchCancelsItsPendingPageAndDoesNotRequestAnother() {
        val fixture = showSearch()
        awaitRequests(fixture, 1)
        compose.runOnIdle { fixture.enabled = false }
        awaitStatus(SearchHistoryStatus.Idle)

        fixture.requests[0].response.complete(Result.success(true))
        settle()

        assertEquals(1, fixture.requests.size)
        compose.onNodeWithTag("search-status").assertTextEquals(SearchHistoryStatus.Idle.name)
    }

    @Test fun changingSessionsIgnoresTheOldPageAndKeepsTheNewSearchLoading() {
        replacementSearchIgnoresOldPage { it.identity = "session-b" }
    }

    @Test fun changingTheQueryIgnoresTheOldPageAndKeepsTheNewSearchLoading() {
        replacementSearchIgnoresOldPage { it.query = "replacement" }
    }

    private fun replacementSearchIgnoresOldPage(replace: (PagingFixture) -> Unit) {
        val fixture = showSearch()
        awaitRequests(fixture, 1)
        val oldRequest = fixture.requests[0]
        compose.runOnIdle { replace(fixture) }
        awaitRequests(fixture, 2)

        // A response can race cancellation; it must not start the old loop's next page.
        oldRequest.response.complete(Result.success(true))
        settle()
        assertEquals(2, fixture.requests.size)
        compose.onNodeWithTag("search-status").assertTextEquals(SearchHistoryStatus.Loading.name)
        assertEquals(fixture.identity, fixture.requests[1].identity)
        assertEquals(fixture.query, fixture.requests[1].query)

        fixture.requests[1].response.complete(Result.success(false))
        awaitStatus(SearchHistoryStatus.Complete)
        settle()
        assertEquals(2, fixture.requests.size)
    }

    private fun showSearch(appendSuccessfulPages: Boolean = false): PagingFixture {
        val fixture = PagingFixture(appendSuccessfulPages).also(fixtures::add)
        compose.setContent {
            MaterialTheme {
                val status = rememberSearchHistoryStatus(
                    identity = fixture.identity,
                    query = fixture.query,
                    enabled = fixture.enabled,
                    retry = fixture.retry,
                    loadNext = { fixture.loadNext() },
                )
                Column {
                    Text(status.name, Modifier.testTag("search-status"))
                    Text(
                        sessionSearchHits(fixture.messages, fixture.query, SessionSearchTab.Messages).size.toString(),
                        Modifier.testTag("hit-count"),
                    )
                }
            }
        }
        return fixture
    }

    private fun awaitRequests(fixture: PagingFixture, count: Int) {
        compose.waitUntil(5_000) { fixture.requests.size >= count }
        assertEquals(count, fixture.requests.size)
    }

    private fun awaitStatus(status: SearchHistoryStatus) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(status.name).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("search-status").assertTextEquals(status.name)
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private data class PageRequest(
        val identity: String,
        val query: String,
        val response: CompletableDeferred<Result<Boolean>> = CompletableDeferred(),
    )

    private class PagingFixture(private val appendSuccessfulPages: Boolean) {
        var identity by mutableStateOf("session-a")
        var query by mutableStateOf("needle")
        var enabled by mutableStateOf(true)
        var retry by mutableStateOf(0)
        var messages by mutableStateOf(listOf(SearchableMessage("recent", "needle in the latest page")))
        val requests = CopyOnWriteArrayList<PageRequest>()

        suspend fun loadNext(): Result<Boolean> {
            val request = PageRequest(identity, query)
            requests += request
            // Deliberately allow a late transport response after the search is cancelled.
            val result = withContext(NonCancellable) { request.response.await() }
            if (appendSuccessfulPages && result.isSuccess) {
                val id = "older-${requests.indexOf(request)}"
                messages = listOf(SearchableMessage(id, "needle in $id")) + messages
            }
            return result
        }
    }
}
