package com.luckyagent.android.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronModelsTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun createRequestKeepsNaturalScheduleAndDestination() {
        val encoded = json.encodeToString(
            CronCreateRequest(
                name = "早报",
                schedule = "每天 09:00",
                command = "总结昨天的消息",
                metadata = mapOf("session_id" to "session-1", "platform" to "android"),
            ),
        )

        assertTrue(encoded.contains("\"schedule\":\"每天 09:00\""))
        assertTrue(encoded.contains("\"session_id\":\"session-1\""))
        assertTrue(encoded.contains("\"platform\":\"android\""))
        assertEquals("pause", json.decodeFromString(CronActionRequest.serializer(), "{\"action\":\"pause\"}").action)
    }
}
