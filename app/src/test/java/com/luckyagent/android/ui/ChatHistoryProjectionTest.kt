package com.luckyagent.android.ui

import com.luckyagent.android.data.api.HistoryToolCall
import com.luckyagent.android.data.api.MediaAttachment
import com.luckyagent.android.data.api.ProviderMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryProjectionTest {
    @Test
    fun resultPreservesCallIdentityArgumentsAndAttachments() {
        val attachment = MediaAttachment(type = "file", fileName = "input.txt")
        val messages = listOf(
            call("one").copy(attachments = listOf(attachment)),
            result("one", "preview").copy(contentTruncated = true),
        )

        val bubbles = projectHistoryBubbles(messages, startIndex = 40) { message, index ->
            message.toHistoryBubbles(index, message.attachments.map(::ChatMedia))
        }

        val tool = bubbles.single()
        assertEquals("h-40-tool-0", tool.id)
        assertEquals("file_read", tool.toolName)
        assertEquals("{}", tool.toolArgs)
        assertEquals("preview", tool.toolOutput)
        assertEquals(listOf(ChatMedia(attachment)), tool.attachments)
        assertTrue(tool.contentTruncated)
        assertEquals(41, tool.historyIndex)
        assertEquals(40, tool.serverOrdinal)
    }

    @Test
    fun resultAttachmentsReplaceCallAttachments() {
        val old = MediaAttachment(type = "file", fileName = "input.txt")
        val next = MediaAttachment(type = "file", fileName = "output.txt")
        val messages = listOf(call("one").copy(attachments = listOf(old)), result("one", "done").copy(attachments = listOf(next)))

        val tool = projectHistoryBubbles(messages) { message, index ->
            message.toHistoryBubbles(index, message.attachments.map(::ChatMedia))
        }.single()

        assertEquals(listOf(ChatMedia(next)), tool.attachments)
    }

    @Test
    fun reusedToolIdUpdatesOnlyTheMostRecentCall() {
        val bubbles = projectHistoryBubbles(listOf(
            call("same"),
            result("same", "first"),
            ProviderMessage(role = "user", content = "next turn"),
            call("same"),
            result("same", "second"),
        ))

        assertEquals(listOf("first", "second"), bubbles.filter { it.role == "tool" }.map { it.toolOutput })
        assertEquals("next turn", bubbles[1].content)
    }

    @Test
    fun resultWhoseCallIsOutsideThePageIsKeptAndCanBeUpdated() {
        val bubbles = projectHistoryBubbles(listOf(
            result("missing", "partial").copy(contentTruncated = true),
            ProviderMessage(role = "assistant", content = "explanation"),
            result("missing", "complete"),
        ))

        assertEquals(2, bubbles.size)
        assertEquals("complete", bubbles.first().toolOutput)
        assertFalse(bubbles.first().contentTruncated)
        assertEquals(2, bubbles.first().historyIndex)
        assertEquals("explanation", bubbles.last().content)
    }

    @Test
    fun mixedHistoryMatchesThePreviousProjectionExactly() {
        val messages = buildList {
            repeat(200) { index ->
                add(ProviderMessage(role = "user", content = "question $index", clientMessageId = "request-$index"))
                add(call("call-$index").copy(content = "commentary", reasoningContent = "thinking"))
                if (index % 3 == 0) add(call("call-$index"))
                add(result("call-$index", "result $index"))
                add(result("orphan-$index", "orphan"))
                add(ProviderMessage(role = "assistant", content = "answer $index"))
            }
        }

        assertEquals(previousProjection(messages, 27), projectHistoryBubbles(messages, 27))
    }

    @Test
    fun longHistoryProjectsEachMessageOnceAndKeepsDistantToolResults() {
        val count = 10_000
        val messages = (0 until count).map { call("call-$it") } +
            (0 until count).map { result("call-$it", "result $it") } +
            (0 until count).map { result("orphan-$it", "orphan $it") }
        var projected = 0

        val bubbles = projectHistoryBubbles(messages, 100) { message, index ->
            projected++
            message.toHistoryBubbles(index)
        }

        assertEquals(messages.size, projected)
        assertEquals(count * 2, bubbles.size)
        assertEquals((0 until count).map { "result $it" }, bubbles.take(count).map { it.toolOutput })
        assertEquals((0 until count).map { "orphan $it" }, bubbles.drop(count).map { it.toolOutput })
        assertEquals(count + 100, bubbles.first().historyIndex)
    }

    @Test
    fun retryOnEarlierAssistantSendsThatUserTurn() {
        val bubbles = listOf(
            ChatBubble(id = "user-a", role = "user", content = "sentence A"),
            ChatBubble(id = "assistant-a", role = "assistant", content = "answer A"),
            ChatBubble(id = "user-b", role = "user", content = "sentence B"),
            ChatBubble(id = "assistant-b", role = "assistant", content = "answer B"),
        )

        assertEquals("sentence A", retrySourceForAssistant(bubbles, "assistant-a")?.content)
        assertEquals("sentence B", retrySourceForAssistant(bubbles, "assistant-b")?.content)
        assertNull(retrySourceForAssistant(bubbles, "missing"))
        assertNull(retrySourceForAssistant(bubbles, "user-a"))
    }

    /** Reference implementation before the indexed join; used only on small fixtures. */
    private fun previousProjection(messages: List<ProviderMessage>, startIndex: Int): List<ChatBubble> {
        val bubbles = mutableListOf<ChatBubble>()
        messages.forEachIndexed { index, message ->
            message.toHistoryBubbles(startIndex + index).forEach { bubble ->
                val callIndex = if (message.role == "tool" && bubble.role == "tool" && !message.toolCallId.isNullOrBlank()) {
                    bubbles.indexOfLast { it.role == "tool" && it.stepId == message.toolCallId }
                } else -1
                if (callIndex >= 0) {
                    val call = bubbles[callIndex]
                    bubbles[callIndex] = call.copy(
                        content = bubble.content,
                        toolOutput = bubble.toolOutput,
                        attachments = if (bubble.attachments.isNotEmpty()) bubble.attachments else call.attachments,
                        contentTruncated = bubble.contentTruncated,
                        historyIndex = bubble.historyIndex,
                    )
                } else {
                    bubbles += bubble
                }
            }
        }
        return bubbles
    }

    @Test
    fun longReasoningAndToolOutputStayComplete() {
        val reasoning = "r".repeat(16_001) + "TAIL"
        val args = "a".repeat(8_001) + "ARGS"
        val output = (1..13).joinToString("\n") { "line-$it" } + "\n" + "o".repeat(16_001) + "OUT"
        val message = ProviderMessage(
            role = "assistant",
            reasoningContent = reasoning,
            toolCalls = listOf(HistoryToolCall(id = "call-1", name = "file_read", arguments = args)),
        )
        val tool = ProviderMessage(role = "tool", toolCallId = "call-1", name = "file_read", content = output)

        val bubbles = projectHistoryBubbles(listOf(message, tool))
        val thought = bubbles.single { it.role == "reasoning" }
        val step = bubbles.single { it.role == "tool" }

        assertEquals(reasoning, thought.content)
        assertFalse(thought.content.contains("内容已截断"))
        assertTrue(thought.content.endsWith("TAIL"))
        assertEquals(args, step.toolArgs)
        assertEquals(output, step.toolOutput)
        assertEquals(output, step.content)
        assertFalse(step.toolOutput.orEmpty().contains("内容已截断"))
        assertTrue(step.toolOutput.orEmpty().endsWith("OUT"))
        assertTrue(step.content.lines().size > 12)
        val summary = buildToolContent("file_read", args, output, done = true, success = true)
        assertTrue(summary.length < args.length)
        assertTrue(summary.startsWith("file_read · done"))
    }

    private fun call(id: String) = ProviderMessage(
        role = "assistant",
        toolCalls = listOf(HistoryToolCall(id = id, name = "file_read", arguments = "{}")),
    )

    private fun result(id: String, content: String) = ProviderMessage(role = "tool", toolCallId = id, content = content)
}
