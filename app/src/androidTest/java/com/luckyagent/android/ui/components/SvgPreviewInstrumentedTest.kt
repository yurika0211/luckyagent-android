package com.luckyagent.android.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class SvgPreviewInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun graph(): String = instrumentation.context.assets.open("svg/kruskal-static.svg")
        .bufferedReader().use { it.readText() }.trim()

    @Test
    fun staticSvgKeepsCaseSensitiveViewBoxInTheWebView() {
        val source = "<svg viewBox='0 0 200 80' xmlns='http://www.w3.org/2000/svg'><rect width='200' height='80' fill='#356f42'/></svg>"
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        MarkdownText("```html\n$source\n```")
                    }
                }
            }
        }
        awaitDocument()
        assertEquals("\"0 0 200 80\"", javascript("document.querySelector('svg').getAttribute('viewBox')"))
        assertEquals("true", javascript("!!document.querySelector('svg rect')"))
    }

    @Test
    fun suppliedBareGraphPaintsEdgesAndNodesInTheChatCard() {
        showMarkdown(graph())
        awaitGraph()
        assertGraphPainted("kruskal-bare")
    }

    @Test
    fun suppliedFencedGraphPaintsInlineAndFullScreen() {
        showMarkdown("```html\n${graph()}\n```")
        awaitGraph()
        assertGraphPainted("kruskal-inline")
        val inline = requireNotNull(webView())
        compose.onNodeWithContentDescription("全屏查看 SVG").performClick()
        compose.onNodeWithContentDescription("关闭 SVG 预览").assertIsDisplayed()
        compose.waitUntil(15_000) { webView()?.let { it !== inline } == true }
        awaitGraph()
        assertGraphPainted("kruskal-fullscreen")
        val fullscreen = requireNotNull(webView())
        compose.onNodeWithText("源码", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { webView() == null }
        compose.onNodeWithText("预览", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { webView()?.let { it !== fullscreen } == true }
        awaitGraph()
        assertGraphPainted("kruskal-reopened")
        compose.onNodeWithContentDescription("关闭 SVG 预览").performClick()
        compose.onNodeWithContentDescription("全屏查看 SVG").assertIsDisplayed()
        awaitGraph()
        assertGraphPainted("kruskal-returned")
    }

    @Test
    fun replacingAnEmptySvgInTheSameSlotPaintsTheNewGraph() {
        val markdown = mutableStateOf("<svg viewBox='0 0 320 240'><title>initial empty</title></svg>")
        showMarkdown { markdown.value }
        awaitDocument()
        assertEquals("0", javascript("document.querySelectorAll('svg line').length"))
        compose.runOnIdle { markdown.value = graph() }
        awaitGraph()
        assertGraphPainted("kruskal-replaced")
    }

    private fun showMarkdown(markdown: String) = showMarkdown { markdown }

    private fun showMarkdown(markdown: () -> String) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                        // Chat bubbles constrain width without giving the preview an explicit size.
                        Column(Modifier.widthIn(max = 520.dp)) {
                            MarkdownText(markdown())
                        }
                    }
                }
            }
        }
    }

    private fun awaitDocument() {
        compose.waitUntil(15_000) {
            webView() != null && javascript(
                "document.readyState === 'complete' && !!document.querySelector('svg')",
            ) == "true"
        }
    }

    private fun awaitGraph() {
        compose.waitUntil(15_000) {
            webView() != null && javascript(
                "document.readyState === 'complete' && document.querySelectorAll('svg line').length === 6" +
                    " && document.querySelectorAll('svg circle').length === 4",
            ) == "true"
        }
        assertEquals("\"0 0 320 240\"", javascript("document.querySelector('svg').getAttribute('viewBox')"))
        assertEquals("\"CSS1Compat\"", javascript("document.compatMode"))
        assertEquals("\"ABCD\"", javascript(
            "Array.from(document.querySelectorAll('svg text')).slice(-4).map(t => t.textContent).join('')",
        ))
    }

    private fun assertGraphPainted(name: String) {
        val view = requireNotNull(webView())
        val drawn = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    view.postOnAnimation { drawn.countDown() }
                }
            })
        }
        assertTrue("WebView visual update timeout", drawn.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val bounds = Rect()
        var lastScreenshot: Bitmap? = null
        var bluePixels = 0
        var grayPixels = 0
        val start = SystemClock.elapsedRealtime()
        try {
            // A visual-state callback makes the next WebView draw ready; it does
            // not mean SurfaceFlinger has presented that frame to a screenshot.
            compose.waitUntil(5_000) {
                lastScreenshot?.recycle()
                val frame = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                lastScreenshot = frame
                instrumentation.runOnMainSync { assertTrue(view.getGlobalVisibleRect(bounds)) }
                bluePixels = 0
                grayPixels = 0
                for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(frame.height)) {
                    for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(frame.width)) {
                        val color = frame.getPixel(x, y)
                        val r = color shr 16 and 255
                        val g = color shr 8 and 255
                        val b = color and 255
                        if (r < 70 && g in 60..160 && b > 180) bluePixels++
                        if (r in 185..205 && g in 190..210 && b in 198..218) grayPixels++
                    }
                }
                bluePixels > 100 && grayPixels > 100
            }
        } catch (_: ComposeTimeoutException) {
            // Preserve the failing screenshot/DOM before reporting the pixel assertion.
        }
        val screenshot = requireNotNull(lastScreenshot)
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: instrumentation.targetContext.getExternalFilesDir(null)!!
        output.mkdirs()
        File(output, "$name-layout.json").writeText(javascript(
            """JSON.stringify({
                mode: document.compatMode,
                viewport: [innerWidth, innerHeight],
                htmlBounds: document.documentElement.getBoundingClientRect().toJSON(),
                bodyBounds: document.body.getBoundingClientRect().toJSON(),
                htmlCss: getComputedStyle(document.documentElement).height,
                bodyCss: getComputedStyle(document.body).height,
                svgCss: getComputedStyle(document.querySelector('svg')).height,
                svg: document.querySelector('svg').outerHTML,
                svgBounds: document.querySelector('svg').getBoundingClientRect().toJSON(),
                lineBounds: document.querySelector('svg line').getBoundingClientRect().toJSON(),
                lineStyle: {
                    stroke: getComputedStyle(document.querySelector('svg line')).stroke,
                    display: getComputedStyle(document.querySelector('svg line')).display,
                    visibility: getComputedStyle(document.querySelector('svg line')).visibility,
                    opacity: getComputedStyle(document.querySelector('svg line')).opacity,
                    namespace: document.querySelector('svg line').namespaceURI,
                    type: document.querySelector('svg line').constructor.name
                },
                styles: Array.from(document.querySelectorAll('style')).map(s => s.textContent)
            })""",
        ).orEmpty())
        File(output, "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        File(output, "$name-pixels.txt").writeText(
            "blue=$bluePixels gray=$grayPixels bounds=$bounds elapsedMs=${SystemClock.elapsedRealtime() - start}",
        )
        assertTrue("SVG DOM exists but blue edges were not painted ($bluePixels pixels)", bluePixels > 100)
        assertTrue("Discarded gray edges were not painted ($grayPixels pixels)", grayPixels > 100)
    }

    private fun webView(): WebView? {
        var found: WebView? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fun visit(view: View): WebView? = when {
                view is WebView -> view
                view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { visit(view.getChildAt(it)) }
                else -> null
            }
            found = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { visit(it) }
        }
        return found
    }

    private fun javascript(script: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        val view = webView()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view?.evaluateJavascript(script) { value -> result = value; latch.countDown() }
                ?: latch.countDown()
        }
        assertTrue("WebView callback timeout", latch.await(5, TimeUnit.SECONDS))
        return result
    }
}
