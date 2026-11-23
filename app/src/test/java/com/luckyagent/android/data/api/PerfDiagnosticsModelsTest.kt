package com.luckyagent.android.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfDiagnosticsModelsTest {
    @Test
    fun uploadUsesServerFieldNames() {
        val encoded = Json.encodeToString(
            PerfDiagnosticsUploadRequest(
                captureId = "capture",
                sequence = 0,
                endpointHash = "endpoint",
                sessionHash = "session",
                appVersion = "debug",
                deviceModel = "emulator",
                screenClass = "narrow",
                startedAt = "start",
                endedAt = "end",
                sampleCount = 1,
                payloadBase64 = "payload",
                payloadSHA256 = "sha",
            ),
        )

        assertTrue(encoded.contains("\"capture_id\""))
        assertTrue(encoded.contains("\"payload_base64\""))
        assertTrue(encoded.contains("\"payload_sha256\""))
        assertTrue(!encoded.contains("\"captureId\""))
    }
}
