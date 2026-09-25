package com.luckyagent.android.ui.util

import com.luckyagent.android.data.api.TokenUsage
import java.util.Locale

/**
 * Token count formatting for chat message metadata.
 *
 * Compact labels must never strip a trailing unit suffix. The previous
 * implementation formatted 674016 as "674.0k" and then removed ".0k",
 * which left a bare "674" and made the summary disagree with the detail rows.
 */
object TokenFormat {
    fun usageSummary(usage: TokenUsage): String? {
        val total = usage.totalTokens
        if (total <= 0 && usage.inputTokens <= 0 && usage.outputTokens <= 0 && usage.cachedInputTokens <= 0) {
            return null
        }
        val shownTotal = when {
            total > 0 -> total
            else -> usage.inputTokens + usage.outputTokens
        }
        val parts = mutableListOf<String>()
        if (shownTotal > 0) {
            parts += "${compact(shownTotal)} tokens"
        }
        if (usage.cachedInputTokens > 0) {
            parts += "缓存 ${compact(usage.cachedInputTokens)}"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Expanded detail rows: full integer with thousands separators. */
    fun full(tokens: Int): String = String.format(Locale.US, "%,d", tokens)

    /**
     * Collapsed metadata compact label.
     * 999 -> "999"
     * 1000 -> "1k"
     * 674016 -> "674k"
     * 1_500_000 -> "1.5M"
     */
    fun compact(tokens: Int): String {
        if (tokens < 1000) return tokens.toString()
        if (tokens < 1_000_000) {
            val tenths = (tokens + 50) / 100 // round to 0.1k
            val whole = tenths / 10
            val frac = tenths % 10
            return if (frac == 0) "${whole}k" else "$whole.${frac}k"
        }
        val tenths = (tokens + 50_000) / 100_000 // round to 0.1M
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0) "${whole}M" else "$whole.${frac}M"
    }
}
