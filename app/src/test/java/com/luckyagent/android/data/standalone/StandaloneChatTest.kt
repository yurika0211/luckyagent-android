package com.luckyagent.android.data.standalone

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneChatTest {
    @Test
    fun chatUrlAppendsCompletionsOnce() {
        assertEquals(
            "https://api.example.test/v1/chat/completions",
            chatCompletionsUrl("https://api.example.test/v1"),
        )
        assertEquals(
            "https://api.example.test/v1/chat/completions",
            chatCompletionsUrl("https://api.example.test/v1/chat/completions"),
        )
    }

    @Test
    fun chatUrlNormalizesHostAndPreservesQueryParameters() {
        assertEquals(
            "https://api.example.test/v1/chat/completions?tenant=mobile",
            chatCompletionsUrl("https://api.example.test?tenant=mobile"),
        )
        assertEquals(
            "https://api.example.test/v1/chat/completions?tenant=mobile",
            chatCompletionsUrl("https://api.example.test/v1?tenant=mobile"),
        )
        assertEquals(
            "https://api.example.test/v1/chat/completions?tenant=mobile",
            chatCompletionsUrl("https://api.example.test/v1/chat/completions?tenant=mobile"),
        )
        assertEquals(
            "https://api.example.test/v1/models?tenant=mobile",
            modelsUrl("https://api.example.test/v1?tenant=mobile"),
        )
    }

    @Test
    fun chatUrlAppendsToGatewaysWithTheirOwnVersionPath() {
        // Saving a config that worked before this change must keep working:
        // these gateways carry their own prefix instead of /v1.
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            chatCompletionsUrl("https://open.bigmodel.cn/api/paas/v4"),
        )
        assertEquals(
            "https://host.test/llm/openai/chat/completions",
            chatCompletionsUrl("https://host.test/llm/openai"),
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/models",
            modelsUrl("https://open.bigmodel.cn/api/paas/v4"),
        )
        // An unknown sub-path is appended, not rejected.
        assertEquals(
            "https://api.example.test/v1/chat/chat/completions",
            chatCompletionsUrl("https://api.example.test/v1/chat"),
        )
    }

    @Test
    fun chatUrlRejectsUnsupportedScheme() {
        val error = runCatching { chatCompletionsUrl("ftp://api.example.test/v1") }.exceptionOrNull()
        assertTrue(error?.message?.contains("http://") == true)
    }

    @Test
    fun emptyModelUsesFirstModelFromModelsEndpoint() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"model-a"},{"id":"model-b"}]}"""))
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

            val settings = StandaloneSettings(
                apiBase = server.url("/v1?tenant=mobile").toString(),
                apiKey = "sample-key",
            )
            val result = StandaloneChatClient().check(settings)

            assertEquals("model-a", result.getOrThrow())
            val modelsRequest = server.takeRequest()
            assertEquals("/v1/models?tenant=mobile", modelsRequest.path)
            assertEquals("Bearer sample-key", modelsRequest.getHeader("Authorization"))
            val completionRequest = server.takeRequest()
            assertEquals("/v1/chat/completions?tenant=mobile", completionRequest.path)
            assertEquals("Bearer sample-key", completionRequest.getHeader("Authorization"))
            assertTrue(completionRequest.body.readUtf8().contains("\"model\":\"model-a\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun namedModelSkipsModelDiscovery() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))

            val settings = StandaloneSettings(
                apiBase = server.url("/v1").toString(),
                apiKey = "sample-key",
                model = "my-model",
            )
            val result = StandaloneChatClient().complete(
                settings,
                listOf(mapOf("role" to "user", "content" to "hi")),
            )

            assertEquals("ok", result.getOrThrow())
            // A typed model name means exactly one request: no /models probe.
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertTrue(request.body.readUtf8().contains("\"model\":\"my-model\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun checkAcceptsReasoningOnlyAnswer() {
        val server = MockWebServer()
        server.start()
        try {
            // A reasoning model that spends the whole budget on reasoning_content.
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"","reasoning_content":"thinking"},"finish_reason":"length"}]}""",
                ),
            )

            val settings = StandaloneSettings(
                apiBase = server.url("/v1").toString(),
                apiKey = "sample-key",
                model = "reasoner",
            )
            val result = StandaloneChatClient().check(settings)

            assertEquals("reasoner", result.getOrThrow())
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertTrue(request.body.readUtf8().contains("\"model\":\"reasoner\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun checkFailureNamesTheRequestedUrl() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"no such model"}}"""))

            val settings = StandaloneSettings(
                apiBase = server.url("/v1").toString(),
                apiKey = "sample-key",
                model = "missing",
            )
            val error = StandaloneChatClient().check(settings).exceptionOrNull()

            assertTrue(error?.message?.contains("no such model") == true)
            assertTrue(error?.message?.contains("/v1/chat/completions") == true)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun completionReadsMessageContent() {
        val body = """{"choices":[{"message":{"role":"assistant","content":" hello "}}]}"""
        assertEquals("hello", extractCompletion(body))
    }

    @Test
    fun compactKeepsRecentMessagesAndFoldsOlderOnes() {
        val session = StandaloneSession(
            id = "s",
            messages = listOf(
                StandaloneMessage("user", "记住口令是 blue-fox"),
                StandaloneMessage("assistant", "已记住"),
                StandaloneMessage("user", "最近的问题"),
                StandaloneMessage("assistant", "最近的回答"),
            ),
        )
        val compacted = compactStandaloneSession(session, keepRecentTokens = 8)
        assertTrue(compacted != null)
        assertTrue(compacted!!.summary.contains("blue-fox"))
        assertTrue(compacted.messages.none { it.content.contains("blue-fox") })
        assertTrue(compacted.messages.last().content.contains("最近的回答"))
    }

    @Test
    fun shortSessionIsNotCompacted() {
        val session = StandaloneSession(
            id = "s",
            messages = listOf(StandaloneMessage("user", "hi"), StandaloneMessage("assistant", "hello")),
        )
        assertNull(compactStandaloneSession(session))
    }

    @Test
    fun promptCarriesSummaryWithoutWritingTheKey() {
        val session = StandaloneSession(id = "s", summary = "口令是 blue-fox", messages = emptyList())
        val prompt = buildStandaloneMessages(session, "口令是什么")
        assertTrue(prompt.first()["content"]!!.contains("blue-fox"))
        assertEquals("user", prompt.last()["role"])
        assertTrue(prompt.none { it["content"]!!.contains("sk-") })
    }

    @Test
    fun promptAddsCurrentQuestionOnlyOnceAfterCompletedHistory() {
        val session = StandaloneSession(
            id = "s",
            messages = listOf(
                StandaloneMessage("user", "之前的问题"),
                StandaloneMessage("assistant", "之前的回答"),
            ),
        )
        val prompt = buildStandaloneMessages(session, "现在的问题")
        assertEquals(
            1,
            prompt.count { it["role"] == "user" && it["content"] == "现在的问题" },
        )
    }

    @Test
    fun laterTurnsStillCarryTheSearchResult() {
        val session = StandaloneSession(
            id = "s",
            messages = listOf(
                StandaloneMessage("user", "/search 口令"),
                StandaloneMessage("assistant", "搜索「口令」\nblue-fox", kind = "search"),
                StandaloneMessage("assistant", "查到了"),
            ),
        )
        val prompt = buildStandaloneMessages(session, "再重复一次")
        assertTrue(prompt.any { it["content"]!!.contains("blue-fox") })
        assertEquals("再重复一次", prompt.last()["content"])
    }

    @Test
    fun searchFormatsAbstractAndRelatedTopics() {
        val body = """
            {"AbstractText":"A fact","AbstractURL":"https://example.test/fact",
             "RelatedTopics":[{"Text":"More","FirstURL":"https://example.test/more"}]}
        """.trimIndent()
        val formatted = formatSearch("fact", body)
        assertTrue(formatted.contains("A fact"))
        assertTrue(formatted.contains("https://example.test/more"))
    }
}
