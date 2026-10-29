package com.luckyagent.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the M1 task-page and M3 collaboration copy already on screen. */
class TaskAcceptanceTest {
    @Test
    fun attentionAndActiveGroupsMatchTheTaskList() {
        assertTrue("blocked".taskNeedsAttention())
        assertTrue("failed".taskNeedsAttention())
        assertTrue("timeout".taskNeedsAttention())
        assertTrue("error".taskNeedsAttention())
        assertFalse("running".taskNeedsAttention())
        assertTrue("pending".taskIsActive())
        assertTrue("queued".taskIsActive())
        assertTrue("running".taskIsActive())
        assertTrue("in_progress".taskIsActive())
        assertFalse("completed".taskIsActive())
        assertFalse("cancelled".taskIsActive())
    }

    @Test
    fun statusModeAndSourceCopyStayStable() {
        assertEquals("排队中", friendlyTaskStatus("pending"))
        assertEquals("进行中", friendlyTaskStatus("running"))
        assertEquals("需要处理", friendlyTaskStatus("blocked"))
        assertEquals("已完成", friendlyTaskStatus("completed"))
        assertEquals("失败", friendlyTaskStatus("failed"))
        assertEquals("超时", friendlyTaskStatus("timeout"))
        assertEquals("已取消", friendlyTaskStatus("cancelled"))
        assertEquals("Parallel", friendlyTaskMode("parallel"))
        assertEquals("Step by step", friendlyTaskMode("pipeline"))
        assertEquals("Review rounds", friendlyTaskMode("debate"))
        assertEquals("Assistant", friendlyTaskSource("tool"))
        assertEquals("Gateway", friendlyTaskSource("gateway"))
        assertEquals("Background", friendlyTaskSource("autonomy"))
    }

    @Test
    fun collaborationCardsUseReadableEventNames() {
        assertEquals("Progress update", friendlyAgentMessageType("task_progress"))
        assertEquals("Debate turn", friendlyAgentMessageType("debate_turn"))
        assertEquals("Task created", friendlyEventType("task.created"))
        assertEquals("Step added", friendlyEventType("task.child_created"))
        assertEquals("Task failed", friendlyEventType("task.failed"))
    }
}
