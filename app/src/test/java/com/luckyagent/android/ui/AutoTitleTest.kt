package com.luckyagent.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTitleTest {
    @Test
    fun normalizesCommitStyleOutput() {
        assertEquals(
            "fix(android): 修复冷启动连接提示",
            normalizeAutoSessionTitle("```fix(android): 修复冷启动连接提示。```"),
        )
    }

    @Test
    fun suppliesDefaultTypeAndTruncatesToOneLine() {
        val title = normalizeAutoSessionTitle("标题：" + "给第一轮对话增加一个很长的说明".repeat(8))
        requireNotNull(title)
        assertTrue(title.startsWith("feat(android): 给第一轮对话"))
        assertEquals(80, title.length)
    }

    @Test
    fun emptyModelOutputDoesNotRename() {
        assertNull(normalizeAutoSessionTitle("\n```\n"))
    }
}
