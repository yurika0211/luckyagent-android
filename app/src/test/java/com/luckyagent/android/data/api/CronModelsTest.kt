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

    @Test
    fun deviceRegistrationKeepsCapabilityDefaultsAndPendingUsesResultTaskFields() {
        val registration = json.encodeToString(
            CronDeviceRegistration(id = "phone"),
        )
        assertTrue(registration.contains("\"channel\":\"android\""))
        assertTrue(registration.contains("\"notifications_enabled\":true"))
        assertTrue(registration.contains("\"channel_enabled\":true"))

        val pending = json.decodeFromString(
            CronPendingResponse.serializer(),
            """
            {
              "device_id":"phone",
              "items":[{
                "execution":{"id":"exec-1","task_id":"task-1","scheduled_at":"2026-11-27T09:00:00Z","status":"succeeded","result":"done"},
                "task":{"id":"task-1","name":"早报","schedule":"每天09:00","status":"active","next_run_at":"2026-11-28T09:00:00Z","channels":["android"]},
                "delivery":{"id":"delivery-1","execution_id":"exec-1","channel":"android","status":"pending"}
              }]
            }
            """.trimIndent(),
        )
        assertEquals("exec-1", pending.items.single().execution.id)
        assertEquals("2026-11-28T09:00:00Z", pending.items.single().task.nextRunAt)
        assertEquals("exec-1", pending.items.single().delivery.executionId)
    }
}
