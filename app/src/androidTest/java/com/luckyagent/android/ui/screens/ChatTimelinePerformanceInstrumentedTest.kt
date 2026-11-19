package com.luckyagent.android.ui.screens

import android.app.Instrumentation
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.luckyagent.android.perf.PerfProbe
import com.luckyagent.android.ui.ChatBubble
import com.luckyagent.android.ui.components.MarkdownParseCache
import com.luckyagent.android.ui.util.MessageRow
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production chat rows/effects in a bounded fixture; metrics are observations, not a device SLA. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ChatTimelinePerformanceInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun longHistoryScrollKeepsTheParentStableAndReleasesOffscreenWebViews() {
        val fixture = TimelineFixture(follow = false)
        val probe = FrameIntervalProbe(instrumentation)
        val previousProbeState = PerfProbe.shared.enabled
        try {
            showTimeline(fixture)
            compose.waitUntil(15_000) { webViewCount(fixture.hostView) == 1 }
            // The deliberately composed prefetch row is never a visible list
            // key. It must not create a second WebView even before scrolling.
            assertEquals(1, webViewCount(fixture.hostView))
            compose.onNodeWithTag("performance-chat-list").performScrollToIndex(24)
            compose.waitUntil(5_000) { webViewCount(fixture.hostView) == 0 }
            compose.waitForIdle()
            val scrollStartIndex = fixture.listState.firstVisibleItemIndex
            val parentBefore = fixture.parentCompositions.get()
            PerfProbe.shared.clear()
            PerfProbe.shared.enabled = true

            probe.start(2_000)
            repeat(3) {
                compose.onNodeWithTag("performance-chat-list").performTouchInput {
                    swipeUp(durationMillis = 250)
                }
            }
            compose.waitForIdle()
            val indexAfterUpwardGestures = fixture.listState.firstVisibleItemIndex
            repeat(2) {
                compose.onNodeWithTag("performance-chat-list").performTouchInput {
                    swipeDown(durationMillis = 250)
                }
            }
            compose.waitForIdle()
            val frames = probe.stop()
            val parentRecompositions = fixture.parentCompositions.get() - parentBefore
            val parseCalls = markdownParseCalls()
            val offscreenWebViews = webViewCount(fixture.hostView)
            writeMetrics("chat-scroll-emulator-metrics.json", frames, JSONObject()
                .put("historyMessages", fixture.history.size)
                .put("cacheState", "warm completed-message cache")
                .put("scrollStartIndex", scrollStartIndex)
                .put("indexAfterUpwardGestures", indexAfterUpwardGestures)
                .put("parentRecompositions", parentRecompositions)
                .put("markdownParseCallsDuringScroll", parseCalls)
                .put("nativeWebViewsAfterScroll", offscreenWebViews))

            assertTrue("No Choreographer intervals captured", frames.isNotEmpty())
            assertTrue("The real gestures did not advance through the chat rows", indexAfterUpwardGestures > scrollStartIndex)
            assertEquals("Row/date scroll observations recomposed the parent chat", 0, parentRecompositions)
            assertEquals("Warm history was parsed again while scrolling", 0, parseCalls)
            assertEquals("An offscreen or prefetch-only HTML row kept a WebView", 0, offscreenWebViews)
            compose.runOnIdle { assertTrue(fixture.listState.firstVisibleItemIndex > 0) }
        } finally {
            probe.stop()
            PerfProbe.shared.enabled = previousProbeState
            PerfProbe.shared.clear()
        }
    }

    @Test fun streamingGrowthFollowsTheTailAndSettlesWithoutAHeightFeedbackLoop() {
        val fixture = TimelineFixture(follow = true)
        val probe = FrameIntervalProbe(instrumentation)
        val previousProbeState = PerfProbe.shared.enabled
        try {
            showTimeline(fixture)
            awaitTail(fixture)
            compose.waitUntil(5_000) { fixture.liveHeight.get() > 0 && webViewCount(fixture.hostView) == 0 }
            val parentBefore = fixture.parentCompositions.get()
            val initialHeight = fixture.liveHeight.get()
            fixture.liveMeasureHeights.clear()
            PerfProbe.shared.clear()
            PerfProbe.shared.enabled = true

            var body = "Streaming performance fixture"
            probe.start(2_000)
            repeat(20) { index ->
                body += "\nLive line $index grows the actual assistant bubble."
                val content = body
                instrumentation.runOnMainSync {
                    fixture.liveBubble = fixture.liveBubble.copy(content = content)
                }
                // Keep appending at the client's 40 ms stream cadence without
                // waiting for each 180 ms height transition to finish first.
                compose.mainClock.advanceTimeBy(40)
                SystemClock.sleep(40)
            }
            compose.mainClock.advanceTimeBy(1_000)
            awaitTail(fixture)
            // Being at the tail can be true between animation frames. Finish
            // pending transitions before using this height as a settled baseline.
            compose.waitForIdle()
            val parentRecompositions = fixture.parentCompositions.get() - parentBefore
            val streamParseCalls = markdownParseCalls()
            val settledHeight = fixture.liveHeight.get()
            val settledMeasurements = fixture.liveMeasureHeights.size
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            val extraMeasurements = fixture.liveMeasureHeights.size - settledMeasurements
            val frames = probe.stop()

            assertTrue("The real streaming bubble did not grow", settledHeight > initialHeight)
            assertEquals("A settled content height kept triggering measurement changes", 0, extraMeasurements)
            assertEquals(settledHeight, fixture.liveHeight.get())
            assertEquals("Streaming growth recomposed the parent chat", 0, parentRecompositions)
            assertEquals("Streaming appends reparsed Markdown", 0, streamParseCalls)
            compose.onNodeWithTag("performance-chat-tail").assertIsDisplayed()

            compose.runOnIdle { fixture.liveBubble = fixture.liveBubble.copy(streaming = false) }
            compose.waitUntil(5_000) { markdownParseCalls() >= 1 }
            compose.waitForIdle()
            val completedParseCalls = markdownParseCalls()
            writeMetrics("chat-stream-emulator-metrics.json", frames, JSONObject()
                .put("historyMessages", fixture.history.size)
                .put("streamChunks", 20)
                .put("streamCadenceMs", 40)
                .put("parentRecompositions", parentRecompositions)
                .put("markdownParseCallsDuringStream", streamParseCalls)
                .put("markdownParseCallsAfterCompletion", completedParseCalls)
                .put("initialBubbleHeightPx", initialHeight)
                .put("settledBubbleHeightPx", settledHeight)
                .put("growthMeasurements", settledMeasurements)
                .put("extraMeasurementsAfterSettling", extraMeasurements)
                .put("nativeWebViewsAtTail", webViewCount(fixture.hostView)))
            assertTrue("No Choreographer intervals captured", frames.isNotEmpty())
            assertEquals("One completed body should need one Markdown parse", 1, completedParseCalls)
        } finally {
            probe.stop()
            PerfProbe.shared.enabled = previousProbeState
            PerfProbe.shared.clear()
        }
    }

    private fun showTimeline(fixture: TimelineFixture) {
        assertTrue("The warm-cache fixture must fit before testing repeated parses",
            fixture.history.size + 1 <= MarkdownParseCache.MAX_ENTRIES)
        MarkdownParseCache.clearForTest()
        // A warm-scroll regression isolates repeated work and surface lifetime
        // from first-time parsing; cold-start cost is outside these metrics.
        fixture.history.forEach { MarkdownParseCache.getOrParse(it.content, streaming = false) }
        MarkdownParseCache.getOrParse(PREFETCH_HTML, streaming = false)
        compose.setContent { MaterialTheme { ActualChatRows(fixture) } }
    }

    @Composable
    private fun ActualChatRows(fixture: TimelineFixture) {
        val listState = rememberLazyListState()
        val rows = remember(fixture) { fixture.history.map { MessageRow(it.id) } + MessageRow("live-bubble") }
        val dates = remember(rows) {
            rows.mapIndexed { index, row -> row.key to LocalDate.of(2026, 11, 1).plusDays((index / 12).toLong()) }.toMap()
        }
        val host = LocalView.current.rootView
        SideEffect {
            fixture.listState = listState
            fixture.hostView = host
            fixture.parentCompositions.incrementAndGet()
        }
        UserChatScrollEffect(listState) { fixture.followLatest = it }
        FollowLatestEffect(listState, "performance-session", fixture.history.size + 2, fixture.followLatest)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(420.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(420.dp).testTag("performance-chat-list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(fixture.history, key = { it.id }) { bubble -> RealBubble(listState, bubble, fixture) }
                    item(key = "live-bubble") { RealBubble(listState, fixture.liveBubble, fixture) }
                    item(key = "chat-tail") { Spacer(Modifier.height(1.dp).testTag("performance-chat-tail")) }
                }
                ChatFloatingDate(listState, rows, dates, Modifier.align(Alignment.TopCenter))
            }
            // Always composed, but never a visible LazyColumn key. This makes
            // prefetch suppression testable even if LazyColumn disposes old rows.
            Box(Modifier.fillMaxWidth().height(1.dp)) {
                RealBubble(listState, ChatBubble("prefetch-only", "assistant", PREFETCH_HTML), fixture)
            }
        }
    }

    @Composable
    private fun RealBubble(listState: LazyListState, bubble: ChatBubble, fixture: TimelineFixture) {
        Box(Modifier.fillMaxWidth().onSizeChanged { size ->
            if (bubble.id == "live-bubble") {
                fixture.liveHeight.set(size.height)
                fixture.liveMeasureHeights += size.height
            }
        }) {
            BubbleRow(
                bubble = bubble, avatarFile = "", bubbleOpacity = 100,
                imageHeaders = emptyMap(), imageBaseUrl = "",
                surfaceActive = bubbleSurfaceActive(listState, bubble.id),
                onDownload = {}, onQuote = {}, onCopy = {},
                growing = bubble.streaming,
                dragging = listState.isScrollInProgress && !fixture.followLatest,
                showAvatar = false,
            )
        }
    }

    private fun awaitTail(fixture: TimelineFixture) {
        compose.waitUntil(5_000) {
            fixture.listState.layoutInfo.totalItemsCount == fixture.history.size + 2 &&
                !fixture.listState.isScrollInProgress && !fixture.listState.canScrollForward
        }
    }

    private fun markdownParseCalls(): Int = PerfProbe.shared.snapshot().count { it.section == "markdown.parse" }

    private fun webViewCount(root: View): Int {
        var count = 0
        instrumentation.runOnMainSync {
            fun visit(view: View) {
                if (view is WebView) count += 1
                if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
            }
            visit(root)
        }
        return count
    }

    private fun writeMetrics(name: String, intervals: List<Double>, extra: JSONObject) {
        val sorted = intervals.sorted()
        fun percentile(fraction: Double): Double = if (sorted.isEmpty()) 0.0 else
            sorted[((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.lastIndex)]
        val emulator = Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk", ignoreCase = true) ||
            Build.HARDWARE in setOf("ranchu", "goldfish")
        extra.put("environment", if (emulator) "Android emulator" else "Android device")
            .put("sdkInt", Build.VERSION.SDK_INT).put("device", Build.MODEL)
            .put("fixture", "production BubbleRow, surface policy, scrolling effects and floating date; not a full-server workload")
            .put("metric", "Choreographer callback intervals, including idle intervals; not a physical-device FPS guarantee")
            .put("frameCount", intervals.size).put("p50Ms", percentile(0.50)).put("p95Ms", percentile(0.95))
            .put("maxMs", sorted.lastOrNull() ?: 0.0)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File)
            ?: requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        output.mkdirs()
        File(output, name).writeText(extra.toString(2))
        Log.i("ChatTimelinePerformance", extra.toString())
    }

    private class TimelineFixture(follow: Boolean) {
        val history = List(120) { index ->
            val body = when {
                index == 0 -> "```html\n<!doctype html><html><body><h2>Visible HTML preview</h2><p>release off screen</p></body></html>\n```"
                index % 3 == 0 -> "### Reply $index\n\n- first evidence\n- second evidence\n\nA completed assistant reply."
                index % 3 == 1 -> "Message $index\n\n```kotlin\nval answer = $index\n\nprintln(answer)\n```"
                else -> "Reply $index\n\n| Item | Value |\n| --- | --- |\n| progress | $index |\n| status | complete |"
            }
            ChatBubble("history-$index", if (index % 2 == 0) "assistant" else "user", body)
        }
        var followLatest by mutableStateOf(follow)
        var liveBubble by mutableStateOf(ChatBubble("live-bubble", "assistant", "Streaming performance fixture", streaming = true))
        val parentCompositions = AtomicInteger()
        val liveHeight = AtomicInteger()
        val liveMeasureHeights = CopyOnWriteArrayList<Int>()
        lateinit var listState: LazyListState
        lateinit var hostView: View
    }

    private class FrameIntervalProbe(private val instrumentation: Instrumentation) {
        private val intervals = CopyOnWriteArrayList<Double>()
        private val done = CountDownLatch(1)
        private var started = false
        private var stopped = false
        private var choreographer: Choreographer? = null
        private var previous = 0L
        private var deadline = 0L
        private val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (previous != 0L) intervals += (frameTimeNanos - previous) / 1_000_000.0
                previous = frameTimeNanos
                if (System.nanoTime() < deadline) choreographer?.postFrameCallback(this) else done.countDown()
            }
        }
        fun start(durationMs: Long) {
            instrumentation.runOnMainSync {
                started = true
                deadline = System.nanoTime() + durationMs * 1_000_000
                choreographer = Choreographer.getInstance()
                choreographer?.postFrameCallback(callback)
            }
        }
        fun stop(): List<Double> {
            if (started && !stopped) {
                done.await(5, TimeUnit.SECONDS)
                instrumentation.runOnMainSync { choreographer?.removeFrameCallback(callback) }
                stopped = true
            }
            return intervals.toList()
        }
    }

    companion object {
        private const val PREFETCH_HTML = "```html\n<!doctype html><html><body><h2>Prefetch only</h2></body></html>\n```"
    }
}
