package com.luckyagent.android.ui.screens

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.luckyagent.android.ui.ChatBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native hosting avoids ComposeTestRule's Espresso-idle and virtual-clock synchronization. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MermaidRenderingInstrumentedTest {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var activity: ComponentActivity

    @Before fun launchNativeHost() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity = it }
        Log.i("MermaidAcceptance", "Native activity host ready; Compose and WebView use Android's clock")
    }

    @After fun closeNativeHost() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test(timeout = 40_000) fun mermaidWithBlankLinesStaysSourceUntilClosedThenRendersOneDiagram() {
        val source = diagramSource
        var bubble by mutableStateOf(ChatBubble("mermaid", "assistant", "```mermaid\n$source"))
        showBubble { bubble }
        awaitSource(source)
        assertTrue(onMain { webViews().isEmpty() })

        onMain { bubble = bubble.copy(content = "```mermaid\n$source\n```") }
        val view = awaitDiagram()

        assertNativeDiagram(view)
        assertEquals("1", javascript(view, "document.querySelectorAll('#out svg').length"))
    }

    @Test(timeout = 40_000) fun tappingTheDiagramOpensAFullscreenNativePreviewWithWorkingZoom() {
        showBubble { ChatBubble("mermaid", "assistant", "```mermaid\n$diagramSource\n```") }
        val inline = awaitDiagram()
        assertNativeDiagram(inline)

        tapDiagram(inline)
        var full: WebView? = null
        await("full-screen Mermaid preview", 10_000) {
            val candidate = onMain { webViews().firstOrNull { it !== inline } }
            if (candidate != null && diagramReady(candidate)) full = candidate
            full != null
        }
        val fullscreen = requireNotNull(full)
        assertNativeDiagram(fullscreen)
        onMain {
            assertTrue(fullscreen.settings.supportZoom())
            assertTrue(fullscreen.settings.builtInZoomControls)
            assertFalse(fullscreen.settings.displayZoomControls)
            assertTrue("Preview should use the full dialog rather than the inline card", fullscreen.height > inline.height)
        }
        assertEquals("\"\"", javascript(fullscreen, "document.getElementById('out').className"))

        @Suppress("DEPRECATION")
        val originalScale = onMain { fullscreen.scale }
        onMain { fullscreen.zoomBy(1.4f) }
        await("native WebView zoom", 5_000) {
            @Suppress("DEPRECATION")
            onMain { fullscreen.scale > originalScale * 1.1f }
        }
        assertNativeDiagram(fullscreen)

        clickSemanticAction("关闭 Mermaid 放大预览")
        await("closing the full-screen preview", 5_000) { onMain { webViews().none { it === fullscreen } } }
        assertNativeDiagram(inline)
    }

    @Test(timeout = 25_000) fun malformedMermaidFallsBackToItsCompleteSource() {
        val source = "not-a-mermaid-diagram [[\n\nthis cannot be a valid graph"
        showBubble { ChatBubble("invalid-mermaid", "assistant", "```mermaid\n$source\n```") }

        // This is a closed Mermaid fence, so the failure must be handled by the renderer.
        // The original 3-second fallback is unchanged; allow startup overhead as well.
        awaitSource(source, timeoutMs = 10_000)
        await("failed Mermaid renderer removal", 5_000) { onMain { webViews().isEmpty() } }
        assertSourceVisible(source)
    }

    private fun showBubble(bubble: () -> ChatBubble) {
        onMain { activity.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                        BubbleRow(
                            bubble = bubble(),
                            avatarFile = "",
                            bubbleOpacity = 100,
                            imageHeaders = emptyMap(),
                            imageBaseUrl = "",
                            onDownload = {},
                            onQuote = {},
                            onCopy = {},
                        )
                    }
                }
            }
        } }
    }

    private fun awaitSource(source: String, timeoutMs: Long = 5_000) {
        await("complete Mermaid source", timeoutMs) {
            onMain { sourceNodes(source).size == 1 }
        }
        assertSourceVisible(source)
    }

    private fun awaitDiagram(): WebView {
        var found: WebView? = null
        await("native Mermaid SVG", 15_000) {
            val candidate = onMain { webViews().singleOrNull() }
            if (candidate != null && diagramReady(candidate)) found = candidate
            found != null
        }
        return requireNotNull(found)
    }

    private fun diagramReady(view: WebView): Boolean = javascript(view, """
        (() => {
          const svg = document.querySelector('#out svg');
          if (!svg) return false;
          const bounds = svg.getBoundingClientRect();
          return bounds.width > 0 && bounds.height > 0 &&
            svg.querySelectorAll('.node').length === 3 &&
            svg.querySelectorAll('.edgePaths path').length === 2 &&
            ['Start', 'Middle', 'Done'].every(label => svg.textContent.includes(label));
        })()
    """.trimIndent()) == "true"

    private fun assertNativeDiagram(view: WebView) {
        assertTrue("Expected the complete Mermaid SVG in the native WebView", diagramReady(view))
        onMain {
            assertTrue("Native preview is attached", view.isAttachedToWindow)
            assertTrue("Native preview has visible dimensions", view.isShown && view.width > 0 && view.height > 0)
            assertTrue("Mermaid must remain offline", view.settings.blockNetworkLoads)
        }
    }

    private fun tapDiagram(view: WebView) {
        // Dispatch an actual touch pair through the native WebView rather than calling the JS bridge.
        val point = JSONArray(requireNotNull(javascript(view, """
            (() => {
              const bounds = document.getElementById('out').getBoundingClientRect();
              return [bounds.left + bounds.width / 2, bounds.top + bounds.height / 2, window.innerWidth];
            })()
        """.trimIndent())))
        onMain {
            val downTime = SystemClock.uptimeMillis()
            val pixelsPerCssPixel = view.width / point.getDouble(2)
            val x = (point.getDouble(0) * pixelsPerCssPixel).toFloat().coerceIn(1f, (view.width - 1).toFloat())
            val y = (point.getDouble(1) * pixelsPerCssPixel).toFloat().coerceIn(1f, (view.height - 1).toFloat())
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                try {
                    view.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
        }
    }

    private fun await(description: String, timeoutMs: Long, condition: () -> Boolean) {
        Log.i("MermaidAcceptance", "Awaiting $description on Android's clock (no Compose idle synchronization)")
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            if (condition()) {
                Log.i("MermaidAcceptance", "Completed $description")
                return
            }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Timed out waiting for $description after ${timeoutMs}ms")
    }

    private fun webViews(): List<WebView> {
        return nativeViews().filterIsInstance<WebView>()
    }

    // Read the real unmerged semantics tree directly on Android's main thread.
    // The high-level test selectors call waitForIdle even with autoAdvance=false.
    private fun semanticsNodes(): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return nativeViews().filterIsInstance<RootForTest>()
            .flatMap { descendants(it.semanticsOwner.unmergedRootSemanticsNode) }
    }

    private fun sourceNodes(source: String): List<SemanticsNode> = semanticsNodes().filter { node ->
        node.config.contains(SemanticsProperties.Text) &&
            node.config[SemanticsProperties.Text].any { it.text == source }
    }

    private fun assertSourceVisible(source: String) {
        onMain {
            val nodes = sourceNodes(source)
            assertEquals("Expected exactly one complete source block", 1, nodes.size)
            val bounds = nodes.single().boundsInWindow
            assertTrue("Complete source is visible", bounds.width > 0 && bounds.height > 0)
        }
    }

    private fun clickSemanticAction(description: String) {
        onMain {
            val described = semanticsNodes().single { node ->
                node.config.contains(SemanticsProperties.ContentDescription) &&
                    description in node.config[SemanticsProperties.ContentDescription]
            }
            var clickable: SemanticsNode? = described
            while (clickable != null && !clickable.config.contains(SemanticsActions.OnClick)) {
                clickable = clickable.parent
            }
            val action = requireNotNull(clickable).config[SemanticsActions.OnClick].action
            assertTrue("Expected a working accessibility click for $description", action?.invoke() == true)
        }
    }

    private fun nativeViews(): List<View> {
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            else emptyList()
        return WindowInspector.getGlobalWindowViews().flatMap(::descendants).distinct()
    }

    private fun <T> onMain(block: () -> T): T {
        var value: T? = null
        var failure: Throwable? = null
        val done = CountDownLatch(1)
        main.post {
            try {
                value = block()
            } catch (error: Throwable) {
                failure = error
            } finally {
                done.countDown()
            }
        }
        assertTrue("Android main thread did not respond within 2 seconds", done.await(2, TimeUnit.SECONDS))
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun javascript(view: WebView, script: String): String? {
        val done = CountDownLatch(1)
        var value: String? = null
        main.post {
            if (!view.isAttachedToWindow) {
                done.countDown()
            } else {
                view.evaluateJavascript(script) { result -> value = result; done.countDown() }
            }
        }
        assertTrue("WebView JavaScript did not respond within 2 seconds", done.await(2, TimeUnit.SECONDS))
        return value
    }

    private val diagramSource = "flowchart LR\n  A[Start]-->B[Middle]\n\n  B-->C[Done]"
}
