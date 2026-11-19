package com.luckyagent.android.ui.screens

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Keep the fixed tail visible after rows, their heights, or the viewport change. */
@Composable
internal fun FollowLatestEffect(
    listState: LazyListState,
    sessionId: String,
    expectedItemCount: Int,
    followLatest: Boolean,
) {
    LaunchedEffect(listState, sessionId, expectedItemCount, followLatest) {
        if (!followLatest || expectedItemCount <= 0) return@LaunchedEffect
        // Observe layout, rather than message text, so a WebView finishing its
        // measurement or a growing last bubble also keeps the tail on screen.
        snapshotFlow {
            Triple(listState.layoutInfo, listState.isScrollInProgress, listState.canScrollForward)
        }.collect { (layout, scrolling, canScrollForward) ->
            if (drawnRowsReady(layout.totalItemsCount, expectedItemCount) && !scrolling && canScrollForward) {
                listState.scrollToItem(followLatestScrollIndex(expectedItemCount))
            }
        }
    }
}

/** Only an actual list drag changes follow mode; tapping a message does not. */
@Composable
internal fun UserChatScrollEffect(
    listState: LazyListState,
    onFollowLatestChanged: (Boolean) -> Unit,
) {
    val latestOnFollowLatestChanged by rememberUpdatedState(onFollowLatestChanged)
    LaunchedEffect(listState) {
        var activeDrag: DragInteraction.Start? = null
        var afterDrag: Job? = null
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    afterDrag?.cancel()
                    activeDrag = interaction
                    latestOnFollowLatestChanged(false)
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val start = when (interaction) {
                        is DragInteraction.Stop -> interaction.start
                        is DragInteraction.Cancel -> interaction.start
                        else -> null
                    }
                    if (activeDrag == start && activeDrag != null) {
                        activeDrag = null
                        afterDrag?.cancel()
                        afterDrag = launch {
                            // Drag.Stop can arrive before the fling starts.
                            // Give it a frame, then wait for all scrolling to
                            // finish before deciding whether the reader came back.
                            withFrameNanos { }
                            snapshotFlow { listState.isScrollInProgress }.first { !it }
                            if (!listState.canScrollForward) latestOnFollowLatestChanged(true)
                        }
                    }
                }
            }
        }
    }
}
