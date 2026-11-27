package com.luckyagent.android.ui

import com.luckyagent.android.data.api.CronExecution
import com.luckyagent.android.data.api.CronExecutionsResponse
import com.luckyagent.android.data.api.CronResultTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #510: one task's execution history must be readable newest first. */
class CronHistoryTest {
    private fun execution(id: String, scheduledAt: String) = CronExecution(
        id = id,
        taskId = "task-1",
        scheduledAt = scheduledAt,
        status = "succeeded",
    )

    private fun response(vararg executions: CronExecution) = CronExecutionsResponse(
        task = CronResultTask(id = "task-1"),
        executions = executions.toList(),
    )

    @Test
    fun newestExecutionComesFirst() {
        val state = AppUiState(cronHistoryTaskId = "task-1", cronHistoryLoading = true)

        val next = applyCronHistory(
            state,
            "task-1",
            Result.success(
                response(
                    execution("old", "2026-11-27T01:00:00Z"),
                    execution("new", "2026-11-27T09:00:00Z"),
                ),
            ),
        )

        assertEquals(listOf("new", "old"), next.cronHistory.map { it.id })
        assertFalse(next.cronHistoryLoading)
        assertNull(next.cronHistoryError)
    }

    @Test
    fun emptyHistoryIsNotAnError() {
        val state = AppUiState(cronHistoryTaskId = "task-1", cronHistoryLoading = true)

        val next = applyCronHistory(state, "task-1", Result.success(response()))

        assertTrue(next.cronHistory.isEmpty())
        assertFalse(next.cronHistoryLoading)
        assertNull(next.cronHistoryError)
    }

    @Test
    fun responseForAnotherTaskIsIgnored() {
        val kept = execution("kept", "2026-11-27T01:00:00Z")
        val state = AppUiState(
            cronHistoryTaskId = "task-2",
            cronHistory = listOf(kept),
            cronHistoryLoading = true,
        )

        val next = applyCronHistory(
            state,
            "task-1",
            Result.success(response(execution("other", "2026-11-27T09:00:00Z"))),
        )

        assertEquals(listOf("kept"), next.cronHistory.map { it.id })
        assertTrue(next.cronHistoryLoading)
    }

    @Test
    fun failedRefreshKeepsTheRowsAlreadyOnScreen() {
        val kept = execution("kept", "2026-11-27T01:00:00Z")
        val state = AppUiState(
            cronHistoryTaskId = "task-1",
            cronHistory = listOf(kept),
            cronHistoryLoading = true,
        )

        val next = applyCronHistory(
            state,
            "task-1",
            Result.failure(IllegalStateException("boom")),
        )

        assertEquals(listOf("kept"), next.cronHistory.map { it.id })
        assertEquals("没能读取执行历史：boom", next.cronHistoryError)
        assertFalse(next.cronHistoryLoading)
    }
}