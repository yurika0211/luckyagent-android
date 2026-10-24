package com.luckyagent.android.data.standalone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class ConnectionMode { Runtime, Standalone }

@Serializable
data class StandaloneSettings(
    val apiBase: String = "",
    val apiKey: String = "",
    val model: String = "",
    val sessionId: String = "standalone-main",
)

@Serializable
data class StandaloneMessage(
    val role: String,
    val content: String,
    val kind: String = "chat",
)

@Serializable
data class StandaloneSession(
    val id: String,
    val title: String = "",
    val messages: List<StandaloneMessage> = emptyList(),
    val summary: String = "",
    val updatedAt: Long = 0L,
)

@Serializable
private data class CompletionPayload(
    val model: String,
    val messages: List<Map<String, String>>,
    @SerialName("max_tokens") val maxTokens: Int,
)

/** Rough token estimate. Chinese characters count as one token, Latin words as one. */
internal fun estimateTokens(text: String): Int {
    if (text.isBlank()) return 0
    var tokens = 0
    var latin = false
    text.forEach { ch ->
        if (ch.isWhitespace()) {
            if (latin) tokens += 1
            latin = false
        } else if (ch.code < 128) {
            latin = true
        } else {
            if (latin) tokens += 1
            latin = false
            tokens += 1
        }
    }
    if (latin) tokens += 1
    return tokens
}

internal fun standaloneContextTokens(session: StandaloneSession): Int =
    estimateTokens(session.summary) + session.messages.sumOf { estimateTokens(it.content) }

/**
 * Keep the newest messages and fold the older ones into one summary.
 * Returns null when there is nothing old enough to fold.
 */
internal fun compactStandaloneSession(session: StandaloneSession, keepRecentTokens: Int = 1200): StandaloneSession? {
    if (session.messages.size < 4) return null
    var kept = 0
    var cut = session.messages.size
    for (index in session.messages.indices.reversed()) {
        kept += estimateTokens(session.messages[index].content)
        if (kept > keepRecentTokens && index < session.messages.size - 2) {
            cut = index
            break
        }
        cut = index
    }
    if (cut <= 0) return null
    val older = session.messages.take(cut)
    val folded = buildString {
        if (session.summary.isNotBlank()) append(session.summary.trim()).append("\n")
        older.forEach { message ->
            append(if (message.role == "user") "用户" else "助手")
            append("：")
            append(message.content.trim().take(240))
            append('\n')
        }
    }.trim().take(4000)
    if (folded.isBlank()) return null
    return session.copy(summary = folded, messages = session.messages.drop(cut))
}

/**
 * Build a request from completed history and the current user turn.
 *
 * [session] must not already contain [userText]. The caller persists the current
 * turn separately so it can survive a failed request without sending it twice.
 */
internal fun buildStandaloneMessages(session: StandaloneSession, userText: String): List<Map<String, String>> {
    val system = buildString {
        append("你是手机上的独立聊天助手。回答使用用户所用的语言。")
        if (session.summary.isNotBlank()) {
            append("\n以下是更早对话的压缩摘要，继续遵守其中的约束：\n")
            append(session.summary)
        }
    }
    return buildList {
        add(mapOf("role" to "system", "content" to system))
        session.messages.forEach { message ->
            val content = if (message.kind == "search") "搜索结果：\n${message.content}" else message.content
            add(mapOf("role" to message.role, "content" to content))
        }
        add(mapOf("role" to "user", "content" to userText))
    }
}

class StandaloneChatClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val activeCall = AtomicReference<Call?>(null)

    /**
     * Abort the request in flight. The waiting caller sees the failure as an
     * ordinary error and settles its own UI; OkHttp reports it as an IOException.
     */
    fun cancel() {
        activeCall.getAndSet(null)?.cancel()
    }

    private fun <T> call(request: Request, read: (Response) -> T): T {
        val call = http.newCall(request)
        activeCall.set(call)
        return try {
            call.execute().use(read)
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    fun check(settings: StandaloneSettings): Result<String> = runCatching {
        requireConnectionSettings(settings)
        val model = settings.model.trim().ifBlank { discoverModel(settings) }
        // The probe proves the endpoint answers, not that the model wrote prose.
        // A reasoning model can spend the whole budget on reasoning_content and
        // return an empty message, which still means the URL and Key work.
        val url = chatCompletionsUrl(settings.apiBase)
        val body = post(
            url = url,
            apiKey = settings.apiKey,
            model = model,
            messages = listOf(mapOf("role" to "user", "content" to "Reply with ok")),
            maxTokens = PROBE_MAX_TOKENS,
        )
        requireChatAnswer(body)
        model
    }

    fun complete(
        settings: StandaloneSettings,
        messages: List<Map<String, String>>,
        maxTokens: Int = 1024,
    ): Result<String> = runCatching {
        requireConnectionSettings(settings)
        val model = settings.model.trim().ifBlank { discoverModel(settings) }
        completeRequest(settings, model, messages, maxTokens)
    }

    private fun completeRequest(
        settings: StandaloneSettings,
        model: String,
        messages: List<Map<String, String>>,
        maxTokens: Int,
    ): String {
        val url = chatCompletionsUrl(settings.apiBase)
        val body = post(url, settings.apiKey, model, messages, maxTokens)
        return runCatching { extractCompletion(body) }
            .getOrElse { error("${it.message ?: "模型没有返回正文"}（请求 $url）") }
    }

    private fun post(
        url: String,
        apiKey: String,
        model: String,
        messages: List<Map<String, String>>,
        maxTokens: Int,
    ): String {
        val payload = json.encodeToString(
            CompletionPayload(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
            ),
        )
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON))
            .build()
        return call(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("${readableModelError(response.code, body)}（请求 $url）")
            body
        }
    }

    private fun requireConnectionSettings(settings: StandaloneSettings) {
        require(settings.apiBase.isNotBlank()) { "模型 URL 还没填" }
        require(settings.apiKey.isNotBlank()) { "模型 Key 还没填" }
    }

    private fun discoverModel(settings: StandaloneSettings): String {
        val url = modelsUrl(settings.apiBase)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .get()
            .build()
        return call(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("${readableModelError(response.code, body)}（请求 $url）")
            extractFirstModel(body)
        }
    }

    fun search(query: String): Result<String> = runCatching {
        val trimmed = query.trim()
        require(trimmed.isNotBlank()) { "搜索词是空的" }
        val request = Request.Builder()
            .url("https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}&format=json&no_html=1&skip_disambig=1")
            .header("User-Agent", "LuckyAgent-Android")
            .get()
            .build()
        // Go through call() so a stop aborts the search too; a blocking execute()
        // here would keep running after the coroutine was cancelled.
        call(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("搜索失败：${response.code}")
            formatSearch(trimmed, body).ifBlank { "没有搜到「$trimmed」的结果" }
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

private const val CHAT_COMPLETIONS_SUFFIX = "/chat/completions"
private const val MODELS_SUFFIX = "/models"

/**
 * Budget for the connectivity probe. Reasoning models answer by spending tokens
 * on `reasoning_content` first, so a tiny budget yields an empty message even
 * when the endpoint is healthy.
 */
private const val PROBE_MAX_TOKENS = 64

/** A 2xx body with a `choices` array proves the endpoint speaks chat completions. */
internal fun requireChatAnswer(body: String) {
    val root = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject }
        .getOrElse { error("模型返回的不是对话响应") }
    val choices = runCatching { root["choices"]?.jsonArray }.getOrNull()
    if (choices.isNullOrEmpty()) error("模型返回的不是对话响应")
}

internal fun chatCompletionsUrl(apiBase: String): String {
    val parsed = parseApiUrl(apiBase)
    val path = parsed.encodedPath.trimEnd('/')
    if (path.endsWith(CHAT_COMPLETIONS_SUFFIX)) return parsed.toString()
    val basePath = normalizedApiBasePath(parsed)
    return parsed.newBuilder()
        .encodedPath(basePath.trimEnd('/') + CHAT_COMPLETIONS_SUFFIX)
        .build()
        .toString()
}

internal fun modelsUrl(apiBase: String): String {
    val parsed = parseApiUrl(apiBase)
    val basePath = normalizedApiBasePath(parsed)
    val modelsPath = if (basePath == "/") MODELS_SUFFIX else basePath.trimEnd('/') + MODELS_SUFFIX
    return parsed.newBuilder()
        .encodedPath(modelsPath)
        .build()
        .toString()
}

private fun parseApiUrl(apiBase: String): HttpUrl =
    apiBase.trim().toHttpUrlOrNull()
        ?: error("模型 URL 无效，请填写 http:// 或 https:// 地址")

private fun normalizedApiBasePath(url: HttpUrl): String {
    val path = url.encodedPath.trimEnd('/')
    return when {
        path.isEmpty() || path == "/" -> "/v1"
        path.endsWith(CHAT_COMPLETIONS_SUFFIX) ->
            path.removeSuffix(CHAT_COMPLETIONS_SUFFIX).ifBlank { "/" }
        // Any other prefix is a gateway that already carries its own version
        // (open.bigmodel.cn/api/paas/v4, host/llm/openai). Appending keeps those
        // settings working, which the strict /v1 rule broke.
        else -> path
    }
}

internal fun extractCompletion(body: String): String {
    val json = Json { ignoreUnknownKeys = true }
    val root = json.parseToJsonElement(body)
    val content = root.jsonObject["choices"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("message")
        ?.jsonObject
        ?.get("content")
        ?.jsonPrimitive
        ?.content
        ?.trim()
        .orEmpty()
    if (content.isBlank()) error("模型没有返回正文")
    return content
}

internal fun extractFirstModel(body: String): String {
    val root = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject }
        .getOrElse { error("模型列表响应无效") }
    val model = root["data"]
        ?.jsonArray
        ?.asSequence()
        ?.mapNotNull { item ->
            item.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }
        ?.firstOrNull()
    return model ?: error("模型列表为空，请填写模型名")
}

internal fun formatSearch(query: String, body: String): String {
    val root = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return ""
    val lines = mutableListOf<String>()
    root["AbstractText"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { lines += it }
    root["AbstractURL"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { lines += it }
    root["RelatedTopics"]?.jsonArray?.take(5)?.forEach { item ->
        val obj = item.jsonObject
        val text = obj["Text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val url = obj["FirstURL"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (text != null) lines += if (url != null) "$text\n$url" else text
    }
    if (lines.isEmpty()) return ""
    return "搜索「$query」\n" + lines.joinToString("\n")
}

internal fun readableModelError(code: Int, body: String): String {
    val message = runCatching {
        Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
    return message ?: "模型请求失败：$code"
}
