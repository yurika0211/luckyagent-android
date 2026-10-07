package com.luckyagent.android.data.api

enum class LuckyAction {
    On,
    Off,
    Status,
    Cancel,
    Unknown,
}

data class LuckyCommand(
    val action: LuckyAction,
)

/**
 * Recognizes the runtime collector commands used by the gateways:
 * /lucky on, off, status, and cancel.
 */
fun parseLuckyCommand(text: String): LuckyCommand? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/")) return null
    val body = trimmed.removePrefix("/").trim()
    val parts = body.split(Regex("\\s+"), limit = 2)
    if (!parts.first().equals("lucky", ignoreCase = true)) return null
    val action = parts.getOrNull(1)?.trim()?.lowercase().orEmpty()
    val parsed = when (action) {
        "on", "start" -> LuckyAction.On
        "off", "submit", "send" -> LuckyAction.Off
        "status" -> LuckyAction.Status
        "cancel", "abort" -> LuckyAction.Cancel
        else -> LuckyAction.Unknown
    }
    return LuckyCommand(parsed)
}
