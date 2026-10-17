package com.luckyagent.android.ui.components

import com.luckyagent.android.ui.util.validatedArtifactPath
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal const val HTML_DOCUMENT_LIMIT = 2 * 1024 * 1024
internal const val HTML_PREVIEW_ORIGIN = "https://html-preview.luckyagent.invalid/"
private val previewOrigin = HTML_PREVIEW_ORIGIN.toHttpUrlOrNull()!!

private fun HttpUrl.isArtifactRoute() = encodedPath.endsWith("/api/v1/artifacts")
private fun HttpUrl.artifactPath() = queryParameterValues("path").singleOrNull()?.let(::validatedArtifactPath)
private fun HttpUrl.hasUserInfo() = username.isNotEmpty() || password.isNotEmpty()
private fun HttpUrl.safePath() = pathSegments.none {
    (it.isNotEmpty() && it.trimEnd(' ').all { c -> c == '.' }) ||
        '/' in it || '\\' in it || it.any { c -> c <= '\u001f' } ||
        Regex("%(?:25|2e|2f|5c|00)", RegexOption.IGNORE_CASE).containsMatchIn(it)
}

/** Every fetch, including redirects, stays beside the document that authorized it. */
internal class HtmlResourceScope(documentUrl: String) {
    private val document = documentUrl.toHttpUrlOrNull()
    private val artifactPath = document?.takeIf { it.isArtifactRoute() }?.artifactPath()
    private val directory = if (document?.isArtifactRoute() == true) {
        artifactPath?.substringBeforeLast('/')?.plus("/")
    } else document?.encodedPath?.substringBeforeLast('/')?.plus("/")

    fun allows(source: String): Boolean {
        val target = source.toHttpUrlOrNull() ?: return false
        val origin = document ?: return false
        val dir = directory ?: return false
        if (origin.hasUserInfo() || target.hasUserInfo() || !sameHtmlOrigin(source, origin.toString())) return false
        if (origin.isArtifactRoute()) {
            if (target.encodedPath != origin.encodedPath) return false
            val path = target.artifactPath() ?: return false
            return path.startsWith(dir)
        }
        // A generic document must never gain access to authenticated artifact routes.
        return !target.isArtifactRoute() && target.safePath() && target.encodedPath.startsWith(dir)
    }
}

/** The WebView only sees a synthetic origin, even for ordinary HTTPS attachments. */
internal fun htmlResourceBase(documentUrl: String): String {
    val document = documentUrl.toHttpUrlOrNull() ?: return HTML_PREVIEW_ORIGIN
    val builder = previewOrigin.newBuilder()
    if (document.isArtifactRoute()) {
        val path = document.artifactPath() ?: return HTML_PREVIEW_ORIGIN
        builder.addPathSegment("artifact")
        path.split('/').forEach { builder.addPathSegment(it) }
    } else {
        if (!document.safePath() || document.hasUserInfo()) return HTML_PREVIEW_ORIGIN
        builder.addPathSegment("document")
        document.pathSegments.forEach { builder.addPathSegment(it) }
    }
    return builder.build().toString()
}

/** Only synthetic URLs in the current document directory may enter the application proxy. */
internal fun htmlResourceUrl(requestUrl: String, documentUrl: String): String? {
    val request = requestUrl.toHttpUrlOrNull() ?: return null
    val document = documentUrl.toHttpUrlOrNull() ?: return null
    if (!sameHtmlOrigin(request.toString(), HTML_PREVIEW_ORIGIN) || request.hasUserInfo() || !request.safePath()) return null
    val path = request.pathSegments.drop(1)
    val target = if (document.isArtifactRoute()) {
        if (request.pathSegments.firstOrNull() != "artifact") return null
        val artifact = validatedArtifactPath(path.joinToString("/")) ?: return null
        document.newBuilder().query(null).setQueryParameter("path", artifact).fragment(null).build()
    } else {
        if (request.pathSegments.firstOrNull() != "document") return null
        document.newBuilder().encodedPath("/" + request.encodedPathSegments.drop(1).joinToString("/"))
            .encodedQuery(request.encodedQuery).fragment(null).build()
    }
    return target.toString().takeIf { HtmlResourceScope(documentUrl).allows(it) }
}

internal fun isArtifactResourceUrl(url: String, authBaseUrl: String): Boolean {
    val target = url.toHttpUrlOrNull() ?: return false
    val endpoint = (authBaseUrl.trimEnd('/') + "/api/v1/artifacts").toHttpUrlOrNull() ?: return false
    return !target.hasUserInfo() && sameHtmlOrigin(url, authBaseUrl) &&
        target.encodedPath == endpoint.encodedPath &&
        target.artifactPath() != null
}

/** One budget covers the document and all its subresources, including redirect hops. */
internal class HtmlResourceBudget(
    private val maxRequests: Int = 64,
    private val maxBytes: Int = 8 * 1024 * 1024,
    maxConcurrent: Int = 4,
) {
    private val permits = Semaphore(maxConcurrent, true)
    private var requests = 0
    private var bytes = 0

    fun acquire() {
        if (!permits.tryAcquire(15, TimeUnit.SECONDS)) throw IOException("预览资源加载繁忙，请重试")
    }
    fun release() { permits.release() }

    @Synchronized fun request() {
        if (requests >= maxRequests) throw IOException("预览资源数量超过上限，请下载查看")
        if (bytes >= maxBytes) throw IOException("预览资源总大小超过上限，请下载查看")
        requests++
    }
    @Synchronized fun consume(count: Int) {
        if (count > maxBytes - bytes) {
            bytes = maxBytes
            throw IOException("预览资源总大小超过上限，请下载查看")
        }
        bytes += count
    }
}

internal class HtmlResourceLoader(
    private val headers: Map<String, String>,
    private val authBaseUrl: String,
    private val budget: HtmlResourceBudget = HtmlResourceBudget(),
) : AutoCloseable {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    @Volatile private var closed = false

    data class Resource(val bytes: ByteArray, val mimeType: String, val charset: String, val url: String)

    fun fetch(source: String, limit: Int = HTML_DOCUMENT_LIMIT, scope: HtmlResourceScope? = null): Resource {
        var url = resolveHtmlUrl(source, authBaseUrl) ?: throw IOException("地址不是有效的 HTTP/HTTPS 地址")
        val allowed = scope ?: HtmlResourceScope(url)
        budget.acquire()
        try {
            repeat(6) {
                if (closed) throw IOException("预览已关闭")
                if (!allowed.allows(url)) throw IOException("资源超出当前文档目录，已阻止加载")
                budget.request()
                val request = Request.Builder().url(url).get().apply {
                    if (isArtifactResourceUrl(url, authBaseUrl)) {
                        headers.forEach { (name, value) -> header(name, value) }
                    }
                }.build()
                client.newCall(request).execute().use { response ->
                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                        url = response.header("Location")?.let { resolveHtmlUrl(it, url) }
                            ?: throw IOException("重定向地址无效")
                        // The next iteration checks the complete policy before making a request.
                    } else {
                        if (!response.isSuccessful) throw IOException("服务器返回 ${response.code}")
                        val body = response.body ?: throw IOException("文件内容为空")
                        val type = body.contentType()
                        return Resource(body.byteStream().use { it.readHtmlLimited(limit, budget::consume) },
                            type?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                            type?.charset()?.name() ?: "UTF-8", url)
                    }
                }
            }
            throw IOException("重定向次数过多")
        } finally { budget.release() }
    }

    override fun close() {
        closed = true
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }
}

internal fun InputStream.readHtmlLimited(limit: Int, consume: (Int) -> Unit = {}): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count > limit - out.size()) throw IOException("内容超过 ${limit / 1024 / 1024} MB 的预览上限，请下载查看")
        consume(count)
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
