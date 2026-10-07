package com.luckyagent.android.data.settings

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Payload printed by `lh qr`.
 * v is 1. token is a process-local key that dies when the API restarts.
 */
@Serializable
data class PairingQr(
    val v: Int = 0,
    val url: String = "",
    val token: String = "",
    @SerialName("expires_at") val expiresAt: String = "",
    @SerialName("ttl_hours") val ttlHours: Int = 0,
    val name: String = "",
) {
    fun toEndpoint(id: String = UUID.randomUUID().toString()): RuntimeEndpoint {
        val label = name.trim().ifBlank { "LuckyAgent" }
        val hours = if (ttlHours > 0) " · ${ttlHours}h" else ""
        return RuntimeEndpoint(
            id = id,
            name = label + hours,
            apiBase = url.trim().trimEnd('/'),
            apiKey = token.trim(),
            useBearer = false,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): PairingQr {
            val text = raw.trim()
            if (!text.startsWith("{")) {
                throw IllegalArgumentException("这不是 LuckyAgent 配对码")
            }
            val parsed = json.decodeFromString(PairingQr.serializer(), text)
            if (parsed.v != 1 || parsed.url.isBlank() || parsed.token.isBlank()) {
                throw IllegalArgumentException("配对码缺少地址或临时 key")
            }
            if (!parsed.url.startsWith("http://") && !parsed.url.startsWith("https://")) {
                throw IllegalArgumentException("配对码里的地址不可用")
            }
            return parsed
        }
    }
}
