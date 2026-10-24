package com.luckyagent.android.ui.components

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SvgPreviewInstrumentedTest {
    @get:Rule val compose = createComposeRule()

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
        compose.waitUntil(15_000) { webView() != null }
        assertEquals("\"0 0 200 80\"", javascript("document.querySelector('svg').getAttribute('viewBox')"))
        assertEquals("true", javascript("!!document.querySelector('svg rect')"))
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
