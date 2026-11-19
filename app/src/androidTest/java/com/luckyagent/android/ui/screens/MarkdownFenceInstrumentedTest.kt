package com.luckyagent.android.ui.screens

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luckyagent.android.ui.ChatBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MarkdownFenceInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var hostView: View

    @Test fun completedBubbleKeepsBlankLinesAndHtmlLiteralsInsideOneCodeBlock() {
        val code = "val before = 1\n\nval markup = \"<b>literal</b>\"\n\nval after = 2"
        showBubble {
            ChatBubble("fence", "assistant", "代码之前\n\n```kotlin\n$code\n```\n\n代码之后")
        }

        awaitCode(code)
        compose.onNodeWithText(code, useUnmergedTree = true).assertTextEquals(code).assertIsDisplayed()
        compose.onNodeWithText("代码之前", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("代码之后", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun streamingBubbleKeepsTheCompletedFenceAfterItsFinalBlankLinesArrive() {
        val code = "first()\n\nsecond()\n\nthird()"
        var bubble by mutableStateOf(ChatBubble("stream", "assistant", "```kotlin\nfirst()", streaming = true))
        showBubble { bubble }
        compose.onNodeWithText("```kotlin\nfirst()", useUnmergedTree = true).assertIsDisplayed()

        compose.runOnIdle {
            bubble = bubble.copy(content = "```kotlin\n$code\n```\n\n已完成", streaming = false)
        }

        awaitCode(code)
        compose.onNodeWithText(code, useUnmergedTree = true).assertTextEquals(code).assertIsDisplayed()
        compose.onNodeWithText("已完成", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun consecutiveFencesKeepTheirOwnBlankLinesAndSurroundingParagraphs() {
        val first = "alpha\n\nbeta"
        val second = "{\n\n  \"answer\": 42\n\n}"
        showBubble {
            ChatBubble("two-fences", "assistant", "```text\n$first\n```\n\n中间说明\n\n```json\n$second\n```")
        }

        awaitCode(first)
        awaitCode(second)
        compose.onNodeWithText(first, useUnmergedTree = true).assertTextEquals(first).assertIsDisplayed()
        compose.onNodeWithText(second, useUnmergedTree = true).assertTextEquals(second).assertIsDisplayed()
        compose.onNodeWithText("中间说明", useUnmergedTree = true).assertIsDisplayed()
    }

    @Ignore("API 37 Compose/WebView idle does not settle; manual diagram screenshot recorded in docs/chat-scroll-and-markdown.md")
    @Test(timeout = 30_000) fun mermaidWithBlankLinesStaysSourceUntilClosedThenRendersOneDiagram() {
        val body = "flowchart LR\n  A[Start]-->B[Middle]\n\n  B-->C[Done]"
        var bubble by mutableStateOf(ChatBubble("mermaid", "assistant", "```mermaid\n$body"))
        showBubble { bubble }
        awaitCode(body)
        compose.onNodeWithText(body, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(onNativeViews { views -> views.none { it is WebView } })
        compose.runOnIdle { bubble = bubble.copy(content = "```mermaid\n$body\n```") }
        awaitWebCondition {
            javascript("Boolean(document.querySelector('#out svg')) && document.querySelector('#out').textContent.includes('Start') && document.querySelector('#out').textContent.includes('Done')") == "true"
        }
        assertEquals("1", javascript("document.querySelectorAll('#out svg').length"))
    }

    @Test fun htmlWithBlankLinesStaysSourceUntilClosedThenRendersItsWholeDocument() {
        val body = "<!doctype html><html><body><div id='first'>alpha</div>\n\n<div id='second'>beta</div></body></html>"
        var bubble by mutableStateOf(ChatBubble("html", "assistant", "```html\n$body"))
        showBubble { bubble }
        awaitCode(body)
        compose.onNodeWithText(body, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(onNativeViews { views -> views.none { it is WebView } })

        compose.runOnIdle { bubble = bubble.copy(content = "```html\n$body\n```") }

        awaitWebCondition {
            javascript("document.getElementById('first')?.textContent === 'alpha' && document.getElementById('second')?.textContent === 'beta'") == "true"
        }
        compose.onNodeWithContentDescription("全屏查看 HTML").assertIsDisplayed()
    }

    @Test fun streamingDisplayMathWithBlankLinesBecomesOneRenderedFormulaWhenCompleted() {
        val body = "\\frac{1}{2}\n\n+\\frac{1}{3}"
        val source = "$$\n$body"
        var bubble by mutableStateOf(ChatBubble("math", "assistant", source, streaming = true))
        showBubble { bubble }
        compose.onNodeWithText(source, useUnmergedTree = true).assertIsDisplayed()
        assertTrue(onNativeViews { views -> views.none { it is ImageView } })

        compose.runOnIdle { bubble = bubble.copy(content = "$source\n$$", streaming = false) }

        compose.waitUntil(5_000) {
            onNativeViews { views ->
                views.filterIsInstance<ImageView>().count { view ->
                    val drawable = view.drawable
                    drawable is JLatexMathDrawable &&
                        drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0
                } == 1
            }
        }
    }

    private fun showBubble(bubble: () -> ChatBubble) {
        compose.setContent {
            hostView = LocalView.current.rootView
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
        }
    }

    private fun awaitCode(code: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(code, useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
    }

    private fun <T> onNativeViews(block: (List<View>) -> T): T {
        var result: T? = null
        var failure: Throwable? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                fun descendants(view: View): List<View> = listOf(view) +
                    if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
                    else emptyList()
                result = block(descendants(hostView))
            } catch (error: Throwable) {
                failure = error
            } finally {
                latch.countDown()
            }
        }
        assertTrue("Native view inspection timed out", latch.await(5, TimeUnit.SECONDS))
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    // Compose first mounts the native view; JavaScript then runs on Android's
    // clock. Do not advance virtual frames while waiting on the WebView renderer.
    private fun awaitWebCondition(condition: () -> Boolean) {
        compose.waitUntil(5_000) {
            onNativeViews { views -> views.any { it is WebView } }
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("WebView did not produce the expected document within 15 seconds")
    }

    private fun javascript(script: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        Handler(Looper.getMainLooper()).post {
            fun findWebView(view: View): WebView? = when (view) {
                is WebView -> view
                is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
                else -> null
            }
            val view = findWebView(hostView)
            view?.evaluateJavascript(script) { value -> result = value; latch.countDown() } ?: latch.countDown()
        }
        return if (latch.await(1, TimeUnit.SECONDS)) result else null
    }
}
