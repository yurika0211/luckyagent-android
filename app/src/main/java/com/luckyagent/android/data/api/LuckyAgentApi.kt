package com.luckyagent.android.data.api

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.luckyagent.android.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

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
            val snap = settingsRepository.snapshot()
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
        .addInterceptor(logging)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun uploadAttachment(resolver: ContentResolver, uri: Uri, fileName: String): Result<MediaAttachment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 31 * 1024 * 1024) { "文件不能超过 31 MB" }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: error("无法读取所选文件")
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val fileBody = bytes.toRequestBody(mime.toMediaType())
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

    fun enqueueAttachmentDownload(context: Context, attachment: MediaAttachment): Result<Long> = runCatching {
        val rawUrl = attachment.fileUrl?.trim().orEmpty()
        require(rawUrl.isNotBlank()) { "附件没有可下载的 URL" }
        val downloadUrl = absoluteUrl(rawUrl)
        require(downloadUrl.startsWith("http://") || downloadUrl.startsWith("https://")) {
            "附件 URL 无效"
        }
        val fileName = attachment.fileName
            ?.trim()
            ?.replace('/', '_')
            ?.replace('\\', '_')
            ?.takeIf { it.isNotBlank() }
            ?: "luckyagent-attachment"
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle(fileName)
            .setDescription("LuckyAgent attachment")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        attachment.mimeType?.takeIf { it.isNotBlank() }?.let(request::setMimeType)
        if (isApiUrl(downloadUrl)) {
            val snap = settingsRepository.snapshot()
            if (snap.apiKey.isNotBlank()) {
                if (snap.useBearer) request.addRequestHeader("Authorization", "Bearer ${snap.apiKey}")
                else request.addRequestHeader("X-API-Key", snap.apiKey)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: error("系统下载服务不可用")
        manager.enqueue(request)
    }

    private fun baseUrl(): String =
        settingsRepository.snapshot().apiBase.trim().trimEnd('/')

    private fun absoluteUrl(raw: String): String = when {
        raw.startsWith("http://") || raw.startsWith("https://") -> raw
        else -> baseUrl() + "/" + raw.trimStart('/')
    }

    private fun isApiUrl(raw: String): Boolean {
        val target = raw.toHttpUrlOrNull() ?: return false
        val base = baseUrl().toHttpUrlOrNull() ?: return false
        return target.scheme == base.scheme && target.host == base.host && target.port == base.port
    }

    private fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val raw = baseUrl().trimEnd('/') + "/" + path.trimStart('/')
        val http = raw.toHttpUrlOrNull()
            ?: error("Invalid API base / path: $raw")
        val builder = http.newBuilder()
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
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

    suspend fun renameSession(id: String, title: String): Result<RuntimeSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(SessionPatchRequest(title = title))
                val request = Request.Builder()
                    .url(url("/api/v1/sessions/$id"))
                    .patch(payload.toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("rename session ${resp.code}: $body")
                    runCatching {
                        json.decodeFromString(RuntimeSession.serializer(), body)
                    }.getOrElse {
                        RuntimeSession(id = id, title = title)
                    }
                }
            }
        }

    suspend fun sessionHistory(sessionId: String, limit: Int = 100): Result<SessionHistory> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url("/api/v1/sessions/$sessionId", mapOf("limit" to limit.toString())))
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("session history ${resp.code}: $body")
                    json.decodeFromString(SessionHistory.serializer(), body)
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

    suspend fun sessionToolTrace(sessionId: String): Result<SessionToolTrace> = withContext(Dispatchers.IO) {
        runCatching {
            val id = sessionId.trim()
            require(id.isNotEmpty()) { "session id required" }
            val request = Request.Builder()
                .url(url("/api/v1/sessions/$id/tools"))
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
            val paths = listOf("/api/v1/gateways", "/api/v1/msg-gateway")
            var lastError: Throwable? = null
            for (p in paths) {
                try {
                    val request = Request.Builder().url(url(p)).get().build()
                    client.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) error("gateways ${resp.code}: $body")
                        val decoded = json.decodeFromString(GatewaysResponse.serializer(), body)
                        val list = when {
                            decoded.gateways.isNotEmpty() -> decoded.gateways
                            decoded.items.isNotEmpty() -> decoded.items
                            else -> emptyList()
                        }
                        return@runCatching list
                    }
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            throw lastError ?: error("gateways unavailable")
        }
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

    suspend fun getJson(path: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url(path)).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("${resp.code}: $body")
                body
            }
        }
    }
}
