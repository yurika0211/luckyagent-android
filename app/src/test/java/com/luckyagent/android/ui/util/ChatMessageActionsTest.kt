package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageActionsTest {
    @Test
    fun bubbleCopyText_prefersToolSummary() {
        val text = bubbleCopyText(
            role = "tool",
            content = "",
            toolName = "web_search",
            toolArgs = "{\"q\":\"wsl\"}",
            toolOutput = "ok",
        )
        assertEquals("tool: web_search\nargs: {\"q\":\"wsl\"}\noutput: ok", text)
    }

    @Test
    fun bubbleCopyText_usesContentForNormalMessage() {
        assertEquals("hello", bubbleCopyText(role = "assistant", content = "hello"))
    }

    @Test
    fun formatQuoteBlock_and_outbound() {
        val quote = MessageQuote(messageId = "m1", role = "assistant", content = "第一行\n第二行")
        val block = formatQuoteBlock(quote.role, quote.content)
        assertTrue(block.startsWith("> [引用-assistant]"))
        assertTrue(block.contains("> 第一行"))
        assertTrue(block.contains("> 第二行"))
        val outbound = buildOutboundMessage("继续", quote)
        assertTrue(outbound.contains("> ---"))
        assertTrue(outbound.endsWith("继续"))
    }

    @Test
    fun quotePreview_truncates() {
        val preview = quotePreview("a".repeat(100), max = 10)
        assertEquals(11, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun quoteFromBubble_buildsQuote() {
        val quote = quoteFromBubble(
            id = "a1",
            role = "assistant",
            content = "  hello world  ",
        )
        assertNotNull(quote)
        assertEquals("a1", quote!!.messageId)
        assertEquals("assistant", quote.role)
        assertEquals("hello world", quote.content)
    }

    @Test
    fun quoteFromBubble_emptyReturnsNull() {
        assertNull(quoteFromBubble(id = "x", role = "assistant", content = "   "))
    }

    @Test
    fun quoteFromBubble_toolUsesSummary() {
        val quote = quoteFromBubble(
            id = "t1",
            role = "tool",
            content = "",
            toolName = "web_search",
            toolArgs = "{}",
            toolOutput = "done",
        )
        assertNotNull(quote)
        assertEquals("tool", quote!!.role)
        assertTrue(quote.content.contains("web_search"))
    }

    @Test
    fun quoteRoleLabel_mapsKnownRoles() {
        assertEquals("助手", quoteRoleLabel("assistant"))
        assertEquals("用户", quoteRoleLabel("user"))
        assertEquals("工具", quoteRoleLabel("tool"))
    }
}
