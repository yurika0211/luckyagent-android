package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.settings.ClientSettings

internal data class ManualConnection(
    val apiBase: String,
    val apiKey: String,
    val useBearer: Boolean,
)

enum class ComputerLink { Unknown, Checking, Ready, Failed }

/**
 * Stay on the connection step until this session has authenticated to the
 * computer. A saved address is not enough: the key still has to be accepted.
 * A later socket reconnect does not return here. Standalone model chat does not
 * use this computer connection.
 */
internal fun needsConnectionSetup(settings: ClientSettings, link: ComputerLink): Boolean {
    if (settings.connectionMode == "standalone") return false
    return settings.apiBase.isBlank() || settings.apiKey.isBlank() || link != ComputerLink.Ready
}

/** Health check does not require a key, so a rejected key must be recognized from an authenticated call. */
internal fun connectionFailureMessage(error: Throwable?): String {
    val text = error?.message.orEmpty()
    val rejected = text.contains("401") || text.contains("403") || text.contains("api key", ignoreCase = true)
    return if (rejected) {
        "密钥没有通过这台电脑的验证。可以重试、改地址或重新扫码。"
    } else {
        "没能连上这台电脑。可以重试、改地址或重新扫码。"
    }
}

/** Accepts an address and key. A leading "Bearer " selects that header; otherwise the key is sent as X-API-Key. */
internal fun manualConnection(rawBase: String, rawKey: String): ManualConnection? {
    val base = rawBase.trim().trimEnd('/')
    if (!base.startsWith("http://") && !base.startsWith("https://")) return null
    val raw = rawKey.trim()
    val bearer = raw.startsWith("Bearer ", ignoreCase = true)
    val key = if (bearer) raw.substringAfter(' ').trim() else raw
    if (key.isBlank()) return null
    return ManualConnection(base, key, bearer)
}

internal fun connectionHostLabel(apiBase: String, endpointName: String? = null): String {
    val name = endpointName?.trim()?.substringBefore(" ·")?.takeIf { label ->
        label.isNotBlank() && !label.equals("Default", ignoreCase = true) && !label.equals("Runtime", ignoreCase = true)
    }
    if (name != null) return name
    val withoutScheme = apiBase.trim().substringAfter("://", apiBase.trim())
    return withoutScheme.substringBefore('/').substringBefore(':').ifBlank { "这台电脑" }
}
