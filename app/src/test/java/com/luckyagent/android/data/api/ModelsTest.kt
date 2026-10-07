package com.luckyagent.android.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
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
            listOf("chat", "vision", "embedding", "transcription", "image", "tts", "reranker"),
        ))
    }

    @Test
    fun kindLabelsHaveChineseFallback() {
        assertEquals("对话", modelKindLabel("chat"))
        assertEquals("custom", modelKindLabel("custom"))
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
                  "blob_bytes": 99999
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
    }
}
