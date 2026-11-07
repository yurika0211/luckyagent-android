package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.RuntimeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDrawerRowTest {
    @Test
    fun rowContentTruncatesBeforeCompose() {
        val session = RuntimeSession(
            id = "s1",
            title = "标题".repeat(40),
            recap = "第一行摘要" + "很长".repeat(40) + "\n第二行不进列表",
            pinned = true,
            messageCount = 3,
            updatedAt = "2026-11-07T00:00:00Z",
        )
        val row = sessionDrawerRowContent(session)
        assertEquals("s1", row.id)
        assertTrue(row.title.endsWith("…"))
        assertEquals(48, row.title.length)
        assertTrue(row.summary.endsWith("…"))
        assertTrue(!row.summary.contains("第二行"))
        assertTrue(row.meta.contains("3 条消息"))
        assertTrue(row.pinned)
    }
}
