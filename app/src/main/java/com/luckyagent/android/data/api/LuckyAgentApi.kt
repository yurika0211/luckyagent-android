package com.luckyagent.android.data.api

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.luckyagent.android.BuildConfig
import com.luckyagent.android.data.settings.SettingsRepository
import com.luckyagent.android.data.settings.ClientSettings
import com.luckyagent.android.ui.util.resolveAttachmentDownloadUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Saved chat attachment. [uri] is a MediaStore content Uri on API 29+, otherwise a file Uri. */
data class AttachmentDownload(
    val uri: Uri,
    val displayName: String,
)

private data class PluginConnectionSnapshot(val settings: ClientSettings)

/** Streams picker content into OkHttp without holding a full attachment in RAM. */
private class ContentResolverRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mime: String,
) : okhttp3.RequestBody() {
    override fun contentType() = mime.toMediaType()

    override fun contentLength(): Long = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: error("无法读取所选文件")
        input.use {
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            val output = sink.outputStream()
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                require(total <= 31L * 1024L * 1024L) { "文件不能超过 31 MB" }
                output.write(buffer, 0, read)
            }
        }
    }
}

class LuckyAgentApi(
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val snap = chain.request().tag(PluginConnectionSnapshot::class.java)?.settings
                ?: settingsRepository.snapshot()
            val req = chain.request().newBuilder()
            val key = snap.apiKey.trim()
            if (key.isNotEmpty()) {
                if (snap.useBearer) {
                    req.header("Authorization", "Bearer $key")
                } else {
                    req.header("X-API-Key", key)
                }
            }
            req.header("Accept", "application/json")
            chain.proceed(req.build())
        }
        .apply {
            // BASIC logging is useful during local development, but it adds
            // work to every request and is unnecessary in a release build.
            if (BuildConfig.DEBUG) addInterceptor(logging)
        }
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val pluginClient = client.newBuilder()
        .readTimeout(100, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun listPlugins(): Result<PluginListResponse> =
        pluginRequest("/api/v1/plugins", "GET")

    suspend fun installPlugin(request: PluginInstallRequest): Result<PluginChangeResponse> =
        pluginRequest("/api/v1/plugins", "POST", json.encodeToString(request))

    suspend fun setPluginEnabled(name: String, enabled: Boolean): Result<PluginChangeResponse> =
        pluginRequest(pluginPath(name) + if (enabled) "/enable" else "/disable", "POST", "{}")

    suspend fun updatePlugin(name: String, trust: Boolean): Result<PluginChangeResponse> =
        pluginRequest(pluginPath(name) + "/update", "POST", json.encodeToString(PluginUpdateRequest(trust)))

    suspend fun removePlugin(name: String): Result<PluginChangeResponse> =
        pluginRequest(pluginPath(name), "DELETE")

    private fun pluginPath(name: String): String {
        require(name.length in 1..48 && Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(name)) { "插件名称无效" }
        return "/api/v1/plugins/$name"
    }

    private suspend inline fun <reified T> pluginRequest(path: String, method: String, payload: String? = null): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                // Bind both the URL and authentication to the same connection.
                // Switching hosts while an operation runs must not mix credentials.
                val connection = settingsRepository.snapshot()
                val target = (connection.apiBase.trim().trimEnd('/') + path).toHttpUrlOrNull()
                    ?: error("服务地址无效")
                val request = Request.Builder().url(target)
                    .tag(PluginConnectionSnapshot::class.java, PluginConnectionSnapshot(connection))
                    .method(method, payload?.toRequestBody(jsonMedia))
                    .build()
                executePluginRequest(request).use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (response.code == 404 && method == "GET" && path == "/api/v1/plugins") {
                            error("服务端尚不支持插件管理，请升级 LuckyAgent 服务后重试。")
                        }
                        error("插件操作失败（${response.code}）：${gatewayError(body)}")
                    }
                    Result.success(json.decodeFromString<T>(body))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private suspend fun executePluginRequest(request: Request): okhttp3.Response =
        suspendCancellableCoroutine { continuation ->
            val call = pluginClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    continuation.resume(response) { _, undelivered, _ -> undelivered.close() }
                }
            })
        }

    suspend fun uploadAttachment(resolver: ContentResolver, uri: Uri, fileName: String): Result<MediaAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val fileBody = ContentResolverRequestBody(resolver, uri, mime)
                fileBody.contentLength().takeIf { it >= 0L }?.let { length ->
                    require(length <= MaxUploadBytes) { "文件不能超过 31 MB" }
                }
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .build()
                val request = Request.Builder().url(url("/api/v1/uploads")).post(multipart).build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("upload ${resp.code}: $body")
                    json.decodeFromString(UploadResponse.serializer(), body).attachments.firstOrNull()
                        ?: error("服务器没有返回附件信息")
                }
            }
        }

    private companion object {
        const val MaxUploadBytes = 31L * 1024L * 1024L
        const val MaxDownloadBytes = 31L * 1024L * 1024L
    }

    /**
     * Downloads a chat attachment with the authenticated OkHttp client.
     * The auth interceptor always attaches the API key; system DownloadManager
     * does not, and it also ignores the app cleartext network config.
     */
    suspend fun downloadAttachment(
        context: Context,
        attachment: MediaAttachment,
        onProgress: (bytesCopied: Long, contentLength: Long?) -> Unit = { _, _ -> },
    ): Result<AttachmentDownload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawUrl = attachment.fileUrl?.trim().orEmpty()
                require(rawUrl.isNotBlank()) { "附件没有可下载的 URL" }
                val downloadUrl = resolveAttachmentDownloadUrl(rawUrl, baseUrl())
                    ?: error("附件 URL 无效")
                val fileName = attachment.fileName
                    ?.trim()
                    ?.replace('/', '_')
                    ?.replace('\\', '_')
                    ?.takeIf { it.isNotBlank() }
                    ?: "luckyagent-attachment"
                val destinationName = uniqueDownloadName(context, fileName)
                val mime = attachment.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                val request = Request.Builder().url(downloadUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val snippet = response.body?.string().orEmpty()
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(180)
                        error(
                            if (snippet.isEmpty()) "HTTP ${response.code}"
                            else "HTTP ${response.code}: $snippet",
                        )
                    }
                    val body = response.body ?: error("服务器返回了空文件")
                    val contentLength = body.contentLength().takeIf { it >= 0L }
                    if (contentLength != null && contentLength > MaxDownloadBytes) {
                        error("文件不能超过 31 MB")
                    }
                    body.byteStream().use { source ->
                        streamAttachmentBody(context, source, destinationName, mime, contentLength, onProgress)
                    }
                }
            }
        }

    private suspend fun streamAttachmentBody(
        context: Context,
        source: java.io.InputStream,
        displayName: String,
        mime: String,
        contentLength: Long?,
        onProgress: (bytesCopied: Long, contentLength: Long?) -> Unit,
    ): AttachmentDownload {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: error("无法写入系统下载目录")
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法写入系统下载目录")
                output.use { copyDownloadStream(source, it, contentLength, onProgress) }
                val published = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, published, null, null)
                return AttachmentDownload(uri, displayName)
            } catch (cancelled: CancellationException) {
                context.contentResolver.delete(uri, null, null)
                throw cancelled
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        }
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("应用下载目录不可用")
        dir.mkdirs()
        val target = File(dir, displayName)
        val partial = File(dir, "$displayName.part")
        try {
            partial.outputStream().use { copyDownloadStream(source, it, contentLength, onProgress) }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "无法保存下载文件" }
            return AttachmentDownload(Uri.fromFile(target), displayName)
        } catch (cancelled: CancellationException) {
            partial.delete()
            throw cancelled
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private suspend fun copyDownloadStream(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        contentLength: Long?,
        onProgress: (bytesCopied: Long, contentLength: Long?) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var lastEmitMs = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            copied += count
            if (copied > MaxDownloadBytes) error("文件不能超过 31 MB")
            val now = android.os.SystemClock.elapsedRealtime()
            val finished = contentLength != null && copied >= contentLength
            if (lastEmitMs == 0L || now - lastEmitMs >= 100L || finished) {
                lastEmitMs = now
                onProgress(copied, contentLength)
            }
        }
        onProgress(copied, contentLength)
    }

    private fun baseUrl(): String =
        settingsRepository.snapshot().apiBase.trim().trimEnd('/')

    private fun uniqueDownloadName(context: Context, requested: String): String {
        val taken = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreDownloadExists(context, requested)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir != null && File(dir, requested).exists()
        }
        if (!taken) return requested
        val dot = requested.lastIndexOf('.')
        val stem = if (dot > 0) requested.substring(0, dot) else requested
        val ext = if (dot > 0) requested.substring(dot) else ""
        return "$stem-${System.currentTimeMillis()}$ext"
    }

    private fun mediaStoreDownloadExists(context: Context, displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { it.moveToFirst() } ?: false
    }

    private fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val raw = baseUrl().trimEnd('/') + "/" + path.trimStart('/')
        val http = raw.toHttpUrlOrNull()
            ?: error("Invalid API base / path: $raw")
        val builder = http.newBuilder()
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    suspend fun inspectContext(sessionId: String): Result<ContextInspectResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = sessionId.trim()
                require(id.isNotEmpty()) { "session id required" }
                val request = Request.Builder()
                    .url(url("/api/v1/context", mapOf("session_id" to id, "inspect" to "1")))
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("context ${resp.code}: $body")
                    json.decodeFromString(ContextInspectResponse.serializer(), body)
                }
            }
        }

    suspend fun listApprovals(): Result<List<PendingApproval>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/approvals")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("approvals ${resp.code}: $body")
                json.decodeFromString(ApprovalsResponse.serializer(), body).approvals.orEmpty()
            }
        }
    }

    suspend fun resolveApproval(approval: PendingApproval, decision: String, input: String = ""): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(
                    ApprovalResolutionRequest.serializer(),
                    ApprovalResolutionRequest(
                        provider = approval.provider,
                        approvalId = approval.id,
                        decision = decision,
                        input = input,
                    ),
                )
                val request = Request.Builder()
                    .url(url("/api/v1/approvals"))
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("resolve approval ${resp.code}: $body")
                }
            }
        }

    suspend fun soul(): Result<SoulInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/soul")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("soul ${resp.code}: $body")
                json.decodeFromString(SoulInfo.serializer(), body)
            }
        }
    }

    /**
     * Rewrites a composer draft without opening a session.
     * The server owns the expand instruction; the client only sends the draft.
     */
    suspend fun expandDraft(draft: String, timeoutMs: Long? = null): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val message = draft.trim()
            require(message.isNotEmpty()) { "草稿是空的" }
            val payload = json.encodeToString(DraftExpandRequest(message = message))
            val request = Request.Builder()
                .url(url("/api/v1/chat/complete"))
                .post(payload.toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            val requestClient = timeoutMs
                ?.takeIf { it > 0 }
                ?.let { client.newBuilder().callTimeout(it, TimeUnit.MILLISECONDS).build() }
                ?: client
            requestClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(gatewayError(body))
                val decoded = json.decodeFromString(DraftExpandResponse.serializer(), body)
                decoded.error?.takeIf { it.isNotBlank() }?.let { error(it) }
                decoded.response.trim().ifBlank { error("扩写没有返回文字") }
            }
        }
    }

    suspend fun healthLive(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/health/live")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("health ${resp.code}: $body")
                body.ifBlank { """{"ok":true}""" }
            }
        }
    }

    suspend fun listModels(kind: String? = null, refresh: Boolean = false): Result<ModelsResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val query = buildMap<String, String> {
                kind?.trim()?.takeIf { it.isNotEmpty() }?.let { put("kind", it) }
                if (refresh) put("refresh", "1")
            }
            val request = Request.Builder().url(url("/api/v1/models", query)).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("models ${resp.code}: $body")
                val decoded = json.decodeFromString(ModelsResponse.serializer(), body)
                decoded.error?.takeIf { it.isNotBlank() }?.let { error(it) }
                decoded
            }
        }
    }

    suspend fun switchModel(kind: String, model: String, provider: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(kind.isNotBlank()) { "model kind required" }
                require(model.isNotBlank()) { "model id required" }
                val payload = json.encodeToString(
                    SwitchModelRequest(
                        kind = kind,
                        model = model,
                        provider = provider?.takeIf { it.isNotBlank() },
                    ),
                )
                val request = Request.Builder()
                    .url(url("/api/v1/models/switch"))
                    .post(payload.toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("model switch ${resp.code}: $body")
                }
            }
        }

    suspend fun getModelEffort(): Result<ModelEffortResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/models/effort")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("model effort ${resp.code}: $body")
                json.decodeFromString(ModelEffortResponse.serializer(), body)
            }
        }
    }

    suspend fun setModelEffort(effort: String): Result<ModelEffortResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(effort.isNotBlank()) { "effort required" }
            val payload = json.encodeToString(ModelEffortRequest(effort.trim().lowercase()))
            val request = Request.Builder().url(url("/api/v1/models/effort"))
                .put(payload.toRequestBody(jsonMedia)).header("Content-Type", "application/json").build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("model effort ${resp.code}: $body")
                json.decodeFromString(ModelEffortResponse.serializer(), body)
            }
        }
    }

    suspend fun getRuntimeConfig(): Result<RuntimeConfigSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/config")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("config ${resp.code}: $body")
                RuntimeConfigSnapshot(json.parseToJsonElement(body).jsonObject, resp.header("ETag"))
            }
        }
    }

    suspend fun getRuntimeConfigSchema(): Result<RuntimeConfigSchema> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/config/schema")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("config schema ${resp.code}: $body")
                json.decodeFromString(RuntimeConfigSchema.serializer(), body)
            }
        }
    }

    suspend fun patchRuntimeConfig(
        patch: JsonObject,
        clearSecrets: List<String> = emptyList(),
        ifMatch: String? = null,
        dryRun: Boolean = false,
    ): Result<RuntimeConfigPatchResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(RuntimeConfigPatchEnvelope(patch, clearSecrets, ifMatch, dryRun))
            val request = Request.Builder().url(url("/api/v1/config"))
                .patch(payload.toRequestBody(jsonMedia)).header("Content-Type", "application/json").build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("config patch ${resp.code}: $body")
                json.decodeFromString(RuntimeConfigPatchResponse.serializer(), body)
            }
        }
    }

    /** Compatibility path for runtimes that only expose full PUT updates. */
    suspend fun putRuntimeConfig(config: JsonObject, ifMatch: String? = null): Result<RuntimeConfigSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBuilder = Request.Builder().url(url("/api/v1/config"))
                .put(config.toString().toRequestBody(jsonMedia)).header("Content-Type", "application/json")
            if (!ifMatch.isNullOrBlank()) requestBuilder.header("If-Match", ifMatch)
            client.newCall(requestBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("config update ${resp.code}: $body")
                RuntimeConfigSnapshot(json.parseToJsonElement(body).jsonObject, resp.header("ETag"))
            }
        }
    }

    suspend fun reloadRuntimeConfig(): Result<String> = getJson("/api/v1/config/reload", method = "POST")

    suspend fun listSessions(query: String = ""): Result<List<RuntimeSession>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = if (query.isBlank()) emptyMap() else mapOf("q" to query)
            val request = Request.Builder().url(url("/api/v1/sessions", q)).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("sessions ${resp.code}: $body")
                json.decodeFromString(SessionsResponse.serializer(), body).sessions
            }
        }
    }

    suspend fun createSession(title: String = "Android session"): Result<RuntimeSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(SessionCreateRequest(title = title))
                val request = Request.Builder()
                    .url(url("/api/v1/sessions"))
                    .post(payload.toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("create session ${resp.code}: $body")
                    json.decodeFromString(RuntimeSession.serializer(), body)
                }
            }
        }

    suspend fun patchSession(id: String, patch: SessionPatchRequest): Result<RuntimeSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(patch)
                val request = Request.Builder()
                    .url(url("/api/v1/sessions/$id"))
                    .patch(payload.toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("update session ${resp.code}: $body")
                    runCatching {
                        json.decodeFromString(RuntimeSession.serializer(), body)
                    }.getOrElse {
                        RuntimeSession(
                            id = id,
                            title = patch.title,
                            pinned = patch.pinned == true,
                            project = patch.project,
                        )
                    }
                }
            }
        }

    suspend fun renameSession(id: String, title: String): Result<RuntimeSession> =
        patchSession(id, SessionPatchRequest(title = title))

    suspend fun deleteSession(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url("/api/v1/sessions/$id"))
                    .delete()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("delete session ${resp.code}: $body")
                }
            }
        }

    suspend fun sessionHistory(
        sessionId: String,
        limit: Int? = 40,
        offset: Int = 0,
        /** When true, request full message bodies (include=full). Default uses server preview caps. */
        includeFull: Boolean = false,
    ): Result<SessionHistoryFetch> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = buildMap {
                    limit?.let { put("limit", it.coerceIn(1, 500).toString()) }
                    put("offset", offset.coerceAtLeast(0).toString())
                    if (includeFull) {
                        put("include", "full")
                    }
                }
                val request = Request.Builder()
                    .url(url("/api/v1/sessions/$sessionId", query))
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    sessionHistoryFetch(resp.code, body) { raw ->
                        json.decodeFromString(SessionHistory.serializer(), raw)
                    }
                }
            }
        }

    /** POST /api/v1/sessions/{id}/compact — compress session history into a boundary. */
    suspend fun compactSession(
        sessionId: String,
        dryRun: Boolean = false,
        forceLocal: Boolean = false,
    ): Result<CompactSessionResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(
                    CompactSessionRequest.serializer(),
                    CompactSessionRequest(dryRun = dryRun, forceLocal = forceLocal),
                )
                val request = Request.Builder()
                    .url(url("/api/v1/sessions/$sessionId/compact"))
                    .post(payload.toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("compact session ${resp.code}: $body")
                    json.decodeFromString(CompactSessionResult.serializer(), body)
                }
            }
        }

    /**
     * GET /api/v1/memory returns tier stats only.
     * Prefer /api/v1/memory/recall?q=... for readable entries.
     */
    suspend fun memoryStats(): Result<MemoryStats> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/memory")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("memory stats ${resp.code}: $body")
                json.decodeFromString(MemoryListResponse.serializer(), body).stats
                    ?: MemoryStats()
            }
        }
    }

    suspend fun recallMemory(query: String, limit: Int = 50): Result<List<MemoryEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val q = query.trim().ifBlank { "project" }
                val request = Request.Builder()
                    .url(
                        url(
                            "/api/v1/memory/recall",
                            mapOf(
                                "q" to q,
                                "limit" to limit.toString(),
                            ),
                        ),
                    )
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("memory recall ${resp.code}: $body")
                    val decoded = json.decodeFromString(MemoryListResponse.serializer(), body)
                    when {
                        decoded.results.isNotEmpty() -> decoded.results
                        decoded.entries.isNotEmpty() -> decoded.entries
                        else -> emptyList()
                    }
                }
            }
        }

    suspend fun memoryGraph(limit: Int = 300, includeIsolated: Boolean = false): Result<MemoryGraphResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url("/api/v1/memory/graph", buildMap {
                    put("limit", limit.toString())
                    if (includeIsolated) put("isolated", "1")
                }))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("memory graph ${resp.code}: $body")
                json.decodeFromString(MemoryGraphResponse.serializer(), body)
            }
        }
    }

    suspend fun memoryRecallTrace(query: String, graphDepth: Int): Result<MemorySearchTrace> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url("/api/v1/memory/recall/trace", mapOf(
                    "q" to query.trim(),
                    "graph_depth" to graphDepth.coerceIn(1, 3).toString(),
                )))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("memory trace ${resp.code}: $body")
                json.decodeFromString(MemorySearchTrace.serializer(), body)
            }
        }
    }

    suspend fun sessionToolTrace(
        sessionId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): Result<SessionToolTrace> = withContext(Dispatchers.IO) {
        runCatching {
            val id = sessionId.trim()
            require(id.isNotEmpty()) { "session id required" }
            val query = mapOf(
                "limit" to limit.coerceIn(1, 200).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            )
            val request = Request.Builder()
                .url(url("/api/v1/sessions/$id/tools", query))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("trajectory ${resp.code}: $body")
                json.decodeFromString(SessionToolTrace.serializer(), body)
            }
        }
    }

    suspend fun listGateways(): Result<List<GatewayStatus>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/gateways")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("gateways ${resp.code}: $body")
                val decoded = json.decodeFromString(GatewaysResponse.serializer(), body)
                when {
                    decoded.gateways.isNotEmpty() -> decoded.gateways
                    decoded.items.isNotEmpty() -> decoded.items
                    else -> emptyList()
                }
            }
        }
    }

    /** Starts a gateway in the host `lh serve` process. Other settings are read from host config. */
    suspend fun startGateway(platform: String, token: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = normalizeGatewayName(platform)
            val payload = json.encodeToString(
                GatewayStartRequest.serializer(),
                GatewayStartRequest(token = token?.trim()?.takeIf { it.isNotEmpty() }),
            )
            val request = Request.Builder()
                .url(url("/api/v1/gateways/$name/start"))
                .post(payload.toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("start gateway $name ${resp.code}: ${gatewayError(body)}")
            }
        }
    }

    /** Stops a gateway that is registered in the host `lh serve` process. */
    suspend fun stopGateway(platform: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val name = normalizeGatewayName(platform)
            val request = Request.Builder()
                .url(url("/api/v1/gateways/$name/stop"))
                .post("".toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("stop gateway $name ${resp.code}: ${gatewayError(body)}")
            }
        }
    }

    private fun normalizeGatewayName(platform: String): String {
        val name = platform.trim().lowercase()
        require(name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "invalid gateway name"
        }
        return name
    }

    private fun gatewayError(body: String): String {
        if (body.isBlank()) return "request failed"
        return runCatching {
            json.decodeFromString(GatewayErrorResponse.serializer(), body).error
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(500)
    }

    suspend fun listSkills(): Result<SkillsResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/skills")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("skills ${resp.code}: $body")
                json.decodeFromString(SkillsResponse.serializer(), body)
            }
        }
    }

    suspend fun listCommands(): Result<List<RuntimeCommand>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/commands")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("commands ${resp.code}: $body")
                json.decodeFromString(CommandCatalogResponse.serializer(), body).commands
            }
        }
    }

    suspend fun listCronJobs(): Result<CronListResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/cron")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("cron ${resp.code}: $body")
                json.decodeFromString(CronListResponse.serializer(), body)
            }
        }
    }

    suspend fun createCronJob(requestBody: CronCreateRequest): Result<CronJob> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(CronCreateRequest.serializer(), requestBody)
            val request = Request.Builder()
                .url(url("/api/v1/cron"))
                .post(payload.toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("create cron ${resp.code}: $body")
                json.decodeFromString(CronJob.serializer(), body)
            }
        }
    }

    suspend fun updateCronJob(id: String, action: String): Result<CronJob> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "cron id required" }
            val payload = json.encodeToString(CronActionRequest.serializer(), CronActionRequest(action))
            val request = Request.Builder()
                .url(url("/api/v1/cron/${id.trim()}"))
                .patch(payload.toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("update cron ${resp.code}: $body")
                json.decodeFromString(CronJob.serializer(), body)
            }
        }
    }

    suspend fun deleteCronJob(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "cron id required" }
            val request = Request.Builder()
                .url(url("/api/v1/cron/${id.trim()}"))
                .delete()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("delete cron ${resp.code}: $body")
            }
        }
    }

    suspend fun runCommand(
        command: String,
        args: String,
        sessionId: String,
    ): Result<CommandExecution> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(CommandRequest(command = command, args = args, sessionId = sessionId))
            val request = Request.Builder()
                .url(url("/api/v1/commands"))
                .post(payload.toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("command ${resp.code}: $body")
                json.decodeFromString(CommandExecution.serializer(), body)
            }
        }
    }

    suspend fun listTaskRecords(limit: Int = 200): Result<List<TaskRecord>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url("/api/v1/tasks", mapOf("limit" to limit.coerceIn(1, 200).toString())))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("tasks ${resp.code}: $body")
                json.decodeFromString(TaskListResponse.serializer(), body).tasks
            }
        }
    }

    suspend fun getAutonomyDashboard(state: String? = null, limit: Int = 200): Result<AutonomyDashboardResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = buildMap {
                    state?.trim()?.takeIf { it.isNotEmpty() }?.let { put("state", it) }
                    put("limit", limit.coerceIn(1, 200).toString())
                }
                val request = Request.Builder()
                    .url(url("/api/v1/autonomy/dashboard", query))
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("autonomy dashboard ${resp.code}: $body")
                    json.decodeFromString(AutonomyDashboardResponse.serializer(), body)
                }
            }
        }

    suspend fun getAutonomyTask(id: String): Result<AutonomyTaskDetailResponse> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "background task id required" }
            val request = Request.Builder().url(url("/api/v1/autonomy/tasks/$id")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("background task ${resp.code}: $body")
                json.decodeFromString(AutonomyTaskDetailResponse.serializer(), body)
            }
        }
    }

    suspend fun getTaskRecord(id: String): Result<TaskRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "task id required" }
            val request = Request.Builder().url(url("/api/v1/tasks/$id")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("task ${resp.code}: $body")
                json.decodeFromString(TaskRecord.serializer(), body)
            }
        }
    }

    suspend fun getTaskTree(id: String): Result<TaskTreeNode> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "task id required" }
            val request = Request.Builder().url(url("/api/v1/tasks/$id/tree")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("task tree ${resp.code}: $body")
                json.decodeFromString(TaskTreeNode.serializer(), body)
            }
        }
    }

    suspend fun getTaskEvents(id: String): Result<List<TaskEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "task id required" }
            val request = Request.Builder().url(url("/api/v1/tasks/$id/events")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("task events ${resp.code}: $body")
                json.decodeFromString(TaskEventsResponse.serializer(), body).events
            }
        }
    }

    suspend fun listBlackboard(taskId: String): Result<List<BlackboardEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            require(taskId.isNotBlank()) { "task id required" }
            val request = Request.Builder()
                .url(url("/api/v1/agents/blackboard", mapOf("task_id" to taskId)))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("blackboard ${resp.code}: $body")
                json.decodeFromString(BlackboardResponse.serializer(), body).items
            }
        }
    }

    suspend fun listAgentMessages(taskId: String, limit: Int = 50): Result<List<AgentMessage>> = withContext(Dispatchers.IO) {
        runCatching {
            require(taskId.isNotBlank()) { "task id required" }
            val request = Request.Builder()
                .url(url("/api/v1/agents/messages", mapOf(
                    "task_id" to taskId,
                    "limit" to limit.coerceIn(1, 200).toString(),
                )))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("agent messages ${resp.code}: $body")
                json.decodeFromString(AgentMessagesResponse.serializer(), body).messages
            }
        }
    }

    suspend fun getTaskResult(id: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "task id required" }
            val request = Request.Builder().url(url("/api/v1/tasks/$id/result")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("task result ${resp.code}: $body")
                json.decodeFromString(TaskResultResponse.serializer(), body).result
            }
        }
    }

    /** Planner/MDP trace for a task. Older tasks legitimately return 404. */
    suspend fun getTaskTrace(id: String): Result<kotlinx.serialization.json.JsonElement> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "task id required" }
            val request = Request.Builder().url(url("/api/v1/tasks/$id/trace")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("task trace ${resp.code}: $body")
                json.parseToJsonElement(body)
            }
        }
    }

    suspend fun listLegacyTasks(): Result<List<LegacyCollabTask>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url("/api/v1/agents/tasks")).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("legacy tasks ${resp.code}: $body")
                json.decodeFromString(LegacyTasksResponse.serializer(), body).tasks
            }
        }
    }

    suspend fun getLegacyTask(id: String): Result<LegacyCollabTask> = withContext(Dispatchers.IO) {
        runCatching {
            require(id.isNotBlank()) { "task id required" }
            val request = Request.Builder()
                .url(url("/api/v1/agents/task", mapOf("id" to id)))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("legacy task ${resp.code}: $body")
                json.decodeFromString(LegacyCollabTask.serializer(), body)
            }
        }
    }

    suspend fun cancelTask(id: String, origin: TaskOrigin, reason: String = "cancelled by user"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(id.isNotBlank()) { "task id required" }
                val request = if (origin == TaskOrigin.Legacy) {
                    Request.Builder()
                        .url(url("/api/v1/agents/cancel", mapOf("id" to id)))
                        .post("".toRequestBody(jsonMedia))
                        .header("Content-Type", "application/json")
                        .build()
                } else {
                    val payload = "{\"reason\":${json.encodeToString(reason)}}"
                    Request.Builder()
                        .url(url("/api/v1/tasks/$id/cancel"))
                        .post(payload.toRequestBody(jsonMedia))
                        .header("Content-Type", "application/json")
                        .build()
                }
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("cancel task ${resp.code}: $body")
                }
            }
        }

    suspend fun getJson(path: String, method: String = "GET"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url(path))
            val request = if (method.equals("POST", true)) builder.post("".toRequestBody(jsonMedia)).build() else builder.get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("${resp.code}: $body")
                body
            }
        }
    }
}
