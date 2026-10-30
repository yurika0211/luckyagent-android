package com.luckyagent.android.ui

import com.luckyagent.android.data.api.HistoryToolCall
import com.luckyagent.android.data.api.ProviderMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenUiHistoryTest {
    private val tree =
        """{"tree":{"state":"complete","tree_id":"t1","nodes":[{"id":"a","type":"Table","props":{"columns":["k"],"rows":[["v"]]}}]}}"""

    @Test
    fun storedTreeHangsUnderTheFinalAnswerAndLeavesTheFoldAlone() {
        val messages = listOf(
            ProviderMessage(role = "user", content = "对比一下"),
            ProviderMessage(
                role = "assistant",
                content = "",
                toolCalls = listOf(HistoryToolCall(id = "call-1", name = "present_ui", arguments = tree)),
            ),
            ProviderMessage(role = "tool", name = "present_ui", content = "OpenUI tree t1 accepted", toolCallId = "call-1"),
            ProviderMessage(role = "assistant", content = "| k |\n|---|\n| v |"),
        )

        val bubbles = projectHistoryBubbles(messages)

        assertEquals(listOf("user", "assistant"), bubbles.map { it.role })
        assertEquals("t1", bubbles.last().openUi?.treeId)
        assertEquals("Table", bubbles.last().openUi?.nodes?.single()?.type)
    }

    @Test
    fun halfStreamedTreeIsNotRestored() {
        val streaming =
            """{"tree":{"state":"streaming","tree_id":"t2","nodes":[{"id":"a","type":"Table","props":{}}]}}"""
        val messages = listOf(
            ProviderMessage(role = "user", content = "再来"),
            ProviderMessage(
                role = "assistant",
                content = "",
                toolCalls = listOf(HistoryToolCall(id = "call-2", name = "present_ui", arguments = streaming)),
            ),
            ProviderMessage(role = "assistant", content = "答案"),
        )

        val bubbles = projectHistoryBubbles(messages)

        val answer = bubbles.last()
        assertEquals("assistant", answer.role)
        assertNull(answer.openUi)
    }

    @Test
    fun treeDoesNotLeakIntoTheNextTurn() {
        val messages = listOf(
            ProviderMessage(role = "user", content = "第一轮"),
            ProviderMessage(
                role = "assistant",
                content = "",
                toolCalls = listOf(HistoryToolCall(id = "call-3", name = "present_ui", arguments = tree)),
            ),
            ProviderMessage(role = "assistant", content = "第一轮答案"),
            ProviderMessage(role = "user", content = "第二轮"),
            ProviderMessage(role = "assistant", content = "第二轮答案"),
        )

        val bubbles = projectHistoryBubbles(messages)

        assertTrue(bubbles.first { it.content == "第一轮答案" }.openUi != null)
        assertNull(bubbles.first { it.content == "第二轮答案" }.openUi)
    }
}