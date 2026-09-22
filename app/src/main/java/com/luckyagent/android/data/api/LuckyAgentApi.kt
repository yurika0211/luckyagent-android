package com.luckyagent.android.data.api

import com.luckyagent.android.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
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

    suspend fun listMemory(limit: Int = 50): Result<List<MemoryEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url("/api/v1/memory", mapOf("limit" to limit.toString())))
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("memory ${resp.code}: $body")
                runCatching {
                    json.decodeFromString(MemoryListResponse.serializer(), body).entries
                }.getOrElse {
                    json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MemoryEntry.serializer()), body)
                }
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

    suspend fun postJson(path: String, payload: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val media = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url(url(path))
                .post(payload.toRequestBody(media))
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("${resp.code}: $body")
                body
            }
        }
    }
}
