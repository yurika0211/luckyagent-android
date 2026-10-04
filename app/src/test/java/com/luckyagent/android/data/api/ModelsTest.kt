package com.luckyagent.android.data.api

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
}
