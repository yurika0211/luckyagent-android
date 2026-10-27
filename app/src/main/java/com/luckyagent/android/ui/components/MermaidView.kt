package com.luckyagent.android.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText2
import org.json.JSONObject

private const val RENDER_TIMEOUT_MS = 3_000L

private val BRIDGE_KEY = "mermaid-bridge".hashCode()

/**
 * Draws one mermaid block with the bundled mermaid 11 build.
 *
 * The WebView only loads the asset page. Any other request is dropped.
 * Rendering waits until the page has finished loading, then passes the
 * source through [JSONObject] so it is never spliced into HTML.
 * A render error, or no callback within 3 seconds, falls back to the source.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MermaidView(source: String) {
    var failed by remember(source) { mutableStateOf(false) }
    var heightPx by remember(source) { mutableIntStateOf(0) }
    var expanded by remember(source) { mutableStateOf(false) }
    if (failed || source.isBlank()) {
        SourceBlock(source)
        return
    }
    val background = CloverBgSide.toArgb()
    val foreground = CloverText2.toArgb()
    val line = CloverLine.toArgb()
    val density = LocalDensity.current
    val heightModifier = if (heightPx > 0) {
        val dp = with(density) { heightPx.toDp() }.coerceAtMost(480.dp)
        Modifier.height(dp)
    } else {
        Modifier.heightIn(min = 48.dp, max = 480.dp)
    }
    // The diagram lives in a WebView, which swallows the parent clickable.
    // A page tap calls MermaidBridge.onTapped; pinch-zoom stays in the preview.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(heightModifier)
            .background(CloverBgSide, RoundedCornerShape(8.dp))
            .border(1.dp, CloverLine, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        MermaidWebView(
            source = source,
            background = background,
            foreground = foreground,
            line = line,
            zoomable = false,
            fit = true,
            modifier = Modifier.fillMaxWidth(),
            onRendered = { heightPx = it },
            onFailed = { failed = true },
            onClick = { expanded = true },
        )
    }
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(CloverBgSide),
            ) {
                MermaidWebView(
                    source = source,
                    background = background,
                    foreground = foreground,
                    line = line,
                    zoomable = true,
                    fit = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    onRendered = {},
                    onFailed = { expanded = false },
                    onClick = {},
                )
                IconButton(
                    onClick = { expanded = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭 Mermaid 放大预览")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidWebView(
    source: String,
    background: Int,
    foreground: Int,
    line: Int,
    zoomable: Boolean,
    fit: Boolean,
    modifier: Modifier,
    onRendered: (Int) -> Unit,
    onFailed: () -> Unit,
    onClick: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.blockNetworkLoads = true
                if (zoomable) {
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                }
                isClickable = false
                val bridge = MermaidBridge(
                    onRendered = onRendered,
                    onFailed = onFailed,
                    onClick = onClick,
                )
                addJavascriptInterface(bridge, "MermaidBridge")
                setTag(BRIDGE_KEY, bridge)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.render(source, background, foreground, line, fit, bridge)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url?.toString().orEmpty()
                        if (url.startsWith("file:///android_asset/mermaid/")) return null
                        return WebResourceResponse("text/plain", "utf-8", null)
                    }
                }
                loadUrl("file:///android_asset/mermaid/index.html")
            }
        },
        update = { webView ->
            val bridge = webView.getTag(BRIDGE_KEY) as? MermaidBridge ?: return@AndroidView
            webView.evaluateJavascript("window.__ready === true") { ready ->
                if (ready == "true") webView.render(source, background, foreground, line, fit, bridge)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun WebView.render(
    source: String,
    background: Int,
    foreground: Int,
    line: Int,
    fit: Boolean,
    bridge: MermaidBridge,
) {
    val payload = JSONObject()
        .put("source", source)
        .put("background", hex(background))
        .put("foreground", hex(foreground))
        .put("line", hex(line))
        .put("fit", fit)
        .toString()
    bridge.arm()
    evaluateJavascript("window.renderMermaid($payload)", null)
}

private class MermaidBridge(
    private val onRendered: (Int) -> Unit,
    private val onFailed: () -> Unit,
    private val onClick: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun arm() {
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            pending = null
            onFailed()
        }
        pending = task
        handler.postDelayed(task, RENDER_TIMEOUT_MS)
    }

    @JavascriptInterface
    fun onRendered(heightPx: Int) {
        handler.post {
            pending?.let { handler.removeCallbacks(it) }
            pending = null
            onRendered(heightPx)
        }
    }

    @JavascriptInterface
    fun onFailed() {
        handler.post {
            pending?.let { handler.removeCallbacks(it) }
            pending = null
            onFailed()
        }
    }

    @JavascriptInterface
    fun onTapped() {
        handler.post { onClick() }
    }
}

private fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

@Composable
private fun SourceBlock(source: String) {
    SelectionContainer {
        Text(
            text = source,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = CloverText2,
                lineHeight = 18.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(CloverBgSide, RoundedCornerShape(8.dp))
                .border(1.dp, CloverLine, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}
