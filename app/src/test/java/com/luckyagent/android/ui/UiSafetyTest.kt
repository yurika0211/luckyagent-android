package com.luckyagent.android.ui

import com.luckyagent.android.data.api.RuntimeSession
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSafetyTest {
    @Test
    fun structuredJsonFieldsAreIgnoredInsteadOfThrowing() {
        val payload = buildJsonObject {
            put("text", buildJsonObject { put("nested", "value") })
            put("count", 12)
        }

        assertNull(payload["text"].textOrNull())
        assertEquals(12, payload["count"].intOrNullSafe())
        for (value in listOf(null, JsonNull, JsonArray(emptyList()), payload)) {
            assertNull(value.textOrNull())
            assertNull(value.intOrNullSafe())
            assertNull(value.longOrNullSafe())
        }
        assertNull(JsonPrimitive("not a number").intOrNullSafe())
        assertEquals("hello", JsonPrimitive("hello").textOrNull())
        assertEquals(12L, payload["count"].longOrNullSafe())
    }

    @Test
    fun respectsSmallLimitsAndPreservesShortText() {
        assertEquals("short", capUiText("short", 20))
        for (limit in 0..8) {
            assertTrue(capUiText("x".repeat(100), limit).length <= limit)
        }
    }

    @Test
    fun truncationDoesNotSplitEmoji() {
        val capped = capUiText("😀".repeat(20), 19)

        assertEquals("😀".repeat(5) + "\n…内容已截断…", capped)
        assertEquals(capped, appendUiText(capped, "new chunk", 19))
    }

    @Test
    fun streamRemainsBoundedAcrossManyFlushes() {
        var content = ""
        repeat(100) {
            content = appendUiText(content, "a".repeat(10), 40)
            assertTrue(content.length <= 40)
        }
        assertEquals("a".repeat(32) + "\n…内容已截断…", content)
        assertEquals(content, appendUiText(content, "", 40))
        assertEquals("hello world", appendUiText("hello ", "world", 40))
        assertEquals("a".repeat(32) + "\n…内容已截断…", appendUiText("a".repeat(40), "more", 40))
    }

    @Test
    fun capsLargeTextWithoutGrowingUiState() {
        val capped = capUiText("x".repeat(100), 20)

        assertEquals(20, capped.length)
        assertEquals("…内容已截断…", capped.takeLast(7))
    }

    @Test
    fun duplicateAndBlankSessionsAreRemovedKeepingFirstRow() {
        val first = RuntimeSession(id = "same", title = "first")
        val duplicate = RuntimeSession(id = "same", title = "second")
        val result = dedupeSessions(listOf(first, duplicate, RuntimeSession(id = " ")))

        assertEquals(listOf(first), result)
        assertSame(first, result.single())
    }
}
