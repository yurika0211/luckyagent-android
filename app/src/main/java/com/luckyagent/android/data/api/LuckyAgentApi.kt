package com.luckyagent.android.data.api

import com.luckyagent.android.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
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
        // BASIC: never dump Authorization / body secrets at BODY level by default
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

    private fun baseUrl(): String =
        settingsRepository.snapshot().apiBase.trim().trimEnd('/')

    private fun url(path: String, query: Map<String, String> = emptyMap()): String {
        val base = baseUrl()
        val raw = if (path.startsWith("http")) path else "$base$path"
        val http = raw.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid API base or path: $raw")
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

    suspend fun memoryGraph(limit: Int = 120): Result<MemoryGraphResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url("/api/v1/memory/graph", mapOf("limit" to limit.toString())))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("memory graph ${resp.code}: $body")
                json.decodeFromString(MemoryGraphResponse.serializer(), body)
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
