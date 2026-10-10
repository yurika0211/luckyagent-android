package com.luckyagent.android.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundFrameTest {
    @Test
    fun cursorPrefersEventIdOverId() {
        val frame = """{"id":"msg-1","event_id":"evt-9","type":"stream_chunk"}"""
        assertEquals("evt-9", LuckyAgentWsClient.inboundEventCursor(frame))
    }

    @Test
    fun cursorFallsBackToIdAndIgnoresEscapedValues() {
        val fallback = """{"type":"final","id":"msg-2"}"""
        assertEquals("msg-2", LuckyAgentWsClient.inboundEventCursor(fallback))

        val escaped = """{"event_id":"bad\"tail","id":"msg-3"}"""
        assertEquals("msg-3", LuckyAgentWsClient.inboundEventCursor(escaped))
    }

    @Test
    fun cursorSurvivesAHugePayloadBetweenTheKeys() {
        val filler = "x".repeat(20_000)
        val frame = """{"type":"stream_end","data":{"text":"$filler"},"event_id":"evt-tail"}"""
        assertEquals("evt-tail", LuckyAgentWsClient.inboundEventCursor(frame))
    }

    @Test
    fun oversizedNoticeIsMarkedAndDoesNotSliceSurrogates() {
        val notice = LuckyAgentWsClient.oversizedFrameNotice(8_000_001)
        assertTrue(notice.startsWith("{"))
        assertTrue(notice.contains("frame truncated for display"))
        assertTrue(notice.contains("8000001"))
        assertFalse("notice must not embed a sliced payload", notice.contains("\\u"))
        assertTrue(notice.endsWith("}"))
    }

    @Test
    fun limitsStayDistinctFromUiDisplayCaps() {
        assertEquals(8_000_000, LuckyAgentWsClient.MAX_INBOUND_MESSAGE_CHARS)
        assertEquals(64_000, LuckyAgentWsClient.MAX_RAW_MESSAGE_CHARS)
        assertTrue(LuckyAgentWsClient.MAX_RAW_MESSAGE_CHARS < LuckyAgentWsClient.MAX_INBOUND_MESSAGE_CHARS)
    }
}
