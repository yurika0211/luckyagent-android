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
    val notifyOnChatCompleted: Boolean = true,
    val notifyOnCron: Boolean = true,
    val notifyOnSubagent: Boolean = true,
    val notifyOnBackground: Boolean = true,
    /** Off: only finished, failed, or blocked work alerts. On: progress updates a quiet notice. */
    val notifyOnProgress: Boolean = false,
    val chatBackgroundFile: String = "",
    val chatBackgroundDim: Int = 12,
    val avatarFile: String = "",
    /** 0 全透明，100 不透明。 */
    val composerOpacity: Int = 78,
    val bubbleOpacity: Int = 92,
    val chromeOpacity: Int = 62,
    val cardOpacity: Int = 82,
    val drawerOpacity: Int = 90,
    /** App text scale, persisted locally and applied to the Material typography. */
    val fontScale: Float = 1.0f,
    /** `serif` uses the Android Noto Serif CJK fallback; `sans` uses Noto Sans CJK. */
    val fontFamily: String = "sans",
    /** `runtime` keeps the computer connection. `standalone` talks to a model URL on the phone. */
    val connectionMode: String = "runtime",
    val standaloneApiBase: String = "",
    val standaloneApiKey: String = "",
    val standaloneModel: String = "",
    val standaloneSessionId: String = "standalone-main",
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
            notifyOnChatCompleted = prefs.getBoolean(KEY_NOTIFY_ON_CHAT_COMPLETED, true),
            notifyOnCron = prefs.getBoolean(KEY_NOTIFY_ON_CRON, true),
            notifyOnSubagent = prefs.getBoolean(KEY_NOTIFY_ON_SUBAGENT, true),
            notifyOnBackground = prefs.getBoolean(KEY_NOTIFY_ON_BACKGROUND, true),
            notifyOnProgress = prefs.getBoolean(KEY_NOTIFY_ON_PROGRESS, false),
            chatBackgroundFile = prefs.getString(KEY_CHAT_BACKGROUND, "") ?: "",
            chatBackgroundDim = prefs.getInt(KEY_CHAT_BACKGROUND_DIM, 12).coerceIn(0, 70),
            avatarFile = prefs.getString(KEY_AVATAR, "") ?: "",
            composerOpacity = prefs.getInt(KEY_COMPOSER_OPACITY, 78).coerceIn(0, 100),
            bubbleOpacity = prefs.getInt(KEY_BUBBLE_OPACITY, 92).coerceIn(0, 100),
            chromeOpacity = prefs.getInt(KEY_CHROME_OPACITY, 62).coerceIn(0, 100),
            cardOpacity = prefs.getInt(KEY_CARD_OPACITY, 82).coerceIn(0, 100),
            drawerOpacity = prefs.getInt(KEY_DRAWER_OPACITY, 90).coerceIn(0, 100),
            fontScale = prefs.getFloat(KEY_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.35f),
            fontFamily = prefs.getString(KEY_FONT_FAMILY, "sans")
                ?.lowercase()
                ?.takeIf { it == "serif" || it == "sans" }
                ?: "sans",
            connectionMode = prefs.getString(KEY_CONNECTION_MODE, "runtime")
                ?.takeIf { it == "standalone" } ?: "runtime",
            standaloneApiBase = prefs.getString(KEY_STANDALONE_BASE, "") ?: "",
            standaloneApiKey = prefs.getString(KEY_STANDALONE_KEY, "") ?: "",
            standaloneModel = prefs.getString(KEY_STANDALONE_MODEL, "") ?: "",
            standaloneSessionId = prefs.getString(KEY_STANDALONE_SESSION, "standalone-main") ?: "standalone-main",
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
                fontScale = it.fontScale.coerceIn(0.85f, 1.35f),
                fontFamily = it.fontFamily.lowercase().let { family ->
                    if (family == "sans") "sans" else "serif"
                },
                connectionMode = if (it.connectionMode == "standalone") "standalone" else "runtime",
                standaloneApiBase = it.standaloneApiBase.trim().trimEnd('/'),
                standaloneApiKey = it.standaloneApiKey.trim(),
                standaloneModel = it.standaloneModel.trim(),
                standaloneSessionId = it.standaloneSessionId.trim().ifEmpty { "standalone-main" },
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
            if (next.notifyOnChatCompleted != previous.notifyOnChatCompleted) putBoolean(KEY_NOTIFY_ON_CHAT_COMPLETED, next.notifyOnChatCompleted)
            if (next.notifyOnCron != previous.notifyOnCron) putBoolean(KEY_NOTIFY_ON_CRON, next.notifyOnCron)
            if (next.notifyOnSubagent != previous.notifyOnSubagent) putBoolean(KEY_NOTIFY_ON_SUBAGENT, next.notifyOnSubagent)
            if (next.notifyOnBackground != previous.notifyOnBackground) putBoolean(KEY_NOTIFY_ON_BACKGROUND, next.notifyOnBackground)
            if (next.notifyOnProgress != previous.notifyOnProgress) putBoolean(KEY_NOTIFY_ON_PROGRESS, next.notifyOnProgress)
            if (next.chatBackgroundFile != previous.chatBackgroundFile) putString(KEY_CHAT_BACKGROUND, next.chatBackgroundFile)
            if (next.chatBackgroundDim != previous.chatBackgroundDim) putInt(KEY_CHAT_BACKGROUND_DIM, next.chatBackgroundDim.coerceIn(0, 70))
            if (next.avatarFile != previous.avatarFile) putString(KEY_AVATAR, next.avatarFile)
            if (next.composerOpacity != previous.composerOpacity) putInt(KEY_COMPOSER_OPACITY, next.composerOpacity.coerceIn(0, 100))
            if (next.bubbleOpacity != previous.bubbleOpacity) putInt(KEY_BUBBLE_OPACITY, next.bubbleOpacity.coerceIn(0, 100))
            if (next.chromeOpacity != previous.chromeOpacity) putInt(KEY_CHROME_OPACITY, next.chromeOpacity.coerceIn(0, 100))
            if (next.cardOpacity != previous.cardOpacity) putInt(KEY_CARD_OPACITY, next.cardOpacity.coerceIn(0, 100))
            if (next.drawerOpacity != previous.drawerOpacity) putInt(KEY_DRAWER_OPACITY, next.drawerOpacity.coerceIn(0, 100))
            if (next.fontScale != previous.fontScale) putFloat(KEY_FONT_SCALE, next.fontScale.coerceIn(0.85f, 1.35f))
            if (next.fontFamily != previous.fontFamily) putString(KEY_FONT_FAMILY, next.fontFamily)
            if (next.connectionMode != previous.connectionMode) putString(KEY_CONNECTION_MODE, next.connectionMode)
            if (next.standaloneApiBase != previous.standaloneApiBase) putString(KEY_STANDALONE_BASE, next.standaloneApiBase)
            if (next.standaloneApiKey != previous.standaloneApiKey) putString(KEY_STANDALONE_KEY, next.standaloneApiKey)
            if (next.standaloneModel != previous.standaloneModel) putString(KEY_STANDALONE_MODEL, next.standaloneModel)
            if (next.standaloneSessionId != previous.standaloneSessionId) putString(KEY_STANDALONE_SESSION, next.standaloneSessionId)
        }.apply()
        _settings.value = next
    }

    fun snapshot(): ClientSettings = _settings.value

    /** Cards the user closed from chat. The task itself stays on the computer. */
    fun dismissedWorkCardIds(): Set<String> = synchronized(prefs) {
        prefs.getStringSet(KEY_DISMISSED_WORK_CARDS, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun dismissWorkCard(id: String) {
        val cardId = id.trim()
        if (cardId.isEmpty()) return
        synchronized(prefs) {
            val next = dismissedWorkCardIds() + cardId
            prefs.edit().putStringSet(KEY_DISMISSED_WORK_CARDS, next).apply()
        }
    }

    /**
     * Cards hidden from the current session only. The computer still has the
     * task, background item, trace, and memory.
     */
    fun hiddenSessionCards(): Set<String> = synchronized(prefs) {
        prefs.getStringSet(KEY_HIDDEN_SESSION_CARDS, emptySet()).orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun hideSessionCard(key: String) {
        val cardKey = key.trim()
        if (cardKey.isEmpty()) return
        synchronized(prefs) {
            val next = hiddenSessionCards() + cardKey
            prefs.edit().putStringSet(KEY_HIDDEN_SESSION_CARDS, next).apply()
        }
    }

    /** Last visible chat position, keyed by session so switching chats keeps context. */
    fun scrollAnchor(sessionId: String): Pair<Int, Int>? = synchronized(prefs) {
        if (sessionId.isBlank()) return@synchronized null
        val raw = prefs.getString(KEY_SCROLL_ANCHORS, null) ?: return@synchronized null
        val anchor = runCatching { json.decodeFromString<Map<String, List<Int>>>(raw)[sessionId] }.getOrNull()
            ?: return@synchronized null
        if (anchor.size < 2) null else anchor[0].coerceAtLeast(0) to anchor[1].coerceAtLeast(0)
    }

    fun saveScrollAnchor(sessionId: String, index: Int, offset: Int) {
        if (sessionId.isBlank()) return
        synchronized(prefs) {
            val current = prefs.getString(KEY_SCROLL_ANCHORS, null)
                ?.let { runCatching { json.decodeFromString<Map<String, List<Int>>>(it) }.getOrDefault(emptyMap()) }
                .orEmpty().toMutableMap()
            current[sessionId] = listOf(index.coerceAtLeast(0), offset.coerceAtLeast(0))
            prefs.edit().putString(KEY_SCROLL_ANCHORS, json.encodeToString(current)).apply()
        }
    }

    fun eventCursor(sessionId: String): String = synchronized(prefs) {
        val raw = prefs.getString(KEY_EVENT_CURSORS, null) ?: return@synchronized ""
        runCatching { json.decodeFromString<Map<String, String>>(raw)[sessionId].orEmpty() }.getOrDefault("")
    }

    fun saveEventCursor(sessionId: String, cursor: String) {
        if (sessionId.isBlank() || cursor.isBlank()) return
        synchronized(prefs) {
            val current = prefs.getString(KEY_EVENT_CURSORS, null)
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap()) }
                .orEmpty()
                .toMutableMap()
            current[sessionId] = cursor
            prefs.edit().putString(KEY_EVENT_CURSORS, json.encodeToString(current)).apply()
        }
    }

    companion object {
        private const val KEY_API_BASE = "api_base"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SESSION = "session_id"
        private const val KEY_USE_BEARER = "use_bearer"
        private const val KEY_WS_URL = "ws_url"
        private const val KEY_ENDPOINTS = "runtime_endpoints"
        private const val KEY_ACTIVE_ENDPOINT = "active_runtime_endpoint"
        private const val KEY_EVENT_CURSORS = "event_cursors"
        private const val KEY_SCROLL_ANCHORS = "scroll_anchors"
        private const val KEY_NOTIFY_ON_CHAT_COMPLETED = "notify_on_chat_completed"
        private const val KEY_NOTIFY_ON_CRON = "notify_on_cron"
        private const val KEY_NOTIFY_ON_SUBAGENT = "notify_on_subagent"
        private const val KEY_NOTIFY_ON_BACKGROUND = "notify_on_background"
        private const val KEY_NOTIFY_ON_PROGRESS = "notify_on_progress"
        private const val KEY_CHAT_BACKGROUND = "chat_background_file"
        private const val KEY_CHAT_BACKGROUND_DIM = "chat_background_dim"
        private const val KEY_AVATAR = "avatar_file"
        private const val KEY_COMPOSER_OPACITY = "composer_opacity"
        private const val KEY_BUBBLE_OPACITY = "bubble_opacity"
        private const val KEY_CHROME_OPACITY = "chrome_opacity"
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_DRAWER_OPACITY = "drawer_opacity"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_CONNECTION_MODE = "connection_mode"
        private const val KEY_STANDALONE_BASE = "standalone_api_base"
        private const val KEY_STANDALONE_KEY = "standalone_api_key"
        private const val KEY_STANDALONE_MODEL = "standalone_model"
        private const val KEY_STANDALONE_SESSION = "standalone_session_id"
        private const val KEY_DISMISSED_WORK_CARDS = "dismissed_work_cards"
        private const val KEY_HIDDEN_SESSION_CARDS = "hidden_session_cards"
    }
}

fun Int.opacityFraction(): Float = coerceIn(0, 100) / 100f
