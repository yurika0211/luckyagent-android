package com.luckyagent.android.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Static SVG in the message. The card keeps the graphic's aspect ratio.
 * A drawing taller than the card scrolls inside the card. The WebView is
 * created only while the card is on screen, and destroyed when it leaves.
 */
@Composable
internal fun InlineSvgPreview(source: String, svg: SvgDocument) {
    var show by remember(source) { mutableStateOf(false) }
    var crashed by remember(source) { mutableStateOf(false) }
    var widthPx by remember(source) { mutableIntStateOf(0) }
    var measured by remember(source) { mutableStateOf<SvgFrame?>(null) }
    // A declared frame is exact. Without one the square card holds the drawing
    // until the laid-out size comes back from the WebView.
    val frame = svg.frame ?: measured
    val density = LocalDensity.current
    val viewport = LocalConfiguration.current.screenHeightDp.toFloat()
    val widthDp = with(density) { widthPx.toDp().value }.takeIf { it > 0f } ?: 320f
    val height = svgCardHeight(frame, widthDp, viewport).dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { widthPx = it.width },
    ) {
        if (crashed) {
            Text(
                HTML_RENDER_CRASH_MESSAGE,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!show) {
            SvgPage(
                svg = svg,
                fit = !svgCardScrolls(frame, widthDp, viewport),
                modifier = Modifier.fillMaxSize(),
                zoomable = false,
                onSize = { w, h ->
                    if (svg.frame == null && w > 0 && h > 0) measured = SvgFrame(w.toFloat(), h.toFloat())
                },
                onCrashed = { crashed = true },
            )
        }
        IconButton(
            onClick = { show = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
        ) {
            Icon(Icons.Outlined.OpenInFull, contentDescription = "全屏查看 SVG")
        }
    }
    if (show) {
        SvgPreviewDialog(source = source, svg = svg, onDismiss = { show = false }, onCrashed = { crashed = true })
    }
}

@Composable
private fun SvgPreviewDialog(source: String, svg: SvgDocument, onDismiss: () -> Unit, onCrashed: () -> Unit) {
    var showSource by remember(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭 SVG 预览") }
                    Text("SVG 预览", modifier = Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 1)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    FilterChip(selected = !showSource, onClick = { showSource = false }, label = { Text("预览") })
                    Spacer(Modifier.padding(4.dp))
                    FilterChip(selected = showSource, onClick = { showSource = true }, label = { Text("源码") })
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (failed && !showSource) {
                        Text(HTML_RENDER_CRASH_MESSAGE, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                    } else if (!showSource) {
                        // Full width, natural height, scroll and zoom: the whole drawing is reachable.
                        SvgPage(svg, fit = false, Modifier.fillMaxSize(), zoomable = true, onSize = { _, _ -> }, onCrashed = {
                            failed = true
                            onCrashed()
                        })
                    }
                    if (showSource) Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                            CodeText(source)
                        }
                    }
                }
            }
        }
    }
}

private class SvgSizeBridge(private val onSize: (Int, Int) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(raw: String?) {
        val parts = raw?.split(',') ?: return
        val width = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return
        val height = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return
        if (width <= 0 || height <= 0) return
        main.post { onSize(width, height) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun SvgPage(
    svg: SvgDocument,
    fit: Boolean,
    modifier: Modifier,
    zoomable: Boolean,
    onSize: (Int, Int) -> Unit,
    onCrashed: () -> Unit,
) {
    val document = remember(svg, fit) { svgPreviewDocument(svg, fit) }
    val reportSize by rememberUpdatedState(onSize)
    val reportCrash by rememberUpdatedState(onCrashed)
    val bridge = remember { SvgSizeBridge { w, h -> reportSize(w, h) } }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // AndroidView's default WRAP_CONTENT makes WebView treat CSS
                // percentage/viewport heights as content-dependent (zero). The
                // enclosing card already supplies an exact size.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0)
                // The injected size probe is the only script this page needs; the
                // document policy in svgPreviewDocument allows none from the source.
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.domStorageEnabled = false
                // Nested scrolling fights the chat list. The touch listener, installed in
                // update so it follows `fit`, decides which one owns each drag.
                isNestedScrollingEnabled = false
                addJavascriptInterface(bridge, "LuckySvgBridge")
                if (zoomable) {
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): WebResourceResponse? {
                        if (request.method != "GET") return blockedHtmlResource()
                        val scheme = request.url?.scheme?.lowercase()
                        // data:/about: stay in the WebView. CSP already allows data: images;
                        // handing them an empty 200 body makes an embedded image look blank.
                        if (scheme == "data" || scheme == "about") return null
                        return blockedHtmlResource()
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript("LuckySvgBridge.report(String($SVG_SIZE_JS))", null)
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        view.stopLoading()
                        reportCrash()
                        return true
                    }
                }
            }
        },
        update = { view ->
            // AndroidView keeps the native view across recompositions. A history
            // refresh (or a fit/scroll layout change) must load the new document,
            // while unrelated recompositions must not restart it.
            if (view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
            // fit: the drawing fills the card, so a drag belongs to the chat list.
            // scrolling: keep the drag while the drawing can still move that way.
            var lastY = 0f
            view.setOnTouchListener { touched, event ->
                val keep = when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastY = event.y
                        !fit
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val goingDown = event.y - lastY > 0f
                        lastY = event.y
                        !fit && touched.canScrollVertically(if (goingDown) -1 else 1)
                    }
                    else -> false
                }
                touched.parent?.requestDisallowInterceptTouchEvent(keep)
                false
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
