package com.luckyagent.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.luckyagent.android.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClientSettings(
    val apiBase: String = BuildConfig.DEFAULT_API_BASE,
    val apiKey: String = "",
    val sessionId: String = "android-main",
    val useBearer: Boolean = true,
    /** Optional absolute ws/wss URL. Empty → derive from apiBase + /api/v1/ws */
    val wsUrl: String = "",
)

/**
 * Persists connection settings. API key goes through EncryptedSharedPreferences.
 * Mirrors GUI localStorage keys conceptually (api base + session), plus mobile auth.
 */
class SettingsRepository(context: Context) {
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

    private fun read(): ClientSettings = ClientSettings(
        apiBase = prefs.getString(KEY_API_BASE, BuildConfig.DEFAULT_API_BASE) ?: BuildConfig.DEFAULT_API_BASE,
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        sessionId = prefs.getString(KEY_SESSION, "android-main") ?: "android-main",
        useBearer = prefs.getBoolean(KEY_USE_BEARER, true),
        wsUrl = prefs.getString(KEY_WS_URL, "") ?: "",
    )

    fun update(transform: (ClientSettings) -> ClientSettings) {
        val previous = _settings.value
        val next = transform(previous).let {
            it.copy(
                apiBase = it.apiBase.trim().trimEnd('/'),
                apiKey = it.apiKey.trim(),
                sessionId = it.sessionId.trim().ifEmpty { "android-main" },
                wsUrl = it.wsUrl.trim(),
            )
        }
        if (next == previous) return
        prefs.edit().apply {
            if (next.apiBase != previous.apiBase) putString(KEY_API_BASE, next.apiBase)
            if (next.apiKey != previous.apiKey) putString(KEY_API_KEY, next.apiKey)
            if (next.sessionId != previous.sessionId) putString(KEY_SESSION, next.sessionId)
            if (next.useBearer != previous.useBearer) putBoolean(KEY_USE_BEARER, next.useBearer)
            if (next.wsUrl != previous.wsUrl) putString(KEY_WS_URL, next.wsUrl)
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
    }
}
