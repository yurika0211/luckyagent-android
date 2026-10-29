package com.luckyagent.android.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.IOException

internal fun htmlPreviewDocument(source: String, resourceBaseUrl: String, inline: Boolean = false): String {
    val doc = Jsoup.parse(source)
    // The document has an isolated origin; the base only resolves CSS/images and page anchors.
    doc.select("base").remove()
    resolveHtmlUrl(resourceBaseUrl, resourceBaseUrl)?.let { doc.head().prependElement("base").attr("href", htmlResourceBase(it)) }
    if (doc.selectFirst("meta[name=viewport]") == null) {
        doc.head().prependElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
    }
    // Page JS may run for DOM/SVG interaction, but must not talk to the network.
    // Subresources are mediated by shouldInterceptRequest + htmlResourceUrl allowlist.
    doc.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content",
        "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' data:; " +
            "img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' data: blob:; " +
            "connect-src 'none'; worker-src 'none'; child-src 'none'; form-action 'none'; object-src 'none'; frame-src 'none'; base-uri 'self'")
    val inlineFit = if (inline) {
        // A page written for a full viewport (100vh + flex centering) would otherwise
        // become one screen tall and hide its controls under the message card.
        "html,body{min-height:0!important;height:auto!important}body{display:block!important}" +
            "body *{min-height:0!important}"
    } else ""
    doc.head().appendElement("style").appendText("html,body{margin:0;background:transparent}html{overflow-wrap:anywhere}img,video{max-width:100%;height:auto}pre{overflow:auto}$inlineFit")
    return doc.outerHtml()
}

/** Measure laid-out content, ignoring viewport-sized ancestors such as 100vh. */
internal const val INLINE_HTML_HEIGHT_JS =
    "(function(){var b=document.body;if(!b)return 0;" +
        "var bottom=0;var nodes=b.querySelectorAll('*');" +
        "for(var i=0;i<nodes.length;i++){var n=nodes[i];var s=getComputedStyle(n);" +
        "if(s.position==='fixed'||s.display==='none'||s.visibility==='hidden')continue;" +
        "if(n.offsetHeight<=0||n.offsetWidth<=0)continue;" +
        "var h=n.offsetHeight+n.offsetTop;var p=n.offsetParent;" +
        "while(p&&p!==b){h+=p.offsetTop-(p.scrollTop||0);p=p.offsetParent}" +
        "bottom=Math.max(bottom,h)}" +
        "return Math.ceil(bottom+8)})()"

private class HtmlHeightBridge(
    private val onHeight: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun report(raw: String?) {
        val height = raw?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: return
        main.post { onHeight(height) }
    }
}

private const val INLINE_HTML_MAX_HEIGHT = 480

/** Heavy pages stay as source until the user asks. A light page can render inline. */
internal const val INLINE_HTML_ACTIVE_LIMIT = 48_000

internal fun inlineHtmlShouldRender(source: String): Boolean = source.length <= INLINE_HTML_ACTIVE_LIMIT

internal const val HTML_RENDER_CRASH_MESSAGE = "这段 HTML 把预览进程打崩了，已改显示源码。聊天其他内容不受影响。"

/**
 * Fenced HTML sits in the message flow. Page controls stay tappable; the corner
 * button opens the existing full-screen preview. Attachments keep [HtmlPreviewDialog].
 * A heavy page, or one whose renderer crashes, falls back to source instead of
 * taking the process down. Full screen pauses the inline renderer so the same
 * page is not running twice.
 */
@Composable
internal fun InlineHtmlPreview(source: String, baseUrl: String, headers: Map<String, String>) {
    var show by remember(source) { mutableStateOf(false) }
    var crashed by remember(source) { mutableStateOf(false) }
    var contentHeight by remember(source) { mutableIntStateOf(0) }
    val render = inlineHtmlShouldRender(source) && !crashed
    // JS offsetHeight is a CSS pixel. With width=device-width that matches 1 dp, so do not divide by density again.
    val height = if (!render) {
        96.dp
    } else if (contentHeight > 0) {
        contentHeight.dp.coerceIn(24.dp, INLINE_HTML_MAX_HEIGHT.dp)
    } else {
        48.dp
    }
    val loader = remember(headers, baseUrl, source) { HtmlResourceLoader(headers, baseUrl) }
    DisposableEffect(loader) { onDispose { loader.close() } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (render && !show) {
            HtmlPageView(
                source = source,
                baseUrl = baseUrl,
                loader = loader,
                modifier = Modifier.fillMaxSize(),
                visible = true,
                zoomable = false,
                onContentHeight = { contentHeight = it },
                onRenderCrashed = { crashed = true },
            )
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    if (crashed) HTML_RENDER_CRASH_MESSAGE else "这段 HTML 较大，点右上角再预览。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (crashed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        IconButton(
            onClick = { show = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
        ) {
            Icon(Icons.Outlined.OpenInFull, contentDescription = "全屏查看 HTML")
        }
    }
    if (show) {
        HtmlPreviewDialog(
            source = source,
            baseUrl = baseUrl,
            headers = headers,
            onDismiss = { show = false },
            onRenderCrashed = { crashed = true },
        )
    }
}

@Composable
internal fun HtmlPreviewCard(source: String, baseUrl: String, headers: Map<String, String>) {
    InlineHtmlPreview(source = source, baseUrl = baseUrl, headers = headers)
}

/** One full-page viewer used by both fenced HTML and file attachments. */
@Composable
internal fun HtmlPreviewDialog(
    source: String? = null,
    uri: String? = null,
    title: String = "HTML 预览",
    baseUrl: String = "",
    headers: Map<String, String> = emptyMap(),
    onDownload: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onRenderCrashed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    val loader = remember(headers, baseUrl, source, uri, attempt) { HtmlResourceLoader(headers, baseUrl) }
    DisposableEffect(loader) { onDispose { loader.close() } }
    var content by remember(source, uri) { mutableStateOf(source) }
    var resourceBase by remember(source, uri, baseUrl) { mutableStateOf(if (source != null) "" else resolveHtmlUrl(uri.orEmpty(), baseUrl) ?: "") }
    var error by remember(source, uri) { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var renderFailed by remember(source, uri) { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { target ->
        if (target != null) scope.launch {
            saved = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(target)?.bufferedWriter()?.use { it.write(content.orEmpty()) }
                    ?: throw IOException("无法写入文件")
            }; "已保存 HTML" }.getOrElse { "保存失败：${it.message}" }
        }
    }
    LaunchedEffect(source, uri, loader, attempt) {
        if (source != null) return@LaunchedEffect
        error = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val parsed = Uri.parse(uri ?: throw IOException("附件没有可预览的地址"))
                if (parsed.scheme == "content") {
                    val bytes = context.contentResolver.openInputStream(parsed)?.use { it.readHtmlLimited(HTML_DOCUMENT_LIMIT) }
                        ?: throw IOException("无法读取附件")
                    String(bytes, Charsets.UTF_8) to ""
                } else {
                    val response = loader.fetch(uri.orEmpty())
                    String(response.bytes, charset(response.charset)) to response.url
                }
            }
            content = loaded.first
            resourceBase = loaded.second
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "无法加载 HTML" }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭 HTML 预览") }
                    Text(title, modifier = Modifier.weight(1f).padding(vertical = 14.dp), maxLines = 1)
                    IconButton(enabled = content != null || onDownload != null, onClick = {
                        if (onDownload != null) onDownload() else save.launch("LuckyAgent.html")
                    }) { Icon(Icons.Outlined.Download, "下载 HTML") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showSource, onClick = { showSource = false }, label = { Text("预览") })
                    FilterChip(selected = showSource, onClick = { showSource = true }, label = { Text("源码") })
                }
                saved?.let { Text(it, Modifier.padding(12.dp)) }
                when {
                    error != null -> Column(Modifier.padding(16.dp)) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { attempt++ }) { Text("重试") }
                    }
                    content == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                    else -> Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (renderFailed != null && !showSource) {
                            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                                Text(renderFailed.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                HtmlSourceText(content.orEmpty(), source != null)
                            }
                        } else {
                            HtmlPageView(
                                content.orEmpty(),
                                resourceBase,
                                loader,
                                Modifier.fillMaxSize(),
                                visible = !showSource,
                                onRenderCrashed = {
                                    renderFailed = HTML_RENDER_CRASH_MESSAGE
                                    onRenderCrashed()
                                },
                            )
                        }
                        if (showSource) Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                                HtmlSourceText(content.orEmpty(), source != null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPageView(
    source: String,
    baseUrl: String,
    loader: HtmlResourceLoader,
    modifier: Modifier,
    visible: Boolean,
    zoomable: Boolean = true,
    onContentHeight: ((Int) -> Unit)? = null,
    onRenderCrashed: () -> Unit = {},
) {
    val document = remember(source, baseUrl, zoomable) { htmlPreviewDocument(source, baseUrl, inline = !zoomable) }
    val currentBase by rememberUpdatedState(baseUrl)
    val currentLoader by rememberUpdatedState(loader)
    val reportHeight by rememberUpdatedState(onContentHeight)
    val reportCrash by rememberUpdatedState(onRenderCrashed)
    val bridge = remember {
        HtmlHeightBridge { height -> reportHeight?.invoke(height) }
    }
    fun WebView.reportContentHeight() {
        if (reportHeight == null) return
        evaluateJavascript(
            "LuckyHtmlBridge.report(String($INLINE_HTML_HEIGHT_JS))",
            null,
        )
    }
    AndroidView(modifier = modifier, factory = { context -> WebView(context).apply {
        setBackgroundColor(0)
        settings.javaScriptEnabled = true
        addJavascriptInterface(bridge, "LuckyHtmlBridge")
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        isNestedScrollingEnabled = false
        setOnTouchListener { view, event ->
            if (!zoomable) {
                // A drag belongs to the message list. A tap still reaches the page.
                view.parent?.requestDisallowInterceptTouchEvent(
                    event.actionMasked != android.view.MotionEvent.ACTION_MOVE,
                )
            }
            false
        }
        if (zoomable) {
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        }
        // Preview document is https://html-preview...; artifact bytes are injected via interception.
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url?.toString().orEmpty()
                if (request.method != "GET") return blockedHtmlResource()
                val scheme = request.url?.scheme?.lowercase()
                // data/blob/about stay inside the WebView engine; never proxy them through OkHttp.
                if (scheme == "data" || scheme == "blob" || scheme == "about") return null
                val documentUrl = currentBase
                val resourceLoader = currentLoader
                val target = htmlResourceUrl(url, documentUrl) ?: return blockedHtmlResource()
                return try {
                    val resource = resourceLoader.fetch(target, scope = HtmlResourceScope(documentUrl))
                    WebResourceResponse(resource.mimeType, resource.charset, ByteArrayInputStream(resource.bytes))
                } catch (_: Exception) { blockedHtmlResource() }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url?.toString().orEmpty()
                if (url.substringBefore('#') == HTML_PREVIEW_ORIGIN.substringBefore('#')) return false
                if (request.url?.fragment != null && url.substringBefore('#') == htmlResourceBase(currentBase).substringBefore('#')) {
                    view.evaluateJavascript("location.hash=" + org.json.JSONObject.quote(request.url.fragment), null)
                } else if (request.hasGesture()) {
                    val mapped = htmlResourceUrl(url, currentBase)
                    val external = mapped ?: resolveHtmlUrl(url, currentBase)?.takeIf { candidate ->
                        val parsed = candidate.toHttpUrlOrNull()
                        parsed != null && parsed.host != Uri.parse(HTML_PREVIEW_ORIGIN).host &&
                            (parsed.scheme == "http" || parsed.scheme == "https")
                    }
                    external?.let { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
                }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                view.reportContentHeight()
                view.postDelayed({ view.reportContentHeight() }, 250)
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
                // The renderer is already dead. Returning false makes the app exit.
                view.stopLoading()
                reportCrash()
                return true
            }
        }
    } }, update = { view ->
        view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
        val loaded = view.tag as? String
        if (loaded != document) {
            view.tag = document
            view.loadDataWithBaseURL(HTML_PREVIEW_ORIGIN, document, "text/html", null, null)
        } else {
            view.reportContentHeight()
        }
    }, onRelease = { it.stopLoading(); it.destroy() })
}

internal fun blockedHtmlResource() = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

/** Source shown in the viewer. A fenced message is already bounded; an attachment is not. */
internal const val HTML_SOURCE_PREVIEW_LIMIT = 32_000

@Composable
internal fun HtmlSourceText(content: String, complete: Boolean) {
    if (!complete && content.length > HTML_SOURCE_PREVIEW_LIMIT) {
        Text("源码较长，此处显示前 ${HTML_SOURCE_PREVIEW_LIMIT} 字符；下载可保存完整文件")
    }
    CodeText(if (complete) content else content.take(HTML_SOURCE_PREVIEW_LIMIT))
}
