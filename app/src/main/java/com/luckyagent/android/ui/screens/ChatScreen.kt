@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.luckyagent.android.ui.screens

import android.content.Context
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import com.luckyagent.android.ui.MinMainContentWidth
import com.luckyagent.android.ui.AppNavRailWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.CloverMark
import com.luckyagent.android.ui.components.StartupPhase
import com.luckyagent.android.ui.components.StartupReconcileBar
import com.luckyagent.android.ui.components.StartupWait
import com.luckyagent.android.ui.components.StatusChip
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.luckyagent.android.data.cache.SessionCachePolicy
import com.luckyagent.android.data.api.RuntimeSession
import com.luckyagent.android.ui.isFixedRoleSession
import com.luckyagent.android.ui.sessionGroups
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.TaskNode
import com.luckyagent.android.data.api.isTerminalTaskStatus
import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.PendingApproval
import com.luckyagent.android.data.api.inputPrompt
import com.luckyagent.android.data.api.isSecureCredentialForm
import com.luckyagent.android.data.api.needsTextInput
import com.luckyagent.android.data.api.FunctionalModelKinds
import com.luckyagent.android.data.api.ModelRef
import com.luckyagent.android.data.api.modelKindLabel
import com.luckyagent.android.data.api.modelSheetSections
import com.luckyagent.android.data.api.ContextBucketUsage
import com.luckyagent.android.data.api.ContextInspectResponse
import com.luckyagent.android.data.api.ContextSection
import com.luckyagent.android.data.api.TokenUsage
import com.luckyagent.android.ui.AppUiState
import com.luckyagent.android.ui.AppDestination
import com.luckyagent.android.ui.AppViewModel
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.ReplacedBubbleList
import com.luckyagent.android.ui.OutboundQueueItem
import com.luckyagent.android.ui.OutboundQueueStatus
import com.luckyagent.android.ui.util.TokenFormat
import com.luckyagent.android.ui.ChatMedia
import com.luckyagent.android.ui.PendingMedia
import com.luckyagent.android.ui.components.CloverCard
import com.luckyagent.android.ui.components.MarkdownText
import com.luckyagent.android.ui.components.LocalOpenNavigationDrawer
import com.luckyagent.android.ui.theme.CloverAccent
import com.luckyagent.android.ui.theme.CloverBg
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverError
import com.luckyagent.android.ui.theme.CloverLeaf
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverSurface
import com.luckyagent.android.ui.theme.CloverSurface2
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2
import com.luckyagent.android.ui.theme.CloverText3
import com.luckyagent.android.ui.theme.CloverUserBubble
import com.luckyagent.android.ui.theme.CloverWarning
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.outlined.Mic
import androidx.core.content.ContextCompat
import com.luckyagent.android.data.media.CaptureMediaStore
import com.luckyagent.android.data.media.CaptureTarget
import com.luckyagent.android.data.media.VoiceRecorder
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import com.luckyagent.android.ui.util.MessageQuote
import com.luckyagent.android.ui.util.quotePreview
import com.luckyagent.android.ui.util.artifactDownloadUrl
import com.luckyagent.android.ui.components.HtmlPreviewDialog
import com.luckyagent.android.ui.components.isHtmlAttachment
import com.luckyagent.android.ui.util.quoteRoleLabel
import kotlin.math.roundToInt

internal sealed interface ChatTimelineItem {
    val key: String

    data class Message(val bubble: ChatBubble, override val key: String) : ChatTimelineItem

    data class Process(val steps: List<ChatBubble>, override val key: String) : ChatTimelineItem
}

/**
 * One line for the latest chat fold.
 * A running turn shows only the newest summary, and only on the fold that
 * belongs to that turn. A finished turn yields to the answer, except cancel
 * and failure, which replace the summary with an end state.
 */
internal fun outerProcessLabel(
    liveSummary: String?,
    processNotice: String?,
    isResponding: Boolean,
    isLatestProcess: Boolean,
    ownsCurrentTurn: Boolean = true,
): String? {
    if (!isLatestProcess || !ownsCurrentTurn) return null
    if (!isResponding) return processNotice?.trim()?.ifBlank { null }
    return liveSummary?.trim()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(160)
}

/** A status box opened for a new turn before any reasoning or tool text arrives. */
internal fun isTurnStatusPlaceholder(bubble: ChatBubble): Boolean =
    bubble.role == "reasoning" && !bubble.reasoningHasContent && bubble.content.isBlank()

/** A placeholder is hidden while the same fold already has a real step. */
internal fun visibleProcessSteps(steps: List<ChatBubble>): List<ChatBubble> =
    if (steps.any { !isTurnStatusPlaceholder(it) }) steps.filterNot(::isTurnStatusPlaceholder) else steps

/**
 * The fold that owns the open turn: it is the last fold, and a user message
 * opened it after the fold before it. A stored fold — even the only one on
 * screen — does not own a new turn, so the previous turn's box cannot show the
 * new turn's status.
 */
internal fun processOwnsTurn(steps: List<ChatBubble>, timeline: List<ChatTimelineItem>): Boolean {
    val index = timeline.indexOfFirst { item ->
        item is ChatTimelineItem.Process && item.steps === steps
    }.takeIf { it >= 0 } ?: timeline.indexOfFirst { item ->
        item is ChatTimelineItem.Process && item.steps.map { it.id } == steps.map { it.id }
    }
    if (index < 0) return false
    val after = timeline.subList(index + 1, timeline.size)
    if (after.any { it is ChatTimelineItem.Process }) return false
    if (after.any { item ->
            item is ChatTimelineItem.Message &&
                item.bubble.role.equals("assistant", ignoreCase = true) &&
                !item.bubble.isIntermediate
        }
    ) return false
    val previous = timeline.subList(0, index).indexOfLast { it is ChatTimelineItem.Process }
    return timeline.subList(previous + 1, index).any { item ->
        item is ChatTimelineItem.Message && item.bubble.role.equals("user", ignoreCase = true)
    }
}

/**
 * Bubbles that exist only in the current turn. History has not stored them yet,
 * so a page refresh must append them instead of dropping the in-progress fold.
 */
internal fun liveTurnBubbles(bubbles: List<ChatBubble>, turnId: String, bufferId: String?): List<ChatBubble> {
    if (turnId.isBlank() && bufferId.isNullOrBlank()) return emptyList()
    val start = liveTurnStart(bubbles, turnId, bufferId)
    if (start < 0) return emptyList()
    return bubbles.drop(start)
}

/**
 * Index of the in-memory turn that history has not stored yet.
 * The user bubble that opened the turn is included, so a refresh can put the
 * whole turn back after the server page.
 */
internal fun liveTurnStart(bubbles: List<ChatBubble>, turnId: String, bufferId: String?): Int {
    if (turnId.isBlank() && bufferId.isNullOrBlank()) return -1
    val marker = bubbles.indexOfLast { bubble ->
        bubble.id == bufferId ||
            bubble.streaming ||
            (turnId.isNotBlank() && (bubble.id.contains(turnId) || bubble.stepId?.contains(turnId) == true))
    }
    if (marker < 0) return -1
    val user = bubbles.subList(0, marker).indexOfLast { bubble ->
        bubble.role.equals("user", ignoreCase = true)
    }
    return if (user >= 0) user else marker
}

/**
 * Session ordinals a live turn will get once the runtime stores it.
 *
 * History stamps every bubble of one server message with that message's index.
 * A live tool bubble holds both the call and its output, while history stores
 * the call on the assistant message and the output as the next tool message.
 * The combined bubble keeps the call's ordinal and reserves the following
 * result ordinal. Commentary before the result stays on the call. The answer
 * uses the ordinal past the result. A status placeholder shares the next
 * ordinal without consuming it, because history never stores that box.
 */
internal fun liveTurnOrdinals(turn: List<ChatBubble>, userOrdinal: Int): List<Int> {
    val ordinals = MutableList(turn.size) { userOrdinal }
    var next = userOrdinal
    var groupOpen = false
    var reservedResult: Int? = null
    turn.forEachIndexed { index, bubble ->
        val role = bubble.role.lowercase()
        when {
            role == "user" -> {
                ordinals[index] = userOrdinal
                next = userOrdinal + 1
                groupOpen = false
                reservedResult = null
            }
            isTurnStatusPlaceholder(bubble) -> ordinals[index] = next
            role == "tool" && bubble.toolArgs != null && bubble.toolOutput != null -> {
                if (!groupOpen) groupOpen = true
                ordinals[index] = next
                reservedResult = next + 1
            }
            role == "tool" && bubble.toolOutput != null && bubble.toolArgs == null -> {
                val result = reservedResult
                if (result != null) {
                    ordinals[index] = result
                    next = result + 1
                    reservedResult = null
                } else {
                    if (groupOpen) next += 1
                    ordinals[index] = next
                    next += 1
                }
                groupOpen = false
            }
            role == "reasoning" || role == "tool" || (role == "assistant" && bubble.isIntermediate) -> {
                if (!groupOpen) groupOpen = true
                ordinals[index] = next
            }
            else -> {
                val result = reservedResult
                next = when {
                    result != null -> result + 1
                    groupOpen -> next + 1
                    else -> next
                }
                ordinals[index] = next
                next += 1
                groupOpen = false
                reservedResult = null
            }
        }
    }
    return ordinals
}

/**
 * Replace live rows the server already stored.
 *
 * Identity is the session-wide oldest-first index, not a page offset and not
 * full-text equality. [turnOrdinal] is the index of the stored user message that
 * opened this turn; without it nothing can be proven, so the whole live turn
 * stays. A row is dropped only when history holds a bubble of the same role at
 * that index, so a grouping mismatch keeps a row instead of deleting content.
 * Rows past the stored tail stay, including a second send with the same text.
 */
internal fun dedupeLiveTurn(
    history: List<ChatBubble>,
    live: List<ChatBubble>,
    turnOrdinal: Int? = null,
): List<ChatBubble> {
    if (live.isEmpty()) return emptyList()
    if (turnOrdinal == null) return live
    val storedThrough = history.mapNotNull { it.serverOrdinal }.maxOrNull() ?: return live
    val storedByOrdinal = history.filter { it.serverOrdinal != null }.groupBy { it.serverOrdinal!! }
    val ordinals = liveTurnOrdinals(live, turnOrdinal)
    return live.filterIndexed { index, bubble ->
        val ordinal = ordinals[index]
        if (ordinal > storedThrough) return@filterIndexed true
        // The status box is the turn's own marker, not a stored row.
        if (isTurnStatusPlaceholder(bubble)) return@filterIndexed true
        val stored = storedByOrdinal[ordinal].orEmpty()
        if (bubble.role.equals("user", ignoreCase = true)) {
            return@filterIndexed stored.none { it.role.equals("user", ignoreCase = true) }
        }
        // A finished live tool spans the call ordinal and the following result.
        if (bubble.role.equals("tool", ignoreCase = true) && bubble.toolArgs != null && bubble.toolOutput != null) {
            val resultStored = storedByOrdinal[ordinal + 1].orEmpty().any { it.role.equals("tool", ignoreCase = true) }
            if (!resultStored) return@filterIndexed true
        }
        stored.none { it.role.equals(bubble.role, ignoreCase = true) }
    }
}

/**
 * Where to land when a session opens.
 * A saved anchor of 0 is the list default before the first real scroll, so an
 * oldest-first list uses the latest row instead. A later anchor is restored.
 */
internal fun openingScrollIndex(anchor: Pair<Int, Int>?, lastIndex: Int): Pair<Int, Int> {
    if (lastIndex < 0) return 0 to 0
    val saved = anchor?.takeIf { it.first > 0 } ?: return lastIndex to 0
    return saved.first.coerceIn(0, lastIndex) to saved.second
}

/** Follow the newest row when the viewport was already on it, even if only its content changed. */
/**
 * Whether a row that was live should stay live after the list settles.
 *
 * A row absent from [visibleKeys] has been composed by prefetch but is not on
 * screen, so its WebView should come down. An empty set means layout has not
 * run yet; tearing the surface down then would flash a placeholder on open.
 */
internal fun surfaceStaysLive(visibleKeys: List<Any>, key: Any, wasLive: Boolean): Boolean = when {
    visibleKeys.isEmpty() -> wasLive
    key in visibleKeys -> true
    else -> false
}

/**
 * Keeps a heavy surface up while its row is on screen, and takes it down once
 * the row has settled off screen. A fling does not mount or destroy anything:
 * the decision waits for [LazyListState.isScrollInProgress] to clear.
 */
@Composable
private fun bubbleSurfaceActive(listState: LazyListState, key: Any): Boolean {
    var live by remember(key) { mutableStateOf(true) }
    if (!listState.isScrollInProgress) {
        live = surfaceStaysLive(listState.layoutInfo.visibleItemsInfo.map { it.key }, key, live)
    }
    return live
}

internal fun shouldFollowLatest(atBottom: Boolean, stuckToLastAutoScroll: Boolean): Boolean =
    atBottom || stuckToLastAutoScroll

internal fun collabFoldLabel(tasks: List<TaskSummary>, responding: Boolean): String? {
    val collabModes = setOf("parallel", "pipeline", "debate", "auto", "autonomy_queue")
    val matched = tasks.filter { task ->
        task.mode.lowercase() in collabModes && (task.childCount > 0 || task.mode.lowercase() in setOf("parallel", "pipeline", "debate"))
    }
    val task = matched.firstOrNull { !it.status.isTerminalTaskStatus() } ?: matched.firstOrNull()
    if (task == null) {
        val delegates = tasks.filter { it.mode.equals("single", ignoreCase = true) && it.childCount == 0 }
        if (delegates.isEmpty()) return null
        val completed = delegates.count { it.status.equals("completed", ignoreCase = true) }
        val failed = delegates.count { it.status.lowercase() in setOf("failed", "timeout") }
        val suffix = when {
            failed > 0 -> " · $failed 失败"
            completed == delegates.size -> " · 完成"
            delegates.all { it.status.isTerminalTaskStatus() } -> " · 已结束"
            else -> ""
        }
        return "子代理 · $completed/${delegates.size}$suffix"
    }
    val done = task.status.isTerminalTaskStatus()
    val mode = task.mode.lowercase()
    val total = task.childCount.coerceAtLeast(1)
    val completed = task.completedChildren.coerceIn(0, total)
    val round = (task.progress * total).roundToInt().coerceIn(1, total)
    val body = when (mode) {
        "parallel" -> "并行 · $completed/$total"
        "pipeline" -> if (done) "流水线 · $completed/$total" else "流水线 · 第 ${completed.coerceAtLeast(1)} 步"
        "debate" -> if (done) "辩论 · $completed 轮" else "辩论 · 第 $round 轮"
        "autonomy_queue" -> "黑板 · $total 人"
        else -> "协作 · $completed/$total"
    }
    return if (done) "$body · 完成" else body
}

internal fun buildTimeline(bubbles: List<ChatBubble>): List<ChatTimelineItem> {
    val result = mutableListOf<ChatTimelineItem>()
    val steps = mutableListOf<ChatBubble>()
    val usedKeys = mutableSetOf<String>()
    fun uniqueKey(base: String): String {
        // Keep server-supplied IDs separate from the LazyColumn's fixed rows.
        val prefix = "timeline:${base.ifBlank { "item" }}"
        var candidate = prefix
        var suffix = 1
        while (!usedKeys.add(candidate)) {
            candidate = "$prefix-$suffix"
            suffix++
        }
        return candidate
    }
    fun flushSteps() {
        val visible = visibleProcessSteps(steps)
        if (visible.isNotEmpty()) {
            result += ChatTimelineItem.Process(visible, uniqueKey(visible.first().id))
            steps.clear()
        }
    }

    bubbles.forEach { bubble ->
        if (bubble.role == "reasoning" || bubble.role == "tool" || bubble.role == "compact" ||
            bubble.toolName != null || (bubble.role == "assistant" && bubble.isIntermediate)) {
            steps += bubble
        } else {
            flushSteps()
            result += ChatTimelineItem.Message(bubble, uniqueKey(bubble.id))
        }
    }
    flushSteps()
    return result
}

/**
 * Reuse the existing timeline while a streaming answer replaces its last
 * bubble. History/tool changes still take the full projection path because
 * they can change process-fold boundaries.
 */
internal class ChatTimelineCache {
    private var bubbles: List<ChatBubble>? = null
    private var timeline: List<ChatTimelineItem> = emptyList()

    fun update(next: List<ChatBubble>): List<ChatTimelineItem> {
        val previous = bubbles
        if (next === previous) return timeline
        val replacement = next as? ReplacedBubbleList
        val incremental = replacement
            ?.takeIf {
                it.source === previous ||
                    (previous is ReplacedBubbleList &&
                        previous.source === it.source &&
                        previous.replacementIndex == it.replacementIndex)
            }
            ?.let(::replaceLastBubble)
        timeline = incremental ?: buildTimeline(next)
        bubbles = next
        return timeline
    }

    private fun replaceLastBubble(change: ReplacedBubbleList): List<ChatTimelineItem>? {
        if (change.replacementIndex != change.source.lastIndex) return null
        val previous = change.source.lastOrNull() ?: return null
        val replacement = change.replacement
        if (isProcessBubble(previous) != isProcessBubble(replacement)) return null
        val lastIndex = timeline.lastIndex
        if (lastIndex < 0) return null

        val updated = when (val item = timeline[lastIndex]) {
            is ChatTimelineItem.Message -> {
                if (isProcessBubble(replacement) || item.bubble.id != previous.id) return null
                item.copy(bubble = replacement)
            }
            is ChatTimelineItem.Process -> {
                if (!isProcessBubble(replacement) || item.steps.lastOrNull()?.id != previous.id) return null
                item.copy(steps = item.steps.dropLast(1) + replacement)
            }
        }
        return timeline.toMutableList().also { it[lastIndex] = updated }
    }

    private fun isProcessBubble(bubble: ChatBubble): Boolean =
        bubble.role == "reasoning" || bubble.role == "tool" || bubble.role == "compact" ||
            bubble.toolName != null || (bubble.role == "assistant" && bubble.isIntermediate)
}

@Composable
fun ChatScreen(
    state: AppUiState,
    vm: AppViewModel,
    railOccupied: Boolean = false,
    onOpenSettings: () -> Unit = {},
    navigationContent: (@Composable (onSelect: (AppDestination) -> Unit, onClose: () -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var pendingCameraTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var pendingVoiceTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedSec by remember { mutableStateOf(0) }
    var captureHint by remember { mutableStateOf<String?>(null) }
    val voiceRecorder = remember { VoiceRecorder() }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val visualPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.addPickedMedia(context.contentResolver, uris)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            vm.addPickedMedia(context.contentResolver, uris)
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val target = pendingCameraTarget
        pendingCameraTarget = null
        if (target == null) return@rememberLauncherForActivityResult
        if (success && target.file.exists() && target.file.length() > 0L) {
            vm.addPickedMedia(context.contentResolver, listOf(target.uri))
            captureHint = null
        } else {
            target.file.delete()
            captureHint = "拍照取消或失败"
        }
    }

    fun launchCameraCapture() {
        if (isRecordingVoice) {
            captureHint = "请先结束录音"
            return
        }
        val target = CaptureMediaStore.newPhotoTarget(context)
        pendingCameraTarget = target
        runCatching { takePictureLauncher.launch(target.uri) }.onFailure {
            pendingCameraTarget = null
            target.file.delete()
            captureHint = it.message ?: "无法打开相机"
        }
    }

    fun startVoiceCapture() {
        if (isRecordingVoice) return
        val target = CaptureMediaStore.newVoiceTarget(context)
        runCatching {
            voiceRecorder.start(context, target.file)
            pendingVoiceTarget = target
            isRecordingVoice = true
            recordingStartedAtMs = System.currentTimeMillis()
            recordingElapsedSec = 0
            captureHint = null
        }.onFailure {
            target.file.delete()
            pendingVoiceTarget = null
            isRecordingVoice = false
            recordingStartedAtMs = null
            captureHint = it.message ?: "无法开始录音"
        }
    }

    fun stopVoiceCapture(cancel: Boolean) {
        if (!isRecordingVoice && pendingVoiceTarget == null) return
        val target = pendingVoiceTarget
        pendingVoiceTarget = null
        isRecordingVoice = false
        recordingStartedAtMs = null
        recordingElapsedSec = 0
        if (cancel) {
            voiceRecorder.cancel()
            target?.file?.delete()
            captureHint = "已取消录音"
            return
        }
        val file = voiceRecorder.stop()
        if (file != null && target != null) {
            vm.addPickedMedia(context.contentResolver, listOf(target.uri))
            captureHint = null
        } else {
            target?.file?.delete()
            captureHint = "录音太短或失败"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture() else captureHint = "需要相机权限才能拍照"
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceCapture() else captureHint = "需要麦克风权限才能录音"
    }

    fun requestCameraCapture() {
        if (hasPermission(Manifest.permission.CAMERA)) launchCameraCapture()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun requestVoiceCapture() {
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) startVoiceCapture()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var sessionPaneExpanded by rememberSaveable { mutableStateOf(true) }
    // Phones have navigation on the left, chat in the center, and sessions on
    // the right. Layouts with a navigation rail retain the two-page session/chat surface.
    val sessionsPage = if (navigationContent != null) 2 else 0
    val compactPagerState = rememberPagerState(initialPage = 1, pageCount = { if (navigationContent != null) 3 else 2 })
    val compactPagerScope = rememberCoroutineScope()
    val sessionPaneWidth = 304.dp
    var renameTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var projectTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var projectText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<RuntimeSession?>(null) }
    var actionSession by remember { mutableStateOf<RuntimeSession?>(null) }

    LaunchedEffect(isRecordingVoice, recordingStartedAtMs) {
        val started = recordingStartedAtMs ?: return@LaunchedEffect
        if (!isRecordingVoice) return@LaunchedEffect
        while (true) {
            recordingElapsedSec = ((System.currentTimeMillis() - started) / 1000L).toInt().coerceAtLeast(0)
            kotlinx.coroutines.delay(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (voiceRecorder.isRecording) {
                voiceRecorder.cancel()
            }
        }
    }

    LaunchedEffect(captureHint) {
        val hint = captureHint ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        if (captureHint == hint) captureHint = null
    }
    LaunchedEffect(state.settings.sessionId) {
        if (state.contextInspect == null && !state.contextInspectLoading && state.contextInspectError == null) {
            vm.refreshContextInspect()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Fixed session pane only when the remaining chat area stays usable.
        val usePermanentSessionPane =
            maxWidth >= 900.dp &&
                (maxWidth - sessionPaneWidth - (if (railOccupied) AppNavRailWidth else 0.dp)) >= MinMainContentWidth

        val sessionDrawer: @Composable (Boolean) -> Unit = { showClose ->
            SessionDrawer(
                state = state,
                onClose = {
                    if (usePermanentSessionPane) sessionPaneExpanded = false
                    else compactPagerScope.launch { compactPagerState.animateScrollToPage(1) }
                },
                onSelect = { id ->
                    vm.selectSession(id)
                    if (!usePermanentSessionPane) {
                        compactPagerScope.launch { compactPagerState.animateScrollToPage(1) }
                    }
                },
                onCreate = {
                    vm.createSession()
                    if (!usePermanentSessionPane) {
                        compactPagerScope.launch { compactPagerState.animateScrollToPage(1) }
                    }
                },
                onQueryChange = vm::updateSessionQuery,
                onRename = { session ->
                    renameTarget = session
                    renameText = session.title?.takeIf { it.isNotBlank() } ?: session.id
                },
                onProject = { session ->
                    projectTarget = session
                    projectText = session.project.orEmpty()
                },
                onPin = { session -> vm.setSessionPinned(session.id, !session.pinned) },
                onLongPress = { session -> actionSession = session },
                onRefresh = vm::refreshSessions,
                onDelete = { session -> deleteTarget = session },
                showClose = showClose,
            )
        }

        if (usePermanentSessionPane) {
            Row(Modifier.fillMaxSize()) {
                if (sessionPaneExpanded) {
                    Column(
                        Modifier
                            .width(sessionPaneWidth)
                            .widthIn(max = sessionPaneWidth)
                            .fillMaxHeight()
                            .background(CloverBgSide.copy(alpha = state.settings.drawerOpacity.coerceIn(0, 100) / 100f))
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        sessionDrawer(true)
                    }
                }
                ChatConversation(
                    state = state,
                    vm = vm,
                    showSessionMenu = !sessionPaneExpanded,
                    onOpenSessions = { sessionPaneExpanded = true },
                    onNewSession = vm::createSession,
                    onOpenSettings = onOpenSettings,
                    showAttachmentOptions = showAttachmentOptions,
                    onToggleAttachment = { showAttachmentOptions = !showAttachmentOptions },
                    onCameraCapture = {
                        showAttachmentOptions = false
                        requestCameraCapture()
                    },
                    onVoiceCapture = {
                        showAttachmentOptions = false
                        requestVoiceCapture()
                    },
                    onVisualPick = {
                        showAttachmentOptions = false
                        visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onFilePick = {
                        showAttachmentOptions = false
                        filePicker.launch(arrayOf("*/*"))
                    },
                    isRecordingVoice = isRecordingVoice,
                    recordingElapsedSec = recordingElapsedSec,
                    captureHint = captureHint,
                    onStopVoice = { stopVoiceCapture(cancel = false) },
                    onCancelVoice = { stopVoiceCapture(cancel = true) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            LaunchedEffect(compactPagerState.settledPage) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            BackHandler(enabled = compactPagerState.currentPage != 1) {
                compactPagerScope.launch { compactPagerState.animateScrollToPage(1) }
            }
            val inheritedOpenNavigation = LocalOpenNavigationDrawer.current
            val openNavigation: (() -> Unit)? = if (navigationContent != null) {
                { compactPagerScope.launch { compactPagerState.animateScrollToPage(0) }; Unit }
            } else inheritedOpenNavigation
            CompositionLocalProvider(LocalOpenNavigationDrawer provides openNavigation) {
                HorizontalPager(
                    state = compactPagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp,
                ) { page ->
                    if (page == 0 && navigationContent != null) {
                        navigationContent(
                            { destination ->
                                compactPagerScope.launch {
                                    // Reset before leaving chat so returning never reopens navigation.
                                    compactPagerState.scrollToPage(1)
                                    vm.navigate(destination)
                                }
                            },
                            { compactPagerScope.launch { compactPagerState.animateScrollToPage(1) } },
                        )
                    } else if (page == sessionsPage) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(CloverBgSide.copy(alpha = state.settings.drawerOpacity.coerceIn(0, 100) / 100f))
                                .windowInsetsPadding(WindowInsets.safeDrawing),
                        ) {
                            sessionDrawer(true)
                        }
                    } else {
                        ChatConversation(
                            state = state,
                            vm = vm,
                            showSessionMenu = true,
                            onOpenSessions = {
                                compactPagerScope.launch { compactPagerState.animateScrollToPage(sessionsPage) }
                            },
                            onNewSession = vm::createSession,
                            onOpenSettings = onOpenSettings,
                            showAttachmentOptions = showAttachmentOptions,
                            onToggleAttachment = { showAttachmentOptions = !showAttachmentOptions },
                            onCameraCapture = {
                                showAttachmentOptions = false
                                requestCameraCapture()
                            },
                            onVoiceCapture = {
                                showAttachmentOptions = false
                                requestVoiceCapture()
                            },
                            onVisualPick = {
                                showAttachmentOptions = false
                                visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            onFilePick = {
                                showAttachmentOptions = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                            isRecordingVoice = isRecordingVoice,
                            recordingElapsedSec = recordingElapsedSec,
                            captureHint = captureHint,
                            onStopVoice = { stopVoiceCapture(cancel = false) },
                            onCancelVoice = { stopVoiceCapture(cancel = true) },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { session ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename session") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameSession(session.id, renameText)
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    projectTarget?.let { session ->
        val knownProjects = state.sessions.mapNotNull { it.project?.trim()?.takeIf { name -> name.isNotEmpty() } }.distinct()
        AlertDialog(
            onDismissRequest = { projectTarget = null },
            title = { Text("项目分类") },
            text = {
                Column {
                    OutlinedTextField(
                        value = projectText,
                        onValueChange = { projectText = it },
                        singleLine = true,
                        label = { Text("项目名") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (knownProjects.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        knownProjects.take(8).forEach { name ->
                            TextButton(onClick = { projectText = name }) { Text(name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.setSessionProject(session.id, projectText)
                        projectTarget = null
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.setSessionProject(session.id, "")
                        projectTarget = null
                    },
                ) { Text("清除") }
            },
        )
    }

    deleteTarget?.let { session ->
        val title = session.title?.takeIf { it.isNotBlank() } ?: session.id
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话") },
            text = { Text("删除「$title」后，服务端记录和本地缓存都会去掉。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteSession(session.id)
                        deleteTarget = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    actionSession?.let { session ->
        val title = session.title?.takeIf { it.isNotBlank() } ?: session.id
        AlertDialog(
            onDismissRequest = { actionSession = null },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectionContainer {
                        Text("会话 ID：${session.id}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("删除会话前还会再次确认。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = session
                        actionSession = null
                    },
                ) { Text("删除会话") }
            },
            dismissButton = {
                TextButton(onClick = { actionSession = null }) { Text("取消") }
            },
        )
    }

}

@Composable
private fun ChatConversation(
    state: AppUiState,
    vm: AppViewModel,
    showSessionMenu: Boolean,
    onOpenSessions: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
    showAttachmentOptions: Boolean,
    onToggleAttachment: () -> Unit,
    onCameraCapture: () -> Unit,
    onVoiceCapture: () -> Unit,
    onVisualPick: () -> Unit,
    onFilePick: () -> Unit,
    isRecordingVoice: Boolean,
    recordingElapsedSec: Int,
    captureHint: String?,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showThinkingSheet by rememberSaveable(state.settings.sessionId) { mutableStateOf(false) }
    var selectedThinkingSteps by remember(state.settings.sessionId) { mutableStateOf<List<ChatBubble>>(emptyList()) }
    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeSnackbar()
    }
    LaunchedEffect(state.attachmentNotice?.text, state.attachmentNotice?.openUri) {
        val notice = state.attachmentNotice ?: return@LaunchedEffect
        if (!notice.isComplete && !notice.isError) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = notice.text,
            actionLabel = notice.openUri?.let { "打开" },
        )
        if (result == SnackbarResult.ActionPerformed && notice.openUri != null) {
            vm.openDownloadedAttachment(context, notice.openUri, notice.mimeType)
        }
    }
    val imageHeaders = remember(state.settings.apiKey, state.settings.useBearer) {
        if (state.settings.apiKey.isBlank()) emptyMap()
        else if (state.settings.useBearer) mapOf("Authorization" to "Bearer ${state.settings.apiKey}")
        else mapOf("X-API-Key" to state.settings.apiKey)
    }
    LaunchedEffect(showModelSheet) {
        if (showModelSheet) {
            vm.loadModels()
            vm.loadModelEffort()
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ChatTopBar(
            state = state,
            onMenu = onOpenSessions,
            showMenu = showSessionMenu,
            onNewSession = onNewSession,
            onOpenSettings = onOpenSettings,
            onOpenModel = { showModelSheet = true },
            onOpenCanvas = { vm.navigate(AppDestination.Background) },
            onReconnect = { vm.connectSocket(force = true) },
        )
        val workCard = remember(
            state.focusedWorkCardId,
            state.focusedWorkCardBackground,
            state.tasks,
            state.backgroundTasks,
            state.settings.sessionId,
            state.dismissedWorkCardIds,
        ) {
            visibleWorkCard(
                focusedId = state.focusedWorkCardId,
                focusedBackground = state.focusedWorkCardBackground,
                tasks = state.tasks,
                background = state.backgroundTasks,
                sessionId = state.settings.sessionId,
                dismissedIds = state.dismissedWorkCardIds,
            )
        }
        if (workCard != null) {
            WorkReturnCard(
                card = workCard,
                onClose = { vm.dismissWorkCard(workCard.id) },
                onCancel = { vm.cancelWorkCard(workCard.id) },
                onOpenResult = {
                    val session = workCard.sessionId
                    if (!session.isNullOrBlank()) vm.openSession(session)
                    if (workCard.terminal) vm.dismissWorkCard(workCard.id)
                },
                onOpenDetail = {
                    if (workCard.kind == WorkCardKind.Background) {
                        vm.selectBackgroundTask(workCard.id)
                        vm.navigate(AppDestination.Background)
                    } else {
                        vm.selectTask(workCard.id)
                        vm.navigate(AppDestination.Tasks)
                    }
                },
            )
        }
        if (!hiddenSessionCard(state.hiddenSessionCards, state.settings.sessionId, SessionCardKind.Panel, SESSION_PANEL_CARD_ID)) {
        SessionNavigationCard(
            sessionId = state.settings.sessionId,
            tasks = state.tasks,
            background = state.backgroundTasks,
            hiddenCards = state.hiddenSessionCards,
            tasksError = state.tasksError,
            backgroundError = state.backgroundError,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            onHideCard = { kind, id -> vm.hideSessionCard(state.settings.sessionId, kind, id) },
            onHidePanel = {
                vm.hideSessionCard(state.settings.sessionId, SessionCardKind.Panel, SESSION_PANEL_CARD_ID)
            },
            onOpenTask = { taskId ->
                if (taskId != null) vm.selectTask(taskId)
                vm.navigate(AppDestination.Tasks)
            },
            onOpenBackground = { taskId ->
                if (taskId != null) vm.selectBackgroundTask(taskId)
                vm.navigate(AppDestination.Background)
            },
            onOpenTrace = { vm.navigate(AppDestination.Trajectory) },
            onOpenMemory = { vm.navigate(AppDestination.Memory) },
        )
        }

        val listState = rememberLazyListState()
        val scrollScope = rememberCoroutineScope()
        val refreshState = rememberPullToRefreshState()
        val timelineCache = remember { ChatTimelineCache() }
        val timeline = remember(state.bubbles) { timelineCache.update(state.bubbles) }
        val latestAssistantId = remember(state.bubbles) {
            state.bubbles
                .lastOrNull { it.role.equals("assistant", ignoreCase = true) && !it.isIntermediate && it.content.isNotBlank() }
                ?.id
        }
        val atBottom by remember {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisible == null ||
                    (lastVisible.index >= listState.layoutInfo.totalItemsCount - 1 &&
                        lastVisible.offset + lastVisible.size <= listState.layoutInfo.viewportEndOffset + 48)
            }
        }
        val unreadCount by remember(timeline) {
            derivedStateOf {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                (timeline.size - lastVisible - 1).coerceAtLeast(0)
            }
        }
        val latestHistoryState by rememberUpdatedState(state.historyHasMore to state.historyLoadingMore)
        var lastAutoScrollPosition by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var restoredSession by remember { mutableStateOf("") }
        val latestContentKey = timeline.lastOrNull()?.let { item ->
            when (item) {
                is ChatTimelineItem.Message -> item.bubble.content.length
                is ChatTimelineItem.Process -> item.steps.sumOf { it.content.length }
            }
        } ?: 0
        LaunchedEffect(state.settings.sessionId) {
            var wasNearTop = false
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisible ->
                    val nearTop = firstVisible <= 2
                    val (hasMore, loadingMore) = latestHistoryState
                    if (nearTop && !wasNearTop && hasMore && !loadingMore) {
                        vm.loadMoreHistory()
                    }
                    wasNearTop = nearTop
                }
        }
        LaunchedEffect(state.settings.sessionId) {
            @OptIn(FlowPreview::class)
            snapshotFlow { listState.isScrollInProgress }
                .debounce(400)
                .collect { scrolling ->
                    // Writing the anchor on every pixel used to wake the VM during
                    // a fling. One write after the finger stops is enough to restore.
                    if (!scrolling && restoredSession == state.settings.sessionId) {
                        vm.saveScrollAnchor(
                            state.settings.sessionId,
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                        )
                    }
                }
        }
        LaunchedEffect(state.settings.sessionId, timeline.size, timeline.lastOrNull()?.key) {
            if (timeline.isEmpty() || restoredSession == state.settings.sessionId) return@LaunchedEffect
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            val anchor = vm.scrollAnchor(state.settings.sessionId)
            restoredSession = state.settings.sessionId
            val (index, offset) = openingScrollIndex(anchor, timeline.lastIndex)
            listState.scrollToItem(index, offset)
            lastAutoScrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        LaunchedEffect(timeline.size, timeline.lastOrNull()?.key, latestContentKey, state.liveSummary, state.isResponding) {
            if (timeline.isEmpty() || restoredSession != state.settings.sessionId || listState.isScrollInProgress) return@LaunchedEffect
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            val atBottom = lastVisible == null ||
                (lastVisible.index >= layout.totalItemsCount - 2 && lastVisible.offset + lastVisible.size <= layout.viewportEndOffset + 48)
            val position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            if (shouldFollowLatest(atBottom, position == lastAutoScrollPosition)) {
                listState.scrollToItem(timeline.lastIndex.coerceAtLeast(0))
                lastAutoScrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PullToRefreshBox(
                state = refreshState,
                isRefreshing = state.historyLoading || state.historyRefreshing,
                onRefresh = { vm.loadHistory(reset = true) },
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = refreshState,
                        isRefreshing = state.historyLoading || state.historyRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = timeline,
                        key = { it.key },
                        contentType = { item ->
                            when (item) {
                                is ChatTimelineItem.Message -> "message"
                                is ChatTimelineItem.Process -> "process"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is ChatTimelineItem.Message -> BubbleRow(
                                bubble = item.bubble,
                                avatarFile = state.settings.avatarFile,
                                bubbleOpacity = state.settings.bubbleOpacity,
                                imageHeaders = imageHeaders,
                                imageBaseUrl = state.settings.apiBase,
                                surfaceActive = bubbleSurfaceActive(listState, item.key),
                                onDownload = { media -> vm.downloadAttachment(context, media) },
                                onQuote = { vm.quoteMessage(it) },
                                onCopy = { bubble ->
                                    val copied = vm.copyBubbleText(bubble)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("luckyagent-message", copied))
                                    vm.notifyCopied()
                                },
                                showActions = item.bubble.id == latestAssistantId,
                                onDelete = vm::deleteBubble,
                                onRetry = vm::retryLastResponse,
                                onRetryOutbound = vm::retryOutboundMessage,
                                onSpeak = { bubble -> speakText(context, bubble.content) },
                                onExpand = vm::expandTruncatedMessage,
                                expanding = state.expandingHistoryIndex >= 0 &&
                                    state.expandingHistoryIndex == item.bubble.historyIndex,
                            )
                            is ChatTimelineItem.Process -> ProcessTimeline(
                                steps = item.steps,
                                isResponding = state.isResponding && processOwnsTurn(item.steps, timeline),
                                liveSummary = outerProcessLabel(
                                    liveSummary = state.liveSummary,
                                    processNotice = state.processNotice,
                                    isResponding = state.isResponding,
                                    isLatestProcess = item.key == timeline.lastOrNull { it is ChatTimelineItem.Process }?.key,
                                    ownsCurrentTurn = processOwnsTurn(item.steps, timeline),
                                ),
                                imageHeaders = imageHeaders,
                                imageBaseUrl = state.settings.apiBase,
                                onDownload = { media -> vm.downloadAttachment(context, media) },
                                onQuote = { vm.quoteMessage(it) },
                                onCopy = { bubble ->
                                    val copied = vm.copyBubbleText(bubble)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("luckyagent-message", copied))
                                    vm.notifyCopied()
                                },
                                onExpand = vm::expandTruncatedMessage,
                                expandingHistoryIndex = state.expandingHistoryIndex,
                                onOpenThinking = {
                                    selectedThinkingSteps = item.steps.filterNot(::isTurnStatusPlaceholder)
                                    showThinkingSheet = true
                                },
                                collabLabel = collabFoldLabel(
                                    collabTasksForTurn(item.steps, state.tasks, state.settings.sessionId),
                                    state.isResponding,
                                ),
                            )
                        }
                    }
                    if (state.pendingApprovals.isNotEmpty()) {
                        item(key = "pending-approvals") {
                            ApprovalCards(
                                approvals = state.pendingApprovals,
                                onDecision = vm::resolveApproval,
                            )
                        }
                    }
                    state.approvalsError?.takeIf { state.pendingApprovals.isEmpty() }?.let { error ->
                        item(key = "approvals-error") {
                            Text(error, color = CloverError, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item(key = "chat-tail") { Spacer(Modifier.height(1.dp)) }
                }
            }
            if ((state.showingOfflineCache || state.activityLine == SessionCachePolicy.REFRESH_FAILED_LABEL) && state.bubbles.isNotEmpty()) {
                Text(
                    state.activityLine ?: SessionCachePolicy.OFFLINE_LABEL,
                    color = CloverText3,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CloverSurface.copy(alpha = .94f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (state.historyLoading && timeline.isEmpty()) {
                StartupWait(
                    phase = StartupPhase.History,
                    next = StartupPhase.HistoryNext,
                    modifier = Modifier.align(Alignment.Center),
                    onTimedOut = {
                        TextButton(onClick = { vm.loadHistory(forceSync = true) }) { Text("重试") }
                    },
                )
            } else if (state.historyLoading || state.historyRefreshing || state.historyLoadingMore) {
                StartupReconcileBar(
                    phase = when {
                        state.historyLoadingMore -> "正在读取更早的对话"
                        state.historyRefreshing -> "正在刷新"
                        else -> StartupPhase.History
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (!atBottom && timeline.size > 1) {
                // Only show the return-to-bottom affordance after the user leaves the tail.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(CloverSurface.copy(alpha = .96f))
                        .border(0.5.dp, CloverLine.copy(alpha = .45f), RoundedCornerShape(999.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 14.dp)
                            .clickable { scrollScope.launch { listState.animateScrollToItem(timeline.size) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "回到底部",
                            modifier = Modifier.size(16.dp),
                            tint = CloverText3,
                        )
                        if (unreadCount > 0) {
                            Text(
                                unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = CloverAccent,
                                modifier = Modifier.padding(start = 18.dp),
                            )
                        }
                    }
                }
            }
        }

        ComposerBar(
            state = state,
            vm = vm,
            onChange = vm::updateComposer,
            onSend = vm::sendComposer,
            showAttachmentOptions = showAttachmentOptions,
            onToggleAttachment = onToggleAttachment,
            onCameraCapture = onCameraCapture,
            onVoiceCapture = onVoiceCapture,
            onVisualPick = onVisualPick,
            onFilePick = onFilePick,
            onRemoveMedia = vm::removePendingMedia,
            onRetryMedia = { id -> vm.retryPendingMedia(context.contentResolver, id) },
            onClearQuote = vm::clearQuote,
            isRecordingVoice = isRecordingVoice,
            recordingElapsedSec = recordingElapsedSec,
            captureHint = captureHint,
            onStopVoice = onStopVoice,
            onCancelVoice = onCancelVoice,
        )
        if (showModelSheet) {
            ModelSwitchSheet(
                state = state,
                onDismiss = { showModelSheet = false },
                onRetry = { vm.loadModels(force = true) },
                onRetryEffort = { vm.loadModelEffort(force = true) },
                onSetEffort = vm::setModelEffort,
                onSelect = {
                    vm.switchModel(it)
                    showModelSheet = false
                },
            )
        }
        if (showThinkingSheet) {
            // Resolve the selected fold by its stable first step so a live sheet
            // also receives newly created subagents while the turn is running.
            val thinkingSteps = timeline.filterIsInstance<ChatTimelineItem.Process>()
                .firstOrNull { it.steps.firstOrNull()?.id == selectedThinkingSteps.firstOrNull()?.id }
                ?.steps ?: selectedThinkingSteps
            val turnTasks = remember(thinkingSteps, state.tasks, state.settings.sessionId) {
                collabTasksForTurn(thinkingSteps, state.tasks, state.settings.sessionId)
            }
            ThinkingSheet(
                steps = thinkingSteps,
                collabLabel = collabFoldLabel(
                    turnTasks,
                    state.isResponding,
                ),
                collabTasks = turnTasks,
                onOpenTask = {
                    vm.focusWorkCard(it, background = false)
                    vm.selectTask(it)
                    vm.navigate(AppDestination.Tasks)
                },
                imageHeaders = imageHeaders,
                imageBaseUrl = state.settings.apiBase,
                onDismiss = { showThinkingSheet = false },
                onDownload = { media -> vm.downloadAttachment(context, media) },
                onQuote = { vm.quoteMessage(it) },
                onCopy = { bubble ->
                    val copied = vm.copyBubbleText(bubble)
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("luckyagent-message", copied))
                    vm.notifyCopied()
                },
                onExpand = vm::expandTruncatedMessage,
                expandingHistoryIndex = state.expandingHistoryIndex,
            )
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }
}

@Composable
private fun WorkReturnCard(
    card: WorkCard,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onOpenResult: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    CloverCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.loading) {
                    Text(
                        "列表还没回来。可以打开详情，或先关掉这张卡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloverText2,
                    )
                }
            }
            if (!card.loading) StatusChip(card.status, workCardStatusLabel(card.status))
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭卡片", tint = CloverText3)
            }
        }
        if (card.terminal && card.sessionId.isNullOrBlank()) {
            Text("还没有关联的对话", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!card.loading && workCardCanCancel(card)) {
                OutlinedButton(onClick = onCancel) { Text("取消") }
            }
            if (!card.loading && card.terminal && !card.sessionId.isNullOrBlank()) {
                Button(onClick = onOpenResult) { Text("打开结果") }
            }
            TextButton(onClick = onOpenDetail) { Text("打开详情") }
        }
    }
}

@Composable
private fun SessionNavigationCard(
    sessionId: String,
    tasks: List<TaskSummary>,
    background: List<AutonomyTaskSummary>,
    hiddenCards: Set<String> = emptySet(),
    tasksError: String? = null,
    backgroundError: String? = null,
    modifier: Modifier = Modifier,
    onHideCard: (SessionCardKind, String) -> Unit = { _, _ -> },
    onHidePanel: () -> Unit = {},
    onOpenTask: (String?) -> Unit,
    onOpenBackground: (String?) -> Unit,
    onOpenTrace: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val mine = remember(tasks, sessionId) { tasksForSession(tasks, sessionId) }
    val board = remember(background, sessionId) { backgroundForSession(background, sessionId) }
    val featured = remember(mine) { sessionNavigationTask(mine) }
    val featuredBoard = remember(board) { sessionNavigationBackground(board) }
    val quiet = featured == null && featuredBoard == null
    // The task and background entries are gone from the drawer, so this card must
    // open them unconditionally. Otherwise a session with no task of its own has
    // no way to reach the global task list or the board.
    val failure = when {
        tasksError != null && backgroundError != null -> "任务和后台列表读取失败"
        tasksError != null -> "任务列表读取失败"
        backgroundError != null -> "后台列表读取失败"
        else -> null
    }
    var confirmHidePanel by remember { mutableStateOf(false) }
    if (confirmHidePanel) {
        SessionCardHideDialog(
            name = "这张卡片",
            onDismiss = { confirmHidePanel = false },
            onConfirm = {
                confirmHidePanel = false
                onHidePanel()
            },
        )
    }
    CloverCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "当前会话",
                style = MaterialTheme.typography.labelMedium,
                color = CloverText3,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmHidePanel = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除当前会话卡片",
                    modifier = Modifier.size(16.dp),
                    tint = CloverText3,
                )
            }
        }
        when {
            featured != null -> Text(
                featured.description.ifBlank { "正在处理的任务" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            featuredBoard != null -> Text(
                featuredBoard.title.ifBlank { featuredBoard.description.ifBlank { "后台还在处理" } },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text("这轮还没有单独的任务。轨迹和记忆从这里进入。", color = CloverText2, style = MaterialTheme.typography.bodySmall)
        }
        if (failure != null) {
            Text(failure, color = CloverWarning, style = MaterialTheme.typography.labelMedium)
        }
        if (!quiet) {
            val tone = when {
                featured?.status?.taskNeedsAttention() == true || featuredBoard?.state?.taskNeedsAttention() == true -> CloverWarning
                else -> CloverLeaf
            }
            Text(
                sessionProgressLabel(featured, featuredBoard),
                color = tone,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        var pendingHide by remember { mutableStateOf<Pair<SessionCardKind, String>?>(null) }
        pendingHide?.let { (kind, id) ->
            SessionCardHideDialog(
                kind = kind,
                onDismiss = { pendingHide = null },
                onConfirm = {
                    pendingHide = null
                    onHideCard(kind, id)
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SessionCardLink(
                label = "任务",
                icon = Icons.AutoMirrored.Outlined.Assignment,
                hidden = featured?.id?.let { hiddenSessionCard(hiddenCards, sessionId, SessionCardKind.Task, it) } == true,
                onOpen = { onOpenTask(featured?.id) },
                onHide = featured?.id?.let { id -> { pendingHide = SessionCardKind.Task to id } },
            )
            SessionCardLink(
                label = "后台",
                icon = Icons.Outlined.Autorenew,
                hidden = featuredBoard?.id?.let { hiddenSessionCard(hiddenCards, sessionId, SessionCardKind.Background, it) } == true,
                onOpen = { onOpenBackground(featuredBoard?.id) },
                onHide = featuredBoard?.id?.let { id -> { pendingHide = SessionCardKind.Background to id } },
            )
            SessionCardLink(
                label = "轨迹",
                icon = Icons.Outlined.Timeline,
                hidden = hiddenSessionCard(hiddenCards, sessionId, SessionCardKind.Trace, "page"),
                onOpen = onOpenTrace,
                onHide = { pendingHide = SessionCardKind.Trace to "page" },
            )
            SessionCardLink(
                label = "记忆",
                icon = Icons.Outlined.AccountTree,
                hidden = hiddenSessionCard(hiddenCards, sessionId, SessionCardKind.Memory, "page"),
                onOpen = onOpenMemory,
                onHide = { pendingHide = SessionCardKind.Memory to "page" },
            )
        }
    }
}

@Composable
private fun SessionCardLink(
    label: String,
    icon: ImageVector,
    hidden: Boolean,
    onOpen: () -> Unit,
    onHide: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            onClick = onOpen,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
        if (!hidden && onHide != null) {
            IconButton(onClick = onHide, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除$label",
                    modifier = Modifier.size(15.dp),
                    tint = CloverText3,
                )
            }
        }
    }
}

@Composable
private fun SessionCardHideDialog(
    kind: SessionCardKind,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = when (kind) {
        SessionCardKind.Task -> "任务"
        SessionCardKind.Background -> "后台"
        SessionCardKind.Trace -> "轨迹"
        SessionCardKind.Memory -> "记忆"
        SessionCardKind.Panel -> "这张卡片"
    }
    SessionCardHideDialog(name = name, onDismiss = onDismiss, onConfirm = onConfirm)
}

@Composable
private fun SessionCardHideDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从当前会话拿掉$name") },
        text = { Text("只从这台手机的当前会话拿掉。电脑上的记录还在。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun sessionProgressLabel(task: TaskSummary?, background: AutonomyTaskSummary?): String {
    val taskLabel = task?.let { friendlyTaskStatus(it.status) }
    val boardLabel = background?.let { friendlyTaskStatus(it.state) }
    return listOfNotNull(
        taskLabel?.let { "任务 · $it" },
        boardLabel?.let { "后台 · $it" },
    ).joinToString(" · ")
}

@Composable
private fun ChatTopBar(
    state: AppUiState,
    onMenu: () -> Unit,
    showMenu: Boolean,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenCanvas: () -> Unit,
    onReconnect: () -> Unit = {},
) {
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    val openNavigation = LocalOpenNavigationDrawer.current
    val hasWallpaper = state.settings.chatBackgroundFile.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .background(CloverBg.copy(alpha = state.settings.chromeOpacity.coerceIn(0, 100) / 100f)),
    ) {
        if (!hasWallpaper) HorizontalDivider(color = CloverLine.copy(alpha = .55f))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Outlined.Menu, contentDescription = "打开会话", tint = CloverText2)
                }
            }
            ModelSwitchButton(
                state = state,
                onClick = onOpenModel,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewSession) {
                Icon(Icons.Outlined.Add, contentDescription = "新建会话", tint = CloverText2)
            }
            Box {
                IconButton(onClick = { moreExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = CloverText2)
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false },
                ) {
                    openNavigation?.let { onOpen ->
                        DropdownMenuItem(
                            text = { Text("这台电脑") },
                            onClick = {
                                moreExpanded = false
                                onOpen()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("设置") },
                        onClick = {
                            moreExpanded = false
                            onOpenSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重新连接") },
                        onClick = {
                            moreExpanded = false
                            onReconnect()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("会话列表") },
                        onClick = {
                            moreExpanded = false
                            onMenu()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("多 agent 画布") },
                        onClick = {
                            moreExpanded = false
                            onOpenCanvas()
                        },
                    )
                }
            }
        }
        if (!hasWallpaper) HorizontalDivider(color = CloverLine.copy(alpha = .55f))
    }
}

@Composable
private fun ChatBackground(settings: com.luckyagent.android.data.settings.ClientSettings) {
    val context = LocalContext.current
    val file = settings.chatBackgroundFile.takeIf { it.isNotBlank() }?.let {
        com.luckyagent.android.data.settings.AppearanceStore.file(context, it)
    }?.takeIf { it.exists() }
    if (file == null) {
        Box(Modifier.fillMaxSize().background(CloverBg))
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(file).memoryCacheKey(file.absolutePath + file.lastModified()).diskCacheKey(file.absolutePath + file.lastModified()).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = settings.chatBackgroundDim.coerceIn(0, 70) / 100f)))
}

@Composable
private fun ProfileAvatar(fileName: String, onClick: (() -> Unit)? = null, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val context = LocalContext.current
    val file = fileName.takeIf { it.isNotBlank() }?.let {
        com.luckyagent.android.data.settings.AppearanceStore.file(context, it)
    }?.takeIf { it.exists() }
    val shape = Modifier
        .size(size)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    if (file == null) {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = CloverText2,
            modifier = shape,
        )
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context).data(file).memoryCacheKey(file.absolutePath + file.lastModified()).diskCacheKey(file.absolutePath + file.lastModified()).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = shape,
    )
}


@Composable
private fun FullTextButton(
    bubble: ChatBubble,
    expanding: Boolean,
    onExpand: (ChatBubble) -> Unit,
) {
    TextButton(
        onClick = { onExpand(bubble) },
        enabled = !expanding,
    ) {
        Text(
            if (expanding) "加载中" else "查看全文",
            color = CloverText3,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BubbleRow(
    bubble: ChatBubble,
    avatarFile: String,
    bubbleOpacity: Int,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    surfaceActive: Boolean = true,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
    showActions: Boolean = false,
    onDelete: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onRetryOutbound: (String) -> Unit = {},
    onSpeak: (ChatBubble) -> Unit = {},
    onExpand: (ChatBubble) -> Unit = {},
    expanding: Boolean = false,
) {
    var menuExpanded by remember(bubble.id) { mutableStateOf(false) }
    var infoExpanded by remember(bubble.id) { mutableStateOf(false) }
    var deleteConfirm by remember(bubble.id) { mutableStateOf(false) }
    val isUser = bubble.role.equals("user", ignoreCase = true)
    val isSystem = bubble.role.equals("system", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        when {
            bubble.role.equals("search", ignoreCase = true) -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("搜索结果", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                    MarkdownText(
                        markdown = bubble.content,
                        imageHeaders = imageHeaders,
                        imageBaseUrl = imageBaseUrl,
                        active = surfaceActive,
                    )
                }
            }
            isSystem -> {
                Text(
                    bubble.content,
                    color = CloverText3,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            else -> {
                Column(
                    Modifier
                        .widthIn(max = 520.dp)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                ) {
                    if (!isUser && !bubble.role.equals("assistant", ignoreCase = true)) {
                        Text(
                            bubble.role.ifBlank { "assistant" },
                            style = MaterialTheme.typography.labelSmall,
                            color = CloverText3,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Column(
                        Modifier
                            .pointerInput(bubble.id) {
                                detectTapGestures(onLongPress = { menuExpanded = true })
                            },
                    ) {
                        bubble.quote?.let { quote ->
                            QuoteCard(quote = quote, compact = true)
                            Spacer(Modifier.height(8.dp))
                        }
                        if (bubble.content.isNotBlank()) {
                            val textModifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (isUser) {
                                        Modifier
                                            .background(CloverUserBubble.copy(alpha = bubbleOpacity.coerceIn(0, 100) / 100f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    } else {
                                        Modifier.padding(vertical = 2.dp)
                                    },
                                )
                            Box(textModifier) {
                                MarkdownText(
                                    markdown = bubble.content,
                                    imageHeaders = imageHeaders,
                                    imageBaseUrl = imageBaseUrl,
                                    streaming = bubble.streaming,
                                    active = surfaceActive,
                                )
                            }
                            if (bubble.contentTruncated) {
                                FullTextButton(bubble, expanding, onExpand)
                            }
                        }
                        if (bubble.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            val images = bubble.attachments.filter { attachmentKind(it.descriptor) == "image" }
                            val otherMedia = bubble.attachments.filter { attachmentKind(it.descriptor) != "image" }
                            if (images.size > 1) {
                                ImageAttachmentGrid(images, imageHeaders, imageBaseUrl, onDownload)
                            } else {
                                images.forEach { media ->
                                    ChatMediaPreview(
                                        media = media,
                                        imageHeaders = imageHeaders,
                                        imageBaseUrl = imageBaseUrl,
                                        onDownload = onDownload,
                                    )
                                }
                            }
                            otherMedia.forEach { media ->
                                ChatMediaPreview(
                                    media = media,
                                    imageHeaders = imageHeaders,
                                    imageBaseUrl = imageBaseUrl,
                                    onDownload = onDownload,
                                )
                            }
                        }
                        bubble.outboundStatus?.let { status ->
                            val (label, tint) = when (status) {
                                OutboundQueueStatus.Queued -> "等待发送" to CloverText3
                                OutboundQueueStatus.Sending -> "正在提交" to CloverAccent
                                OutboundQueueStatus.Accepted -> "电脑已收下" to CloverAccent
                                OutboundQueueStatus.ServerQueued -> "电脑已收下，等上一轮结束" to CloverWarning
                                OutboundQueueStatus.Running -> "电脑正在处理" to CloverAccent
                                OutboundQueueStatus.Failed -> "未送达" to CloverError
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 5.dp),
                            ) {
                                Text(
                                    bubble.outboundError?.let { "$label · $it" } ?: label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tint,
                                )
                                if (status == OutboundQueueStatus.Failed && bubble.outboundId != null) {
                                    TextButton(
                                        onClick = { onRetryOutbound(bubble.outboundId) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    ) {
                                        Text("重试")
                                    }
                                }
                            }
                        }
                        if (showActions && !isUser && bubble.content.isNotBlank()) {
                            MessageActionRow(
                                onCopy = { onCopy(bubble) },
                                onSpeak = { onSpeak(bubble) },
                                onRetry = onRetry,
                                onMore = { menuExpanded = true },
                            )
                        }
                        if (bubble.createdAt != null || bubble.usage != null) {
                            MessageMetadata(bubble)
                        }
                    }
                    MessageActionMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onQuote = { onQuote(bubble) },
                        onCopy = { onCopy(bubble) },
                        onInfo = { infoExpanded = true },
                        onDelete = { deleteConfirm = true },
                    )
                }
            }
        }
    }
    if (infoExpanded) {
        AlertDialog(
            onDismissRequest = { infoExpanded = false },
            title = { Text("消息信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("角色：${bubble.role}", style = MaterialTheme.typography.bodyMedium)
                    bubble.createdAt?.let { Text("时间：${formatMessageTime(it)}", style = MaterialTheme.typography.bodyMedium) }
                    bubble.usage?.let { usage ->
                        Text("输入：${formatTokenCount(usage.inputTokens)}", style = MaterialTheme.typography.bodyMedium)
                        Text("输出：${formatTokenCount(usage.outputTokens)}", style = MaterialTheme.typography.bodyMedium)
                        Text("总计：${formatTokenCount(usage.totalTokens)}", style = MaterialTheme.typography.bodyMedium)
                        usage.model?.takeIf { it.isNotBlank() }?.let { Text("模型：$it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { infoExpanded = false }) { Text("关闭") } },
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除消息？") },
            text = { Text("这条消息会从当前设备的聊天视图中移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        onDelete(bubble.id)
                    },
                ) { Text("删除", color = CloverError) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MessageActionRow(
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onRetry: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制", tint = CloverText2, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.VolumeUp, contentDescription = "朗读", tint = CloverText2, modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Replay, contentDescription = "重试", tint = CloverText2, modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多", tint = CloverText2, modifier = Modifier.size(20.dp))
        }
    }
}


@Composable
private fun MessageActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("引用") },
            onClick = {
                onQuote()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                onCopy()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        DropdownMenuItem(
            text = { Text("信息") },
            onClick = {
                onInfo()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("删除", color = CloverError) },
            onClick = {
                onDelete()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = CloverError) },
        )
    }
}

@Composable
private fun QuoteCard(quote: MessageQuote, compact: Boolean = false, onClear: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CloverSurface2)
            .border(0.5.dp, CloverLine.copy(alpha = .55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (compact) 28.dp else 34.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CloverAccent),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "回复 ${quoteRoleLabel(quote.role)}",
                style = MaterialTheme.typography.labelSmall,
                color = CloverAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                quotePreview(quote.content, max = if (compact) 90 else 120),
                style = MaterialTheme.typography.bodySmall,
                color = CloverText2,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "取消引用",
                    tint = CloverText3,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ImageAttachmentGrid(
    media: List<ChatMedia>,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        media.chunked(3).forEach { rowMedia ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowMedia.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        ChatMediaPreview(
                            media = item,
                            imageHeaders = imageHeaders,
                            imageBaseUrl = imageBaseUrl,
                            onDownload = onDownload,
                            compact = true,
                        )
                    }
                }
                repeat(3 - rowMedia.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MessageMetadata(bubble: ChatBubble) {
    var expanded by remember(bubble.id) { mutableStateOf(false) }
    val usage = bubble.usage
    val summary = listOfNotNull(
        bubble.createdAt?.let(::formatMessageTime),
        usage?.let(::formatUsageSummary),
    ).joinToString(" · ")
    if (summary.isBlank()) return

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = usage != null) { expanded = !expanded },
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "运行详情 · $summary",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (expanded && usage != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text("输入 ${formatTokenCount(usage.inputTokens)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                if (usage.cachedInputTokens > 0) {
                    Text(
                        "缓存 ${formatTokenCount(usage.cachedInputTokens)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CloverText3,
                    )
                }
                Text("输出 ${formatTokenCount(usage.outputTokens)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                Text("总计 ${formatTokenCount(usage.totalTokens)}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                usage.model?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatMessageTime(raw: String): String {
    return runCatching {
        val zone = ZoneId.systemDefault()
        val local = Instant.parse(raw).atZone(zone)
        val today = LocalDate.now(zone)
        if (local.toLocalDate() == today) {
            local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        } else {
            local.format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
        }
    }.getOrElse { raw.take(16).replace('T', ' ') }
}

private fun speakText(context: Context, text: String) {
    if (text.isBlank()) return
    var speaker: TextToSpeech? = null
    speaker = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            speaker?.language = Locale.getDefault()
            speaker?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "luckyagent-message")
        }
    }
}

private fun formatUsageSummary(usage: TokenUsage): String? = TokenFormat.usageSummary(usage)

private fun formatTokenCount(tokens: Int): String = TokenFormat.full(tokens)

private fun compactTokenCount(tokens: Int): String = TokenFormat.compact(tokens)

@Composable
private fun ApprovalCards(
    approvals: List<PendingApproval>,
    onDecision: (PendingApproval, String, String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        approvals.forEach { approval ->
            ApprovalCard(approval = approval, onDecision = onDecision)
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    onDecision: (PendingApproval, String, String) -> Unit,
) {
    val needsInput = approval.needsTextInput()
    val secure = approval.isSecureCredentialForm()
    var draft by rememberSaveable(approval.id) { mutableStateOf("") }
    var expanded by rememberSaveable(approval.id) { mutableStateOf(needsInput) }
    val headline = when {
        secure -> "填写凭据"
        needsInput -> "需要补充"
        else -> approval.method?.takeIf { it.isNotBlank() } ?: "需要审批"
    }
    val summary = if (needsInput) {
        approval.inputPrompt()
    } else {
        approval.summary?.ifBlank { null } ?: approval.reason?.ifBlank { null } ?: "外部工具请求权限"
    }
    val allow = approval.options.firstOrNull { option ->
        val kind = option.kind.orEmpty().lowercase()
        !kind.contains("reject") && !kind.contains("deny") && !kind.contains("cancel")
    }
    val deny = approval.options.firstOrNull { option ->
        val kind = option.kind.orEmpty().lowercase()
        kind.contains("reject") || kind.contains("deny") || kind.contains("cancel")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CloverSurface.copy(alpha = .9f),
        border = BorderStroke(1.dp, CloverAccent.copy(alpha = .35f)),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(headline, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = CloverText3,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (!needsInput && allow != null) {
                    TextButton(
                        onClick = { onDecision(approval, allow.id, "") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text(allow.label, style = MaterialTheme.typography.labelMedium) }
                }
                if (!needsInput && deny != null) {
                    TextButton(
                        onClick = { onDecision(approval, deny.id, "") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text(deny.label, style = MaterialTheme.typography.labelMedium, color = CloverText2) }
                }
            }
            if (expanded && !needsInput) {
                approval.reason?.takeIf { it.isNotBlank() && it != summary }?.let { reason ->
                    Text(reason, style = MaterialTheme.typography.bodySmall, color = CloverText2)
                }
                approval.params?.let { params ->
                    Text(
                        params.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CloverText3,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (expanded && needsInput) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().then(if (secure) Modifier else Modifier.heightIn(min = 88.dp)),
                    label = { Text(if (secure) "凭据" else "填写内容") },
                    placeholder = { Text(if (secure) "输入后只保存在本机加密库" else "输入后点提交") },
                    visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secure) KeyboardType.Password else KeyboardType.Text,
                        imeAction = if (secure) ImeAction.Done else ImeAction.Default,
                    ),
                    singleLine = secure,
                    minLines = if (secure) 1 else 3,
                )
                if (secure) {
                    Text(
                        "明文不会发给模型，也不会出现在聊天记录里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = CloverText2,
                    )
                }
            }
            if (expanded && needsInput) {
                if (approval.options.isEmpty()) {
                    Text("没有可用的审批选项", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        approval.options.forEach { option ->
                            val negative = option.kind.orEmpty().lowercase().let { kind ->
                                kind.contains("reject") || kind.contains("deny") || kind.contains("cancel")
                            }
                            val submit = option.kind.equals("submit", ignoreCase = true) || option.id.equals("submit", ignoreCase = true)
                            val enabled = !submit || draft.isNotBlank()
                            if (negative) {
                                OutlinedButton(onClick = { onDecision(approval, option.id, "") }) { Text(option.label) }
                            } else {
                                Button(
                                    onClick = { onDecision(approval, option.id, draft) },
                                    enabled = enabled,
                                ) { Text(option.label) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessTimeline(
    steps: List<ChatBubble>,
    isResponding: Boolean,
    liveSummary: String? = null,
    collabLabel: String? = null,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
    onExpand: (ChatBubble) -> Unit = {},
    expandingHistoryIndex: Int = -1,
    onOpenThinking: (List<ChatBubble>) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpenThinking(steps) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloverPulseIndicator(
                modifier = Modifier.size(12.dp),
                tint = if (isResponding) CloverLeaf else CloverText3,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    liveSummary != null -> liveSummary
                    collabLabel != null -> collabLabel
                    steps.all { it.role == "compact" } && steps.any { it.streaming } -> "压缩中"
                    steps.all { it.role == "compact" } -> "上下文压缩"
                    steps.all(::isTurnStatusPlaceholder) -> "正在思考"
                    isResponding && steps.any { !it.toolDone && it.role == "tool" } -> "执行中"
                    else -> "思考了 ${steps.count { !isTurnStatusPlaceholder(it) }} 步"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = CloverText3,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "查看思考过程",
                tint = CloverText3,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ThinkingSheet(
    steps: List<ChatBubble>,
    collabLabel: String? = null,
    collabTasks: List<TaskSummary> = emptyList(),
    onOpenTask: (String) -> Unit = {},
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDismiss: () -> Unit,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
    onExpand: (ChatBubble) -> Unit = {},
    expandingHistoryIndex: Int = -1,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CloverBg) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "thinking-header") {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🧠", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        collabLabel ?: "思考了 ${steps.size} 步",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }
            }
            val delegates = collabTasks.filter { it.mode.equals("single", ignoreCase = true) && it.childCount == 0 }
            if (delegates.isNotEmpty()) {
                item(key = "subagent-flow") {
                    TaskFlowStrip(
                        children = delegates.map { TaskNode(it) },
                        mode = "single",
                        title = collabFoldLabel(delegates, false),
                        onOpenTask = { onOpenTask(it.id) },
                    )
                }
            }
            items(collabTasks.filter { it.childCount > 0 || it.mode.lowercase() in setOf("parallel", "pipeline", "debate", "auto", "autonomy_queue") }) { task ->
                Surface(
                    onClick = { onOpenTask(task.id) },
                    color = CloverSurface2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.description.ifBlank { task.id }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${task.mode} · ${task.completedChildren}/${task.childCount.coerceAtLeast(0)} · ${task.status}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CloverText3,
                        )
                    }
                }
            }
            items(steps) { step ->
                when {
                    step.role == "compact" -> CompactProgressRow(step.content)
                    step.role == "reasoning" || step.isIntermediate -> ReasoningPart(step, imageHeaders, imageBaseUrl)
                    else -> ToolPart(
                        step,
                        imageHeaders,
                        imageBaseUrl,
                        onDownload,
                        onQuote,
                        onCopy,
                        onExpand,
                        expanding = expandingHistoryIndex >= 0 && expandingHistoryIndex == step.historyIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactProgressRow(label: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(
            progress = { if (label.startsWith("正在压缩")) 0.45f else 1f },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)),
            color = CloverAccent,
            trackColor = CloverLine,
        )
    }
}

@Composable
private fun ReasoningPart(
    bubble: ChatBubble,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
) {
    if (bubble.content.isBlank()) return
    Column(Modifier.fillMaxWidth()) {
        bubble.reasoningRound?.takeIf { it > 1 }?.let { round ->
            Text("第 $round 轮", style = MaterialTheme.typography.labelSmall, color = CloverText3)
        }
        MarkdownText(markdown = bubble.content, imageHeaders = imageHeaders, imageBaseUrl = imageBaseUrl)
    }
}

@Composable
private fun ToolPart(
    bubble: ChatBubble,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    onQuote: (ChatBubble) -> Unit,
    onCopy: (ChatBubble) -> Unit,
    onExpand: (ChatBubble) -> Unit = {},
    expanding: Boolean = false,
) {
    var expanded by remember(bubble.id) { mutableStateOf(false) }
    var menuExpanded by remember(bubble.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    onLongClick = { menuExpanded = true },
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!bubble.toolDone && bubble.toolSuccess != false) {
                CloverPulseIndicator(Modifier.size(10.dp), CloverAccent)
            } else {
                Box(
                    Modifier.size(7.dp).clip(CircleShape).background(
                        if (bubble.toolSuccess == false) CloverError else CloverLeaf,
                    ),
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                bubble.toolName ?: "工具调用",
                style = MaterialTheme.typography.bodySmall,
                color = CloverText2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    !bubble.toolDone -> "运行中"
                    bubble.toolSuccess == false -> "失败"
                    else -> "完成"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (bubble.toolSuccess == false) CloverError else CloverText3,
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起工具详情" else "展开工具详情",
                tint = CloverText3,
                modifier = Modifier.padding(start = 2.dp).size(18.dp),
            )
        }
        MessageActionMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onQuote = { onQuote(bubble) },
            onCopy = { onCopy(bubble) },
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp)) {
            bubble.toolArgs?.takeIf { it.isNotBlank() }?.let {
                Text("输入", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                MonoBlock(it)
            }
            bubble.toolOutput?.takeIf { it.isNotBlank() }?.let {
                Text(if (bubble.toolSuccess == false) "错误" else "结果", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                MarkdownText(
                    markdown = truncateToolOutput(it),
                    color = CloverText2,
                    imageHeaders = imageHeaders,
                    imageBaseUrl = imageBaseUrl,
                )
                if (bubble.contentTruncated) {
                    FullTextButton(bubble, expanding, onExpand)
                }
            }
            bubble.attachments.forEach { media ->
                ChatMediaPreview(media, imageHeaders, imageBaseUrl, onDownload)
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        maxLines = 12,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Keep the existing compact tool preview while letting complete GFM tables render. */
private fun truncateToolOutput(text: String, maxLines: Int = 12): String {
    val lines = text.replace("\r\n", "\n").split('\n')
    if (lines.size <= maxLines) return text
    return lines.take(maxLines).joinToString("\n") + "\n…"
}

@Composable
private fun ComposerBar(
    state: AppUiState,
    vm: AppViewModel,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    showAttachmentOptions: Boolean,
    onToggleAttachment: () -> Unit,
    onCameraCapture: () -> Unit,
    onVoiceCapture: () -> Unit,
    onVisualPick: () -> Unit,
    onFilePick: () -> Unit,
    onRemoveMedia: (String) -> Unit,
    onRetryMedia: (String) -> Unit,
    onClearQuote: () -> Unit,
    isRecordingVoice: Boolean,
    recordingElapsedSec: Int,
    captureHint: String?,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
) {
    var showContextSheet by rememberSaveable { mutableStateOf(false) }
    val chatWorking = state.isResponding || state.bubbles.any { it.streaming }
    val activityLabel = composerActivityLabel(
        agentWorking = state.isResponding,
        commandExecuting = state.commandExecuting,
        streaming = state.bubbles.any { it.streaming },
    )
    val activityWorking = activityLabel != ComposerActivityLabel.Hidden
    val hasInput = state.composer.isNotBlank() || state.pendingMedia.isNotEmpty() || state.pendingQuote != null
    val isStopCommand = state.composer.trim().equals("/stop", ignoreCase = true)
    val shownActivity = activityStatusLine(state.activityLine)
    LaunchedEffect(showContextSheet) {
        if (showContextSheet) vm.refreshContextInspect()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        state.pendingQuote?.let { quote ->
            QuoteCard(
                quote = quote,
                compact = false,
                onClear = onClearQuote,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!isRecordingVoice && !activityWorking && !shownActivity.isNullOrBlank()) {
            Text(
                shownActivity,
                color = if (activityStatusFailed(state.activityLine, shownActivity)) CloverError else CloverText3,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.attachmentNotice?.let { notice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when {
                    notice.isError -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = CloverError, modifier = Modifier.size(15.dp))
                    notice.isComplete -> Icon(Icons.Outlined.Check, contentDescription = null, tint = CloverLeaf, modifier = Modifier.size(15.dp))
                    else -> CloverPulseIndicator(Modifier.size(14.dp), CloverAccent)
                }
                Text(
                    notice.text,
                    color = when {
                        notice.isError -> CloverError
                        notice.isComplete -> CloverAccent
                        else -> CloverText3
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(visible = showAttachmentOptions && !isRecordingVoice) {
            Column {
                AttachmentActionRow(
                    onCameraCapture = onCameraCapture,
                    onVoiceCapture = onVoiceCapture,
                    onVisualPick = onVisualPick,
                    onFilePick = onFilePick,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (isRecordingVoice) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CloverPulseIndicator(tint = CloverError)
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = CloverError, modifier = Modifier.size(18.dp))
                Text(
                    text = "录音中 ${formatElapsed(recordingElapsedSec)}",
                    color = CloverText2,
                    style = MaterialTheme.typography.bodyMedium,
                )
                VoiceWaveform(Modifier.weight(1f))
                TextButton(onClick = onCancelVoice) { Text("取消") }
                TextButton(onClick = onStopVoice) { Text("完成") }
            }
            Spacer(Modifier.height(8.dp))
        } else if (!captureHint.isNullOrBlank()) {
            Text(
                text = captureHint,
                color = CloverError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        if (state.pendingMedia.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("附件 ${state.pendingMedia.size}", style = MaterialTheme.typography.labelSmall, color = CloverText3)
                val failed = state.pendingMedia.count { it.error != null }
                val uploading = state.pendingMedia.count { it.descriptor == null && it.error == null }
                Text(
                    when {
                        failed > 0 -> "$failed 个失败"
                        uploading > 0 -> "$uploading 个上传中"
                        else -> "已就绪"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed > 0) CloverError else CloverText3,
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.pendingMedia.forEach { media ->
                    PendingMediaChip(media, onRemove = { onRemoveMedia(media.id) }, onRetry = { onRetryMedia(media.id) })
                }
            }
        }
        var showLuckyCommands by rememberSaveable { mutableStateOf(false) }
        if (state.outboundQueue.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.outboundQueue.forEach { item ->
                    OutboundQueueChip(item = item, onRetry = { vm.retryOutboundMessage(item.id) })
                }
            }
        }
        if (state.settings.connectionMode == "standalone") {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "独立聊天 · 约 ${state.standaloneTokens} token",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
                TextButton(onClick = vm::compactStandalone) { Text("压缩") }
            }
            state.standaloneNotice?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = CloverText2, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (activityWorking) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CloverPulseIndicator()
                StreamingDots()
                Text(
                    if (activityLabel == ComposerActivityLabel.Executing) "执行中" else "生成中",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
        }
        if (state.luckyPending.any { it.error != null } || state.luckyActive) {
            LuckyStatusLine(
                active = state.luckyActive,
                segments = state.luckySegments,
                pending = state.luckyPending,
                onRetry = vm::retryLuckySegment,
            )
        }
        Box(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CloverSurface.copy(alpha = state.settings.composerOpacity.coerceIn(0, 100) / 100f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, CloverLine.copy(alpha = .4f)),
            shadowElevation = 0.dp,
        ) {
        Row(
            Modifier
                .padding(start = 2.dp, end = 52.dp, top = 2.dp, bottom = 2.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleAttachment,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    if (showAttachmentOptions) Icons.Outlined.Close else Icons.Outlined.Add,
                    contentDescription = if (showAttachmentOptions) "关闭附件选项" else "添加附件",
                    tint = CloverText2,
                )
            }
            BasicTextField(
                value = state.composer,
                onValueChange = onChange,
                cursorBrush = SolidColor(CloverAccent),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 140.dp)
                    .padding(horizontal = 2.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (state.composer.isEmpty()) {
                        Text(
                            when {
                                state.luckyActive -> "继续添加，/lucky off 一次发送"
                                state.settings.connectionMode == "standalone" -> "直接问模型，/search 搜索"
                                else -> "问 LuckyAgent"
                            },
                            color = CloverText3,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            ContextUsageButton(
                state = state,
                onClick = { showContextSheet = true },
            )
            IconButton(
                onClick = onVoiceCapture,
                modifier = Modifier.size(42.dp),
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = "录音", tint = CloverText2)
            }
        }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showLuckyCommands) {
                LuckyClover(
                    modifier = Modifier.wrapContentSize(unbounded = true),
                    onCommand = { action ->
                        onChange(action)
                        onSend()
                        showLuckyCommands = false
                    },
                    onDismiss = { showLuckyCommands = false },
                )
            }
            // Same source of truth as the label above; recomputing the condition
            // here is how the text and the spinner drift apart.
            if (activityLabel == ComposerActivityLabel.Executing) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(CloverSurface2),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
                SendButton(
                    enabled = hasInput || chatWorking && isStopCommand,
                    contentDescription = if (isStopCommand) "发送 /stop" else "发送，长按打开 Lucky",
                    onClick = onSend,
                    onLongClick = { showLuckyCommands = !showLuckyCommands },
                )
            }
        }
        }
        if (showContextSheet) {
            ContextInspectSheet(
                state = state,
                onDismiss = { showContextSheet = false },
                onRetry = vm::refreshContextInspect,
            )
        }
    }
}

@Composable
private fun LuckyClover(
    modifier: Modifier = Modifier,
    onCommand: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val leaves = listOf(
        "/lucky on" to "开始" to Alignment.TopCenter,
        "/lucky status" to "状态" to Alignment.CenterStart,
        "/lucky off" to "提交" to Alignment.CenterEnd,
        "/lucky cancel" to "放弃" to Alignment.BottomCenter,
    )
    Box(modifier.size(176.dp)) {
        Box(Modifier.matchParentSize().clickable(onClick = onDismiss))
        leaves.forEach { (commandLabel, alignment) ->
            val (command, label) = commandLabel
            Surface(
                onClick = { onCommand(command) },
                modifier = Modifier.align(alignment),
                shape = CircleShape,
                color = CloverSurface.copy(alpha = .96f),
                border = BorderStroke(1.dp, CloverAccent.copy(alpha = .45f)),
                shadowElevation = 2.dp,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = CloverAccent,
                )
            }
        }
    }
}

@Composable
private fun LuckyStatusLine(
    active: Boolean,
    segments: Int,
    pending: List<com.luckyagent.android.ui.LuckyPendingSegment>,
    onRetry: (String) -> Unit,
) {
    val failed = pending.filter { it.error != null }
    val waiting = pending.count { it.error == null }
    val text = when {
        failed.isNotEmpty() -> "${failed.size} 段没送进"
        waiting > 0 -> "$waiting 段发送中"
        active -> "收集中 $segments 段"
        else -> return
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (failed.isNotEmpty()) CloverError else CloverText3, modifier = Modifier.weight(1f))
        failed.firstOrNull()?.let { segment ->
            TextButton(onClick = { onRetry(segment.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("重试", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
                .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) CloverAccent else CloverSurface2)
            .combinedClickable(
                enabled = true,
                onClick = { if (enabled) onClick() },
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Send,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else CloverText3,
        )
    }
}

@Composable
private fun LuckyCommandRow(
    active: Boolean,
    segments: Int,
    attachments: Int,
    pending: List<com.luckyagent.android.ui.LuckyPendingSegment>,
    onCommand: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val waiting = pending.count { it.error == null }
    val failed = pending.count { it.error != null }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val summary = when {
        failed > 0 -> "Lucky · $failed 段未送进"
        waiting > 0 -> "Lucky · $waiting 段发送中"
        active -> "Lucky · 收集中 $segments 段"
        else -> "Lucky"
    }
    val tint = when {
        failed > 0 -> CloverError
        active || waiting > 0 -> CloverAccent
        else -> CloverText3
    }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(999.dp),
                color = if (active || failed > 0) tint.copy(alpha = 0.12f) else CloverSurface2,
                border = BorderStroke(1.dp, if (active || failed > 0) tint.copy(alpha = 0.45f) else CloverLine),
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(summary, style = MaterialTheme.typography.labelSmall, color = tint)
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起 Lucky" else "展开 Lucky",
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (!expanded && active) {
                TextButton(
                    onClick = { onCommand("/lucky off") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("提交", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (pending.isNotEmpty()) {
                    pending.forEach { segment ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                segment.error ?: "发送中 · ${segment.preview}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (segment.error != null) CloverError else CloverText3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (segment.error != null) {
                                TextButton(
                                    onClick = { onRetry(segment.id) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                ) { Text("重试") }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "/lucky on" to "开始",
                        "/lucky off" to "提交",
                        "/lucky status" to "状态",
                        "/lucky cancel" to "放弃",
                    ).forEach { (command, label) ->
                        TextButton(
                            onClick = { onCommand(command) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboundQueueChip(
    item: OutboundQueueItem,
    onRetry: () -> Unit,
) {
    val (label, tint) = when (item.status) {
        OutboundQueueStatus.Queued -> "等待发送" to CloverText3
        OutboundQueueStatus.Sending -> "正在提交" to CloverAccent
        OutboundQueueStatus.Accepted -> "电脑已收下" to CloverAccent
        OutboundQueueStatus.ServerQueued -> "电脑已收下，等上一轮结束" to CloverWarning
        OutboundQueueStatus.Running -> "电脑正在处理" to CloverAccent
        OutboundQueueStatus.Failed -> "未送达" to CloverError
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = CloverSurface2,
        border = BorderStroke(1.dp, tint.copy(alpha = .4f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
            Text(
                item.preview,
                style = MaterialTheme.typography.labelSmall,
                color = CloverText2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            if (item.status == OutboundQueueStatus.Failed) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    }
}

private val ContextBucketOrder = listOf(
    "system" to "系统",
    "history" to "历史",
    "memory" to "记忆",
    "rag" to "检索",
    "tool_result" to "工具",
    "user" to "用户",
)

private val ContextBucketColors = listOf(
    Color(0xFF356F42),
    Color(0xFF3D6B8C),
    Color(0xFF8A6A2F),
    Color(0xFF6E5A8A),
    Color(0xFF8C4E3A),
    Color(0xFF4E7A55),
)

@Composable
private fun ContextUsageButton(
    state: AppUiState,
    onClick: () -> Unit,
) {
    val usage = state.contextInspect?.usage
    val ratio = ((usage?.ratio ?: 0.0).toFloat()).coerceIn(0f, 1.5f)
    val tint = when {
        state.contextInspectError != null -> CloverError
        ratio >= 0.9f -> CloverError
        ratio >= 0.75f -> CloverWarning
        else -> CloverAccent
    }
    val label = when {
        usage == null -> "—"
        else -> TokenFormat.compact(usage.totalTokens)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (state.contextInspectLoading && usage == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CloverAccent,
                )
            } else {
                ContextUsageRing(ratio = ratio.coerceAtMost(1f), color = tint)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun ContextUsageRing(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(18.dp)) {
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
        if (ratio > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
    }
}

@Composable
private fun ContextInspectSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val inspect = state.contextInspect
    val usage = inspect?.usage
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CloverBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前上下文", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("本地估算，不是供应商账单", style = MaterialTheme.typography.bodySmall, color = CloverText3)
                }
                IconButton(onClick = onRetry, enabled = !state.contextInspectLoading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新上下文", tint = CloverText2)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = CloverText2)
                }
            }
            if (state.contextInspectLoading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = CloverAccent,
                )
            }
            state.contextInspectError?.let { error ->
                Text(error, color = CloverError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (usage != null) {
                val ratioPct = (usage.ratio * 100).toInt().coerceAtLeast(0)
                Text(
                    "${TokenFormat.full(usage.totalTokens)} / ${TokenFormat.full(usage.availableTokens)} · $ratioPct%",
                    style = MaterialTheme.typography.titleMedium,
                    color = CloverText,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "剩余 ${TokenFormat.full(usage.headroomTokens)} · ${usage.messageCount} 条消息",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloverText3,
                )
                Spacer(Modifier.height(12.dp))
                ContextBucketOrder.forEachIndexed { index, (key, label) ->
                    ContextBucketRow(
                        label = label,
                        bucket = usage.buckets[key] ?: ContextBucketUsage(),
                        color = ContextBucketColors[index],
                        total = usage.totalTokens.coerceAtLeast(1),
                    )
                }
                if (inspect.sections.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("组成", style = MaterialTheme.typography.titleSmall, color = CloverText)
                    Spacer(Modifier.height(8.dp))
                    inspect.sections.forEach { section ->
                        ContextSectionRow(section)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ContextBucketRow(
    label: String,
    bucket: ContextBucketUsage,
    color: Color,
    total: Int,
) {
    val fraction = (bucket.tokens.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = CloverText)
            Text(
                "${TokenFormat.full(bucket.tokens)} · ${bucket.messages}",
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(CloverLine),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ContextSectionRow(section: ContextSection) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${section.index + 1}. ${contextSectionLabel(section.label)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = CloverText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                TokenFormat.compact(section.tokens),
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
            )
        }
        if (section.preview.isNotBlank()) {
            Text(
                section.preview,
                style = MaterialTheme.typography.labelSmall,
                color = CloverText3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun contextSectionLabel(label: String): String = when (label) {
    "system_prompt" -> "系统提示"
    "tool_catalog" -> "工具目录"
    "history" -> "历史"
    "compact_summary" -> "压缩摘要"
    "core_memory" -> "核心记忆"
    "working_memory" -> "工作记忆"
    "session_history_memory" -> "会话记忆"
    "recent_context" -> "近期上下文"
    "rag" -> "检索"
    "skill_route" -> "技能路由"
    "attachment" -> "附件"
    "tool_result" -> "工具结果"
    "user" -> "用户"
    else -> label.ifBlank { "其他" }
}

@Composable
private fun ModelSwitchButton(
    state: AppUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeChat = state.activeModels["chat"]
        ?: state.models.firstOrNull { it.kind.equals("chat", ignoreCase = true) && it.current }
    val label = activeChat?.displayName?.takeIf { it.isNotBlank() } ?: activeChat?.id ?: "模型"
    val switching = state.modelSwitchingKey?.startsWith("chat|") == true
    Row(
        modifier = modifier
            .widthIn(max = 180.dp)
            .heightIn(min = 48.dp)
            .clickable(enabled = !switching, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "切换模型" }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (switching) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = CloverAccent,
            )
        }
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = CloverText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = CloverText3,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ModelSwitchSheet(
    state: AppUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRetryEffort: () -> Unit,
    onSetEffort: (String) -> Unit,
    onSelect: (ModelRef) -> Unit,
) {
    val sections = remember(state.models) { modelSheetSections(state.models) }
    var showEffortOptions by rememberSaveable { mutableStateOf(false) }
    val effort = state.modelEffort
    val effortSupported = effort?.supported == true
    val effortOptions = effort?.options.orEmpty().filter { it.isNotBlank() }
    val effortLabel = when {
        state.modelEffortLoading && effort == null -> "Loading…"
        effort == null -> "Unavailable"
        effort != null && !effortSupported -> "Not supported"
        effort?.effort.isNullOrBlank() || effort?.effort.equals("auto", ignoreCase = true) -> "Automatic"
        else -> effort?.effort.orEmpty()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CloverBg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(48.dp))
                Text(
                    "选择功能模型",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = CloverText2)
                }
            }
            if (state.modelsLoading) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = CloverAccent,
                )
            }
            state.modelsError?.let { error ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = CloverError, modifier = Modifier.size(18.dp))
                    Text(
                        error,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = CloverError,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
            if (state.modelsLoaded && !state.modelsLoading && state.models.isEmpty() && state.modelsError == null) {
                Text(
                    "服务器未返回可用模型",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloverText3,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            sections.forEach { (kind, models) ->
                val active = state.activeModels[kind] ?: models.firstOrNull { it.current }
                Text(
                    modelKindLabel(kind),
                    style = MaterialTheme.typography.labelLarge,
                    color = CloverText2,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CloverSurface.copy(alpha = .72f),
                    border = BorderStroke(1.dp, CloverLine.copy(alpha = .7f)),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        models.forEach { model ->
                            ModelPickerCardRow(
                                model = model,
                                selected = active?.id == model.id || (active == null && model.current),
                                switching = state.modelSwitchingKey == "$kind|${model.id}",
                                enabled = state.modelSwitchingKey == null,
                                onClick = { onSelect(model) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ModelPickerLink(
                icon = Icons.Outlined.Schedule,
                title = "Effort",
                subtitle = effortLabel,
                enabled = effortSupported && effortOptions.isNotEmpty() && !state.modelEffortSaving,
                onClick = {
                    if (effortSupported && effortOptions.isNotEmpty() && !state.modelEffortSaving) {
                        showEffortOptions = !showEffortOptions
                    }
                },
            )
            if (state.modelEffortLoading && effort != null) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = CloverAccent,
                )
            }
            state.modelEffortError?.let { error ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = CloverError, modifier = Modifier.size(16.dp))
                    Text(error, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = CloverError, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onRetryEffort) { Text("Retry") }
                }
            }
            if (showEffortOptions && effortSupported && effortOptions.isNotEmpty()) {
                Surface(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = CloverSurface.copy(alpha = .72f),
                    border = BorderStroke(1.dp, CloverLine.copy(alpha = .7f)),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        effortOptions.forEach { option ->
                            val selected = option.equals(effort?.effort, ignoreCase = true)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.modelEffortSaving && !selected) {
                                        onSetEffort(option)
                                        showEffortOptions = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(option, style = MaterialTheme.typography.bodyMedium, color = CloverText)
                                    if (selected) Text("当前选择", style = MaterialTheme.typography.labelSmall, color = CloverAccent)
                                }
                                if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = CloverAccent, modifier = Modifier.size(18.dp))
                                if (state.modelEffortSaving && selected) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = CloverAccent)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ModelPickerLink(
                icon = Icons.Outlined.MoreHoriz,
                title = "More models",
                subtitle = null,
                onClick = onRetry,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ModelPickerCardRow(
    model: ModelRef,
    selected: Boolean,
    switching: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CloverSurface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (model.provider ?: model.id).trim().firstOrNull()?.uppercase() ?: "M",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CloverText2,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                model.displayName?.takeIf { it.isNotBlank() } ?: model.id,
                style = MaterialTheme.typography.bodyLarge,
                color = CloverText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ModelCapabilityChip("GF")
                if (model.capabilities?.toString()?.contains("vision", ignoreCase = true) == true ||
                    model.capabilities?.toString()?.contains("image", ignoreCase = true) == true ||
                    model.capabilities?.toString()?.contains("multimodal", ignoreCase = true) == true
                ) {
                    ModelCapabilityChip("多模态")
                }
            }
        }
        when {
            switching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CloverAccent)
            selected -> Icon(Icons.Outlined.Check, contentDescription = "当前模型", tint = CloverText, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ModelCapabilityChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = CloverSurface2,
        border = BorderStroke(1.dp, CloverLine.copy(alpha = .7f)),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText2, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

@Composable
private fun ModelPickerLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CloverSurface.copy(alpha = .72f),
        border = BorderStroke(1.dp, CloverLine.copy(alpha = .7f)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = CloverText, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CloverText2) }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = CloverText2)
        }
    }
}

@Composable
private fun ModelKindSection(
    kind: String,
    models: List<ModelRef>,
    active: ModelRef?,
    switchingKey: String?,
    onSelect: (ModelRef) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(modelKindLabel(kind), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                active?.id ?: "未配置",
                style = MaterialTheme.typography.labelSmall,
                color = if (active == null) CloverText3 else CloverAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (models.isEmpty()) {
            Text(
                "暂无可用模型",
                style = MaterialTheme.typography.bodySmall,
                color = CloverText3,
                modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
            )
        } else {
            models.forEach { model ->
                val isCurrent = active?.id == model.id || (active == null && model.current)
                ModelOptionRow(
                    model = model,
                    isCurrent = isCurrent,
                    enabled = switchingKey == null && !isCurrent,
                    switching = switchingKey == "${kind.lowercase()}|${model.id}",
                    onClick = { onSelect(model) },
                )
            }
        }
    }
}

@Composable
private fun ModelOptionRow(
    model: ModelRef,
    isCurrent: Boolean,
    enabled: Boolean,
    switching: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        when {
            switching -> CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = CloverAccent)
            isCurrent -> Icon(Icons.Outlined.Check, contentDescription = "当前", tint = CloverAccent, modifier = Modifier.size(17.dp))
            else -> Spacer(Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                model.displayName?.takeIf { it.isNotBlank() } ?: model.id,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled || isCurrent) CloverText else CloverText3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val provider = model.provider?.takeIf { it.isNotBlank() }
            if (provider != null && provider != model.id) {
                Text(provider, style = MaterialTheme.typography.labelSmall, color = CloverText3, maxLines = 1)
            }
        }
        if (isCurrent) Text("当前", style = MaterialTheme.typography.labelSmall, color = CloverAccent)
    }
}

@Composable
private fun AttachmentActionRow(
    onCameraCapture: () -> Unit,
    onVoiceCapture: () -> Unit,
    onVisualPick: () -> Unit,
    onFilePick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AttachmentAction(Icons.Outlined.CameraAlt, "拍照", onCameraCapture)
        AttachmentAction(Icons.Outlined.Mic, "录音", onVoiceCapture)
        AttachmentAction(Icons.Outlined.PhotoLibrary, "相册", onVisualPick)
        AttachmentAction(Icons.Outlined.AttachFile, "文件", onFilePick)
    }
}

@Composable
private fun AttachmentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(76.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = CloverText2, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CloverText3)
    }
}

@Composable
private fun CloverPulseIndicator(
    modifier: Modifier = Modifier,
    tint: Color = CloverLeaf,
) {
    val transition = rememberInfiniteTransition(label = "clover-pulse")
    val pulse = transition.animateFloat(
        initialValue = .65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "clover-alpha",
    ).value
    Canvas(modifier.size(14.dp)) {
        val radius = size.minDimension * .27f
        val x1 = size.width * .31f
        val x2 = size.width * .69f
        val y1 = size.height * .31f
        val y2 = size.height * .69f
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x1, y1))
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x2, y1))
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x1, y2))
        drawCircle(tint.copy(alpha = pulse), radius, androidx.compose.ui.geometry.Offset(x2, y2))
    }
}

@Composable
private fun StreamingDots() {
    val transition = rememberInfiniteTransition(label = "streaming-dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val alpha = transition.animateFloat(
                initialValue = .22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1200
                        .22f at 0
                        .22f at (index * 150)
                        1f at (index * 150 + 180)
                        .22f at (index * 150 + 430)
                        .22f at 1200
                    },
                    RepeatMode.Restart,
                ),
                label = "dot-$index",
            ).value
            Box(Modifier.size(4.dp).clip(CircleShape).background(CloverLeaf.copy(alpha = alpha)))
        }
    }
}

@Composable
private fun VoiceWaveform(modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { index ->
            val transition = rememberInfiniteTransition(label = "voice-wave-$index")
            val level = transition.animateFloat(
                initialValue = .25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        .25f at 0
                        (if (index % 3 == 0) 1f else .55f) at 260
                        .25f at 520
                        .25f at 900
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "voice-bar-$index",
            ).value
            Box(
                Modifier
                    .width(2.dp)
                    .height((5f + level * 13f).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CloverLeaf),
            )
        }
    }
}

@Composable
internal fun ChatMediaPreview(
    media: ChatMedia,
    imageHeaders: Map<String, String>,
    imageBaseUrl: String,
    onDownload: (ChatMedia) -> Unit,
    compact: Boolean = false,
) {
    val descriptor = media.descriptor
    val context = LocalContext.current
    val remoteUrl = descriptor.fileUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { resolveMediaUrl(it, imageBaseUrl) }
        ?: descriptor.filePath?.let { artifactDownloadUrl(it, imageBaseUrl) }
    val sourceUri = media.localUri?.let(Uri::parse) ?: remoteUrl?.let(Uri::parse)
    val kind = attachmentKind(descriptor)
    val imageModel = remoteUrl?.let { url ->
        remember(url, imageHeaders, imageBaseUrl) {
            ImageRequest.Builder(context)
                .data(url)
                .apply {
                    if (sameMediaOrigin(url, imageBaseUrl)) {
                        imageHeaders.forEach { (name, value) -> addHeader(name, value) }
                    }
                }
                .build()
        }
    }
    var showImageViewer by remember(sourceUri?.toString(), kind) { mutableStateOf(false) }
    var showHtmlViewer by remember(sourceUri?.toString(), kind) { mutableStateOf(false) }
    var playbackError by remember(sourceUri?.toString(), descriptor.mimeType, kind) { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    val player = rememberAttachmentPlayer(
        context = context,
        sourceUri = sourceUri,
        mimeType = descriptor.mimeType,
        headers = if (remoteUrl != null && sameMediaOrigin(remoteUrl, imageBaseUrl)) imageHeaders else emptyMap(),
        enabled = (kind == "audio" || kind == "video") && sourceUri != null && !playbackError,
    )
    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    durationMs = player.duration.takeIf { it > 0 } ?: 0L
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError = true
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    LaunchedEffect(player, sourceUri, descriptor.mimeType) {
        if (player == null || sourceUri == null) return@LaunchedEffect
        val item = MediaItem.Builder()
            .setUri(sourceUri)
            .apply {
                descriptor.mimeType
                    ?.takeIf { it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true) }
                    ?.let(::setMimeType)
            }
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.takeIf { it > 0 } ?: durationMs
                isPlaying = player.isPlaying
            }
            kotlinx.coroutines.delay(250)
        }
    }

    when {
        kind == "image" && sourceUri != null && imageModel != null -> {
            ImageAttachmentCard(media, imageModel, compact, { showImageViewer = true }, onDownload)
        }
        kind == "image" && sourceUri != null && media.localUri != null -> {
            ImageAttachmentCard(media, sourceUri, compact, { showImageViewer = true }, onDownload)
        }
        kind == "audio" && player != null -> {
            AudioAttachmentCard(media, player, isPlaying, positionMs, durationMs, onDownload)
        }
        kind == "video" && player != null -> {
            VideoAttachmentCard(media, player, onDownload)
        }
        kind == "html" -> Column {
            AttachmentFileCard(media, kind, onDownload)
            TextButton(enabled = sourceUri != null, onClick = { showHtmlViewer = true }) { Text("预览 HTML") }
        }
        else -> AttachmentFileCard(media, kind, onDownload)
    }

    if (showImageViewer && sourceUri != null) {
        FullscreenImagePreview(
            model = imageModel ?: sourceUri,
            name = descriptor.fileName ?: "图片附件",
            onDismiss = { showImageViewer = false },
            onDownload = if (!descriptor.fileUrl.isNullOrBlank() || !descriptor.filePath.isNullOrBlank()) {
                {
                    onDownload(media)
                    showImageViewer = false
                }
            } else {
                null
            },
        )
    }
    if (showHtmlViewer && sourceUri != null) {
        HtmlPreviewDialog(
            uri = sourceUri.toString(),
            title = descriptor.fileName ?: "HTML 预览",
            baseUrl = imageBaseUrl,
            headers = imageHeaders,
            onDownload = { onDownload(media) },
            onDismiss = { showHtmlViewer = false },
        )
    }
}

@Composable
private fun ImageAttachmentCard(
    media: ChatMedia,
    model: Any,
    compact: Boolean,
    onOpen: () -> Unit,
    onDownload: (ChatMedia) -> Unit,
) {
    val descriptor = media.descriptor
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.aspectRatio(1f) else Modifier.heightIn(min = 180.dp, max = 300.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable(onClick = onOpen),
        ) {
            AsyncImage(
                model = model,
                contentDescription = descriptor.fileName ?: "图片附件",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    descriptor.fileName ?: "图片附件",
                    color = CloverText3,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                descriptor.fileSize?.takeIf { it > 0 }?.let {
                    Text(formatMediaSize(it), color = CloverText3, style = MaterialTheme.typography.labelSmall)
                }
            }
            AttachmentDownloadButton(media, onDownload, tint = CloverText2)
        }
    }
}

@Composable
private fun AudioAttachmentCard(
    media: ChatMedia,
    player: ExoPlayer,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onDownload: (ChatMedia) -> Unit,
) {
    val descriptor = media.descriptor
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CloverSurface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CloverAccent),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "暂停音频" else "播放音频",
                        tint = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    descriptor.fileName ?: "音频附件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CloverText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                )
            }
            AttachmentDownloadButton(media, onDownload)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
            color = CloverAccent,
            trackColor = CloverLine,
        )
    }
}

@Composable
private fun VideoAttachmentCard(
    media: ChatMedia,
    player: ExoPlayer,
    onDownload: (ChatMedia) -> Unit,
) {
    val descriptor = media.descriptor
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CloverSurface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 190.dp, max = 260.dp)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { PlayerView(it) },
                update = { view ->
                    view.player = player
                    view.useController = true
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .38f))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    descriptor.fileName ?: "视频附件",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AttachmentDownloadButton(media, onDownload, tint = Color.White)
            }
        }
        Text(
            text = "视频 · ${descriptor.fileName ?: "附件"}",
            style = MaterialTheme.typography.labelMedium,
            color = CloverText2,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberAttachmentPlayer(
    context: Context,
    sourceUri: Uri?,
    mimeType: String?,
    headers: Map<String, String>,
    enabled: Boolean,
): ExoPlayer? {
    if (!enabled || sourceUri == null) return null
    val player = remember(sourceUri.toString(), mimeType, headers) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@Composable
private fun FullscreenImagePreview(
    model: Any,
    name: String,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = model,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭预览", tint = Color.White)
            }
            if (onDownload != null) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = "下载附件", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AttachmentDownloadButton(media: ChatMedia, onDownload: (ChatMedia) -> Unit, tint: Color = CloverText2) {
    if (!media.descriptor.fileUrl.isNullOrBlank() || !media.descriptor.filePath.isNullOrBlank()) {
        IconButton(onClick = { onDownload(media) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = "下载附件", tint = tint)
        }
    }
}

@Composable
private fun AttachmentFileCard(media: ChatMedia, kind: String, onDownload: (ChatMedia) -> Unit) {
    val descriptor = media.descriptor
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CloverSurface2)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = descriptor.fileName ?: "附件",
            style = MaterialTheme.typography.bodyMedium,
            color = CloverText2,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(mediaTypeLabel(kind), style = MaterialTheme.typography.labelSmall, color = CloverText3)
            AttachmentDownloadButton(media, onDownload)
        }
    }
}

private fun formatElapsed(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val min = safe / 60
    val sec = safe % 60
    return "%d:%02d".format(min, sec)
}

private fun formatMediaSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun attachmentKind(descriptor: com.luckyagent.android.data.api.MediaAttachment): String {
    val type = descriptor.type.lowercase()
    val mimeType = descriptor.mimeType.orEmpty().lowercase()
    return when {
        isHtmlAttachment(descriptor.mimeType, descriptor.fileName ?: descriptor.fileUrl ?: descriptor.filePath) -> "html"
        type == "image" || type.startsWith("image/") || mimeType.startsWith("image/") -> "image"
        type == "audio" || type.startsWith("audio/") || mimeType.startsWith("audio/") -> "audio"
        type == "video" || type.startsWith("video/") || mimeType.startsWith("video/") -> "video"
        else -> "document"
    }
}

private fun resolveMediaUrl(url: String, baseUrl: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    if (baseUrl.isBlank()) return url
    return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
}

private fun sameMediaOrigin(url: String, baseUrl: String): Boolean {
    val target = Uri.parse(url)
    val base = Uri.parse(baseUrl)
    val targetPort = target.port.takeIf { it >= 0 } ?: if (target.scheme.equals("https", true)) 443 else 80
    val basePort = base.port.takeIf { it >= 0 } ?: if (base.scheme.equals("https", true)) 443 else 80
    return target.scheme == base.scheme && target.host.equals(base.host, true) && targetPort == basePort
}

@Composable
private fun PendingMediaChip(media: PendingMedia, onRemove: () -> Unit, onRetry: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 230.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (media.mimeType.startsWith("image/")) {
            AsyncImage(
                model = Uri.parse(media.uri),
                contentDescription = media.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(7.dp))
        } else {
            Icon(
                if (media.mimeType.startsWith("audio/")) Icons.Outlined.AudioFile else Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = CloverText2,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(media.fileName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                media.error ?: if (media.descriptor == null) "上传中…" else "已就绪",
                style = MaterialTheme.typography.labelSmall,
                color = if (media.error != null) CloverError else CloverText3,
                maxLines = 1,
            )
        }
        when {
            media.error != null -> IconButton(onClick = onRetry, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = "重试上传", tint = CloverError, modifier = Modifier.size(17.dp))
            }
            media.descriptor == null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = CloverAccent)
            else -> Icon(Icons.Outlined.Check, contentDescription = "上传完成", tint = CloverLeaf, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "移除附件", modifier = Modifier.size(16.dp), tint = CloverText3)
        }
    }
}

private fun mediaTypeLabel(type: String): String = when (type.lowercase()) {
    "image" -> "图片"
    "video" -> "视频"
    "audio" -> "音频"
    "html" -> "HTML"
    else -> "文件"
}

@Composable
private fun SessionWorkingBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CloverAccent.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(CloverAccent),
        )
        Text(
            "Working",
            style = MaterialTheme.typography.labelSmall,
            color = CloverAccent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SessionDrawer(
    state: AppUiState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRename: (RuntimeSession) -> Unit,
    onProject: (RuntimeSession) -> Unit = {},
    onPin: (RuntimeSession) -> Unit = {},
    onDelete: (RuntimeSession) -> Unit = {},
    onLongPress: (RuntimeSession) -> Unit = {},
    onRefresh: () -> Unit,
    showClose: Boolean,
) {
    val openNavigation = LocalOpenNavigationDrawer.current
    val query = state.sessionQuery.trim()
    val sessions = state.sessions
    var collapsedGroups by remember { mutableStateOf(setOf<String>()) }
    val showInitialLoading = state.sessionsLoading && sessions.isEmpty() && state.sessionsError == null
    val showEmpty = !state.sessionsLoading && state.sessionsError == null && sessions.isEmpty()

    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "会话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            openNavigation?.let { onOpen ->
                IconButton(onClick = onOpen) {
                    Icon(Icons.Outlined.Apps, contentDescription = "这台电脑", tint = CloverText2)
                }
            }
            IconButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "New session")
            }
            if (showClose) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
        }
        HorizontalDivider(color = CloverLine)
        BasicTextField(
            value = state.sessionQuery,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CloverSurface)
                .border(1.dp, CloverLine, RoundedCornerShape(10.dp))
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "搜索会话" },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = CloverText),
            cursorBrush = SolidColor(CloverAccent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onRefresh() }),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = CloverText2,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (state.sessionQuery.isEmpty()) {
                            Text("搜索会话", style = MaterialTheme.typography.bodyMedium, color = CloverText3)
                        }
                        innerTextField()
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (state.sessionsLoading && sessions.isNotEmpty()) {
                            CloverMark(size = 18.dp, contentDescription = StartupPhase.Sessions)
                        } else {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh sessions")
                            }
                        }
                    }
                }
            },
        )

        if (state.showingOfflineCache) {
            Text(
                "离线，显示上次内容",
                style = MaterialTheme.typography.bodySmall,
                color = CloverText3,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        if (state.sessionsError != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    state.sessionsError ?: "Failed to load sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onRefresh) {
                    Text("Retry")
                }
            }
        }

        when {
            showInitialLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StartupWait(
                        phase = StartupPhase.Sessions,
                        next = StartupPhase.SessionsNext,
                        onTimedOut = {
                            TextButton(onClick = onRefresh) { Text("重试") }
                        },
                    )
                }
            }

            showEmpty -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (query.isNotEmpty()) {
                                "没有匹配的会话"
                            } else {
                                "还没有会话"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = CloverText2,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isNotEmpty()) {
                                "换个词，或清空搜索。"
                            } else {
                                "新建一个会话后再开始聊天。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = CloverText3,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (query.isNotEmpty()) {
                            TextButton(onClick = {
                                onQueryChange("")
                                onRefresh()
                            }) {
                                Text("Clear search")
                            }
                        } else {
                            TextButton(onClick = onCreate) {
                                Text("New session")
                            }
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (state.sessionsLoading) {
                        item(key = "sessions-loading-bar") {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                color = CloverAccent,
                            )
                        }
                    }
                    sessionGroups(sessions).forEach { group ->
                        val collapsed = group.name in collapsedGroups
                        item(key = "group-${group.name}") {
                            SessionGroupHeader(
                                name = group.name,
                                count = group.sessions.size,
                                expanded = !collapsed,
                                onToggle = {
                                    collapsedGroups = if (collapsed) collapsedGroups - group.name else collapsedGroups + group.name
                                },
                            )
                        }
                        if (!collapsed) {
                            items(group.sessions, key = { "session-${it.id}" }) { session ->
                                SessionDrawerRow(
                                    session = session,
                                    selected = session.id == state.settings.sessionId,
                                    working = session.id in state.workingSessionIds,
                                    nested = true,
                                    onSelect = { onSelect(session.id) },
                                    onRename = { onRename(session) },
                                    onProject = { onProject(session) },
                                    onPin = { onPin(session) },
                                    onDelete = { onDelete(session) },
                                    onLongPress = { onLongPress(session) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionGroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .heightIn(min = 32.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Outlined.ExpandMore else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = if (expanded) "收起分组" else "展开分组",
            tint = CloverText2,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = CloverAccent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = CloverText,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = CloverText3,
        )
    }
}

@Composable
private fun SessionDrawerRow(
    session: RuntimeSession,
    selected: Boolean,
    working: Boolean,
    nested: Boolean = false,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onProject: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    val metadata = buildList {
        (session.updatedAt ?: session.createdAt)?.let { add(formatMessageTime(it)) }
        session.messageCount?.let { add("$it 条消息") }
    }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (nested) 10.dp else 0.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CloverUserBubble else CloverSurface)
            .border(
                1.dp,
                if (selected) CloverAccent else if (working) CloverAccent.copy(alpha = 0.45f) else CloverLine,
                RoundedCornerShape(8.dp),
            )
            .combinedClickable(onClick = onSelect, onLongClick = onLongPress)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = "已置顶",
                        tint = CloverAccent,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(14.dp),
                    )
                }
                Text(
                    session.title?.takeIf { it.isNotBlank() } ?: session.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (working) {
                    Spacer(Modifier.width(6.dp))
                    SessionWorkingBadge()
                }
            }
            if (metadata.isNotEmpty()) {
                Text(
                    metadata,
                    style = MaterialTheme.typography.labelSmall,
                    color = CloverText3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = "管理会话",
                    tint = CloverText2,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (session.pinned) "取消置顶" else "置顶") },
                    onClick = {
                        menuOpen = false
                        onPin()
                    },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                )
                if (!isFixedRoleSession(session)) {
                    DropdownMenuItem(
                        text = { Text("项目分类") },
                        onClick = {
                            menuOpen = false
                            onProject()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("修改名称") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = CloverError) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = CloverError) },
                )
            }
        }
    }
}
