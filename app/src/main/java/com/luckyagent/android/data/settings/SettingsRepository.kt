package com.luckyagent.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.luckyagent.android.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Serializable
data class RuntimeEndpoint(
    val id: String,
    val name: String,
    val apiBase: String,
    val apiKey: String = "",
    val wsUrl: String = "",
    val useBearer: Boolean = true,
)

data class ClientSettings(
    val apiBase: String = BuildConfig.DEFAULT_API_BASE,
    val apiKey: String = "",
    val sessionId: String = "android-main",
    val useBearer: Boolean = true,
    /** Optional absolute ws/wss URL. Empty → derive from apiBase + /api/v1/ws */
    val wsUrl: String = "",
    val runtimeEndpoints: List<RuntimeEndpoint> = emptyList(),
    val activeRuntimeEndpointId: String = "",
)

/**
 * Persists connection settings. API key goes through EncryptedSharedPreferences.
 * Mirrors GUI localStorage keys conceptually (api base + session), plus mobile auth.
 */
class SettingsRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "luckyagent_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ClientSettings> = _settings.asStateFlow()

    private fun read(): ClientSettings {
        val apiBase = prefs.getString(KEY_API_BASE, BuildConfig.DEFAULT_API_BASE) ?: BuildConfig.DEFAULT_API_BASE
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val wsUrl = prefs.getString(KEY_WS_URL, "") ?: ""
        val useBearer = prefs.getBoolean(KEY_USE_BEARER, true)
        val endpoints = prefs.getString(KEY_ENDPOINTS, null)?.let { raw ->
            runCatching { json.decodeFromString<List<RuntimeEndpoint>>(raw) }.getOrDefault(emptyList())
        }.orEmpty().ifEmpty {
            listOf(RuntimeEndpoint("endpoint-default", "Default", apiBase, apiKey, wsUrl, useBearer))
        }
        val activeId = prefs.getString(KEY_ACTIVE_ENDPOINT, null)
            ?.takeIf { id -> endpoints.any { it.id == id } }
            ?: endpoints.first().id
        val active = endpoints.first { it.id == activeId }
        return ClientSettings(
            apiBase = active.apiBase,
            apiKey = active.apiKey,
            sessionId = prefs.getString(KEY_SESSION, "android-main") ?: "android-main",
            useBearer = active.useBearer,
            wsUrl = active.wsUrl,
            runtimeEndpoints = endpoints,
            activeRuntimeEndpointId = activeId,
        )
    }

    fun update(transform: (ClientSettings) -> ClientSettings) {
        val previous = _settings.value
        val transformed = transform(previous)
        var next = transformed.let {
            it.copy(
                apiBase = it.apiBase.trim().trimEnd('/'),
                apiKey = it.apiKey.trim(),
                sessionId = it.sessionId.trim().ifEmpty { "android-main" },
                wsUrl = it.wsUrl.trim(),
            )
        }
        val legacyConnectionChanged = next.apiBase != previous.apiBase ||
            next.apiKey != previous.apiKey || next.wsUrl != previous.wsUrl || next.useBearer != previous.useBearer
        if (legacyConnectionChanged) {
            next = next.copy(
                runtimeEndpoints = next.runtimeEndpoints.map { endpoint ->
                    if (endpoint.id == next.activeRuntimeEndpointId) {
                        endpoint.copy(apiBase = next.apiBase, apiKey = next.apiKey, wsUrl = next.wsUrl, useBearer = next.useBearer)
                    } else endpoint
                },
            )
        } else if (next.runtimeEndpoints != previous.runtimeEndpoints || next.activeRuntimeEndpointId != previous.activeRuntimeEndpointId) {
            val active = next.runtimeEndpoints.firstOrNull { it.id == next.activeRuntimeEndpointId }
                ?: next.runtimeEndpoints.firstOrNull()
            if (active != null) {
                next = next.copy(
                    activeRuntimeEndpointId = active.id,
                    apiBase = active.apiBase.trim().trimEnd('/'),
                    apiKey = active.apiKey.trim(),
                    wsUrl = active.wsUrl.trim(),
                    useBearer = active.useBearer,
                )
            }
        }
        if (next == previous) return
        prefs.edit().apply {
            if (next.apiBase != previous.apiBase) putString(KEY_API_BASE, next.apiBase)
            if (next.apiKey != previous.apiKey) putString(KEY_API_KEY, next.apiKey)
            if (next.sessionId != previous.sessionId) putString(KEY_SESSION, next.sessionId)
            if (next.useBearer != previous.useBearer) putBoolean(KEY_USE_BEARER, next.useBearer)
            if (next.wsUrl != previous.wsUrl) putString(KEY_WS_URL, next.wsUrl)
            if (next.runtimeEndpoints != previous.runtimeEndpoints) putString(KEY_ENDPOINTS, json.encodeToString(next.runtimeEndpoints))
            if (next.activeRuntimeEndpointId != previous.activeRuntimeEndpointId) putString(KEY_ACTIVE_ENDPOINT, next.activeRuntimeEndpointId)
        }.apply()
        _settings.value = next
    }

    fun snapshot(): ClientSettings = _settings.value

    companion object {
        private const val KEY_API_BASE = "api_base"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SESSION = "session_id"
        private const val KEY_USE_BEARER = "use_bearer"
        private const val KEY_WS_URL = "ws_url"
        private const val KEY_ENDPOINTS = "runtime_endpoints"
        private const val KEY_ACTIVE_ENDPOINT = "active_runtime_endpoint"
    }
}
