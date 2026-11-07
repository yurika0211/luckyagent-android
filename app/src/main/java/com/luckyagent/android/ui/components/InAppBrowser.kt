package com.luckyagent.android.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Set by the chat screen. HTML preview WebViews call this for http(s) links. */
internal var chatLinkOpener: ((String) -> Unit)? = null

/** Chat pages only. Settings, plugins, and APK updates stay on ACTION_VIEW. */
fun inAppBrowserUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val scheme = value.substringBefore(':', "").lowercase()
    if (scheme != "http" && scheme != "https") return null
    val rest = value.substringAfter("://", "")
    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    if (host.isBlank() || !host.contains('.')) return null
    return value
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowser(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val start = inAppBrowserUrl(url) ?: return
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember(start) { mutableStateOf(start) }
    var progress by remember(start) { mutableIntStateOf(0) }
    var failed by remember(start) { mutableStateOf(false) }
    fun openOutside() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(start))) }
        onClose()
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        BackHandler {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else onClose()
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "后退")
                }
                IconButton(onClick = { if (webView?.canGoForward() == true) webView?.goForward() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "前进")
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { openOutside() }) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = "用系统浏览器打开")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            if (failed) {
                Text("打不开这个页面")
                IconButton(onClick = { openOutside() }) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = "改用系统浏览器")
                }
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                            override fun onReceivedTitle(view: WebView?, value: String?) {
                                if (!value.isNullOrBlank()) title = value
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val next = request.url?.toString().orEmpty()
                                if (inAppBrowserUrl(next) == null) {
                                    if (request.hasGesture()) {
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                    }
                                    return true
                                }
                                return false
                            }
                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: android.webkit.WebResourceError,
                            ) {
                                if (request.isForMainFrame) failed = true
                            }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
                                failed = true
                                openOutside()
                                return true
                            }
                        }
                        loadUrl(start)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.destroy() },
            )
        }
    }
}
