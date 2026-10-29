package com.luckyagent.android.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeNoticePolicyTest {
    @Test
    fun progressStaysQuietUntilTheUserOptsIn() {
        assertFalse(RuntimeNoticePolicy.taskWorthy("running"))
        assertFalse(RuntimeNoticePolicy.taskWorthy("in_progress", includeProgress = false))
        assertTrue(RuntimeNoticePolicy.taskWorthy("running", includeProgress = true))
    }

    @Test
    fun finishedFailedAndBlockedTasksAreWorthAnAlert() {
        assertTrue(RuntimeNoticePolicy.taskWorthy("completed"))
        assertTrue(RuntimeNoticePolicy.taskWorthy("failed"))
        assertTrue(RuntimeNoticePolicy.taskWorthy("timeout"))
        assertTrue(RuntimeNoticePolicy.taskWorthy("cancelled"))
        assertTrue(RuntimeNoticePolicy.taskWorthy("running", blocked = true))
        assertTrue(RuntimeNoticePolicy.backgroundWorthy("done"))
        assertTrue(RuntimeNoticePolicy.backgroundWorthy("succeeded"))
        assertFalse(RuntimeNoticePolicy.backgroundWorthy("running"))
    }

    @Test
    fun liveEventsOnlyAlertOnAResultUnlessProgressIsEnabled() {
        assertFalse(RuntimeNoticePolicy.eventWorthy("running", "task.progress", includeProgress = false))
        assertTrue(RuntimeNoticePolicy.eventWorthy("running", "task.progress", includeProgress = true))
        assertTrue(RuntimeNoticePolicy.eventWorthy("failed", "task.failed", includeProgress = false))
        assertTrue(RuntimeNoticePolicy.eventWorthy("", "task.child.completed", includeProgress = false))
        assertTrue(RuntimeNoticePolicy.eventWorthy("blocked", "task.updated", includeProgress = false))
    }

    @Test
    fun theSameCronRunDoesNotAlertAgain() {
        assertFalse(RuntimeNoticePolicy.cronRunWorthy(null, "job:1"))
        assertTrue(RuntimeNoticePolicy.cronRunWorthy("", "job:1"))
        assertFalse(RuntimeNoticePolicy.cronRunWorthy("job:1", "job:1"))
        assertTrue(RuntimeNoticePolicy.cronRunWorthy("job:1", "job:2"))
    }

    @Test
    fun copyNamesTheOutcomeInsteadOfARawStatus() {
        assertEquals(
            "LuckyAgent · 完成了 · 整理笔记",
            RuntimeNoticePolicy.taskHeadline("后台任务", "整理笔记", "completed"),
        )
        assertEquals(
            "卡住了 · 还差一步",
            RuntimeNoticePolicy.taskBody("blocked", "还差一步"),
        )
        assertEquals(
            "LuckyAgent · 定时失败 · 早报",
            RuntimeNoticePolicy.cronHeadline("早报", failed = true),
        )
        assertEquals(
            "早报 这次没跑成：电脑不在线",
            RuntimeNoticePolicy.cronBody("早报", failed = true, detail = "电脑不在线"),
        )
    }
}
