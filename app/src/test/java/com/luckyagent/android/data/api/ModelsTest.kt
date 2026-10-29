package com.luckyagent.android.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun chatFrameLeavesIterationLimitToServerConfig() {
        val json = Json { encodeDefaults = true }
        val frame = json.encodeToString(
            ChatOutbound.serializer(),
            ChatOutbound(
                id = "req-1",
                sessionId = "session-1",
                data = ChatOutboundData(message = "hello"),
            ),
        )
        assertTrue(frame.contains("\"max_iterations\":0"))
        assertTrue(!frame.contains("\"max_iterations\":8"))
    }

    @Test
    fun expandDraftSendsOnlyTheDraftAndIntent() {
        val json = Json { encodeDefaults = true; explicitNulls = false }
        val encoded = json.encodeToString(
            DraftExpandRequest.serializer(),
            DraftExpandRequest(message = "修一下启动"),
        )
        assertTrue(encoded.contains("\"message\":\"修一下启动\""))
        assertTrue(encoded.contains("\"intent\":\"expand\""))
        assertTrue(!encoded.contains("session_id"))

        val decoded = json.decodeFromString<DraftExpandResponse>("{\"response\":\"补全后的请求\"}")
        assertEquals("补全后的请求", decoded.response)
    }

    @Test
    fun gatewayStartRequestCarriesOptionalToken() {
        val json = Json { explicitNulls = false }
        val request = json.decodeFromString<GatewayStartRequest>("{\"token\":\"tg-token\"}")

        assertEquals("tg-token", request.token)
    }

    @Test
    fun modelsAreGroupedByNormalizedKind() {
        val models = listOf(
            ModelRef(id = "chat-a", kind = "chat"),
            ModelRef(id = "vision-a", kind = "VISION"),
            ModelRef(id = "chat-b", kind = "chat"),
        )

        val grouped = modelsByKind(models)

        assertEquals(listOf("chat-a", "chat-b"), grouped["chat"]?.map(ModelRef::id))
        assertEquals(listOf("vision-a"), grouped["vision"]?.map(ModelRef::id))
        assertTrue("all runtime kinds are represented", FunctionalModelKinds.map { it.wireValue }.containsAll(
            listOf("chat", "vision", "embedding", "transcription", "image", "tts", "reranker", "compact"),
        ))
    }

    @Test
    fun modelSheetKeepsVisionAndCompactBesideChat() {
        val models = listOf(
            ModelRef(id = "grok", kind = "chat", current = true),
            ModelRef(id = "vision-a", kind = "vision"),
            ModelRef(id = "compact-a", kind = "compact", current = true),
            ModelRef(id = "custom-a", kind = "planner"),
        )

        val sections = modelSheetSections(models)

        assertEquals(listOf("chat", "vision", "compact", "planner"), sections.map { it.first })
        assertEquals(listOf("vision-a"), sections.first { it.first == "vision" }.second.map(ModelRef::id))
        assertEquals(listOf("compact-a"), sections.first { it.first == "compact" }.second.map(ModelRef::id))
    }

    @Test
    fun kindLabelsHaveChineseFallback() {
        assertEquals("压缩", modelKindLabel("compact"))
        assertEquals("对话", modelKindLabel("chat"))
        assertEquals("custom", modelKindLabel("custom"))
    }

    @Test
    fun taskExecutionSessionWinsOverOwnerSession() {
        val task = TaskSummary(
            id = "task-1",
            sessionId = "parent-session",
            executionSessionId = "subagent-session",
            source = "http",
            mode = "parallel",
            status = "running",
            description = "child work",
        )

        assertEquals("subagent-session", task.executionSession())
    }

    @Test
    fun metadataExecutionSessionWinsOverLegacyOwnerLink() {
        val record = TaskRecord(
            id = "task-2",
            sessionId = "owner-session",
            metadata = mapOf("execution_session_id" to "metadata-execution"),
        )

        assertEquals("metadata-execution", record.toTaskSummary().executionSession())
    }

    @Test
    fun sessionHistory304SkipsDecoding() {
        val fetch = sessionHistoryFetch(304, "") { error("body must not be decoded") }
        assertTrue(fetch.notModified)
        assertEquals(null, fetch.history)
    }

    @Test
    fun blankSuccessEndsTheRefreshWithoutDecoding() {
        val fetch = sessionHistoryFetch(200, "   ") { error("body must not be decoded") }
        assertTrue(fetch.notModified)
        assertEquals(null, fetch.history)
    }

    @Test
    fun sessionHistoryErrorDoesNotLookUnchanged() {
        val error = runCatching { sessionHistoryFetch(500, "boom") { error("unused") } }.exceptionOrNull()
        assertTrue(error?.message?.contains("500") == true)
    }

    @Test
    fun sessionHistoryDecodesStorageAndTruncationFields() {
        val raw = """
            {
              "id": "s1",
              "title": "demo",
              "message_count": 2,
              "format": "segment_v1",
              "byte_size": 12345,
              "content_truncated": true,
              "messages": [
                {
                  "role": "tool",
                  "name": "file_read",
                  "content": "preview",
                  "blob_hash": "abc",
                  "blob_bytes": 99999,
                  "client_message_id": "outbound-1"
                }
              ],
              "limit": 40,
              "offset": 0,
              "returned": 1,
              "has_more": false
            }
        """.trimIndent()

        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(SessionHistory.serializer(), raw)

        assertEquals("segment_v1", parsed.format)
        assertEquals(12345L, parsed.byteSize)
        assertEquals(true, parsed.contentTruncated)
        assertEquals("abc", parsed.messages.single().blobHash)
        assertEquals(99999, parsed.messages.single().blobBytes)
        assertEquals("outbound-1", parsed.messages.single().clientMessageId)
    }
}
