package com.luckyagent.android.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.util.SearchableMessage
import com.luckyagent.android.ui.util.SessionSearchHit
import com.luckyagent.android.ui.util.SessionSearchTab
import com.luckyagent.android.ui.util.TimelineRow
import com.luckyagent.android.ui.util.sessionSearchHits
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/** Search the same rows that LazyColumn paints, including folded process rows. */
internal fun searchableTimelineRows(timeline: List<ChatTimelineItem>): List<SearchableMessage> {
    val exposed = Collections.newSetFromMap(IdentityHashMap<ChatBubble, Boolean>())
    timeline.forEach { if (it is ChatTimelineItem.Message) exposed.add(it.bubble) }
    return timeline.mapNotNull { item ->
        val bubbles = when (item) {
            is ChatTimelineItem.Message -> listOf(item.bubble)
            is ChatTimelineItem.Process -> item.steps.filterNot { it in exposed }
        }
        if (bubbles.isEmpty()) return@mapNotNull null
        val texts = buildList {
            bubbles.forEach { bubble ->
                val body = bubble.searchText ?: bubble.content
                add(body)
                val args = bubble.toolArgs
                if (args != null && args !== body) add(args)
                val output = bubble.toolOutput
                if (output != null && output !== body && output !== args) add(output)
            }
        }
        SearchableMessage(
            id = item.key,
            text = texts.first(),
            additionalTexts = texts.drop(1),
            fileNames = bubbles.flatMap { bubble -> bubble.attachments.mapNotNull { it.descriptor.fileName } },
        )
    }
}

private class TimelineReference(val timeline: List<ChatTimelineItem>) {
    override fun equals(other: Any?): Boolean = other is TimelineReference && other.timeline === timeline
    override fun hashCode(): Int = System.identityHashCode(timeline)
}

private data class MatchRequest(
    val identity: Any,
    val timeline: TimelineReference,
    val query: String,
    val tab: SessionSearchTab,
)

private data class StampedMatches(val request: MatchRequest, val hits: List<SessionSearchHit>)

/** Null means matching this exact request is pending; old hits are never reused. */
@Composable
internal fun rememberTimelineSearchMatches(
    identity: Any,
    timeline: List<ChatTimelineItem>,
    query: String,
    tab: SessionSearchTab,
    enabled: Boolean,
): List<SessionSearchHit>? {
    val needle = query.trim()
    if (!enabled || needle.isEmpty()) return emptyList()
    val queryRequest = identity to needle
    val debouncedQuery by produceState<Pair<Any, String>?>(initialValue = null, key1 = queryRequest) {
        delay(150)
        value = queryRequest
    }
    val queryReady = debouncedQuery == queryRequest
    val request = MatchRequest(identity, TimelineReference(timeline), needle, tab)
    val matches by produceState<StampedMatches?>(initialValue = null, key1 = request, key2 = queryReady) {
        if (!queryReady) return@produceState
        val hits = withContext(Dispatchers.Default) {
            val rows = searchableTimelineRows(timeline)
            currentCoroutineContext().ensureActive()
            sessionSearchHits(rows, needle, tab)
        }
        value = StampedMatches(request, hits)
    }
    return if (queryReady) matches?.takeIf { it.request == request }?.hits else null
}

internal enum class SearchHistoryStatus { Idle, Loading, Complete, Incomplete }

/** One search owns a sequential history walk; failure requires an explicit retry. */
@Composable
internal fun rememberSearchHistoryStatus(
    identity: Any,
    query: String,
    enabled: Boolean,
    retry: Int = 0,
    loadNext: suspend () -> Result<Boolean>,
): SearchHistoryStatus {
    val needle = query.trim()
    val active = enabled && needle.isNotEmpty()
    val status = remember(identity, needle, active, retry) {
        mutableStateOf(if (active) SearchHistoryStatus.Loading else SearchHistoryStatus.Idle)
    }
    val latestLoadNext by rememberUpdatedState(loadNext)
    LaunchedEffect(identity, needle, active, retry) {
        if (!active) return@LaunchedEffect
        delay(200)
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = try {
                latestLoadNext()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            currentCoroutineContext().ensureActive()
            if (page.isFailure) {
                status.value = SearchHistoryStatus.Incomplete
                break
            }
            if (!page.getOrThrow()) {
                status.value = SearchHistoryStatus.Complete
                break
            }
            yield()
        }
    }
    return status.value
}

/** Match navigation uses the painted key, which may identify a folded process. */
@Composable
internal fun SearchHitNavigationEffect(
    listState: LazyListState,
    rows: List<TimelineRow>,
    activeRowKey: String?,
    enabled: Boolean,
    expectedItemCount: Int = rows.size,
) {
    LaunchedEffect(listState, rows, activeRowKey, enabled, expectedItemCount) {
        if (!enabled || activeRowKey == null) return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == activeRowKey }
        if (index < 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it == expectedItemCount }
        listState.animateScrollToItem(index)
    }
}
