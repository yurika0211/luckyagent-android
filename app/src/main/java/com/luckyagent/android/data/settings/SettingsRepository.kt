package com.luckyagent.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.luckyagent.android.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ClientSettings(
    val apiBase: String = BuildConfig.DEFAULT_API_BASE,
    val apiKey: String = "",
    val sessionId: String = "android-main",
    val useBearer: Boolean = true,
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
    )

    fun update(transform: (ClientSettings) -> ClientSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_API_BASE, next.apiBase.trim().trimEnd('/'))
            .putString(KEY_API_KEY, next.apiKey.trim())
            .putString(KEY_SESSION, next.sessionId.trim().ifEmpty { "android-main" })
            .putBoolean(KEY_USE_BEARER, next.useBearer)
            .apply()
        _settings.update { read() }
    }

    fun snapshot(): ClientSettings = _settings.value

    companion object {
        private const val KEY_API_BASE = "api_base"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SESSION = "session_id"
        private const val KEY_USE_BEARER = "use_bearer"
    }
}
