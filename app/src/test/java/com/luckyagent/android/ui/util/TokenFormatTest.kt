package com.luckyagent.android.ui.util

import com.luckyagent.android.data.api.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenFormatTest {
    @Test
    fun compact_keepsKSuffixForWholeThousands() {
        assertEquals("999", TokenFormat.compact(999))
        assertEquals("1k", TokenFormat.compact(1000))
        assertEquals("1.5k", TokenFormat.compact(1500))
        // Regression: 674016 must NOT become bare "674"
        assertEquals("674k", TokenFormat.compact(674016))
        assertEquals("674.1k", TokenFormat.compact(674050))
    }

    @Test
    fun compact_supportsMillions() {
        assertEquals("1M", TokenFormat.compact(1_000_000))
        assertEquals("1.5M", TokenFormat.compact(1_500_000))
    }

    @Test
    fun full_usesThousandsSeparators() {
        assertEquals("674,016", TokenFormat.full(674016))
        assertEquals("1,234", TokenFormat.full(1234))
    }

    @Test
    fun usageSummary_includesCacheWhenPresent() {
        val usage = TokenUsage(
            inputTokens = 667330,
            outputTokens = 5713,
            totalTokens = 674016,
            cachedInputTokens = 620000,
            model = "grok-4.5",
        )
        assertEquals("674k tokens · 缓存 620k", TokenFormat.usageSummary(usage))
    }

    @Test
    fun usageSummary_omitsCacheWhenZero() {
        val usage = TokenUsage(
            inputTokens = 100,
            outputTokens = 20,
            totalTokens = 120,
            cachedInputTokens = 0,
        )
        assertEquals("120 tokens", TokenFormat.usageSummary(usage))
        assertNull(TokenFormat.usageSummary(TokenUsage()))
    }
}
