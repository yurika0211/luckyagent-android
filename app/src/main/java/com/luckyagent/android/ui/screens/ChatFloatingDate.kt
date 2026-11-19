package com.luckyagent.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.util.TimelineRow
import com.luckyagent.android.ui.util.floatingDateLabel
import com.luckyagent.android.ui.util.visibleMessageDay
import java.time.LocalDate
import kotlinx.coroutines.delay

/** Scroll observations stay here so crossing a row does not recompose the whole chat. */
@Composable
internal fun ChatFloatingDate(
    listState: LazyListState,
    rows: List<TimelineRow>,
    dayByMessageId: Map<String, LocalDate?>,
    modifier: Modifier = Modifier,
) {
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var visible by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress, dragging) {
        if (dragging) visible = true
        if (!listState.isScrollInProgress) {
            delay(500)
            visible = false
        }
    }
    val label by remember(listState, rows, dayByMessageId) {
        derivedStateOf {
            floatingDateLabel(visibleMessageDay(rows, listState.firstVisibleItemIndex, dayByMessageId))
        }
    }
    if (visible && label.isNotEmpty()) {
        Text(
            label,
            color = CloverText2,
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier.padding(top = 8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CloverSurface.copy(alpha = .94f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
