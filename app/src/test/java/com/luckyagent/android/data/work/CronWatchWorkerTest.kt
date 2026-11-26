package com.luckyagent.android.data.work

import com.luckyagent.android.data.api.CronDelivery
import com.luckyagent.android.data.api.CronExecution
import com.luckyagent.android.data.api.CronPendingItem
import com.luckyagent.android.data.api.CronResultTask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CronWatchWorkerTest {
    @Test
    fun drainsMultipleItemsFromOldestToNewest() = runBlocking {
        val newest = pending("new", "2026-11-27T09:02:00Z")
        val oldest = pending("old", "2026-11-27T09:00:00Z")
        val middle = pending("middle", "2026-11-27T09:01:00Z")
        val notified = mutableListOf<String>()
        val acked = mutableListOf<String>()

        val outcome = drainCronDeliveries(
            items = listOf(newest, oldest, middle),
            notify = { notified += it.execution.id; true },
            ack = { acked += it.execution.id; true },
            onAck = {},
        )

        assertEquals(CronDrainOutcome.COMPLETE, outcome)
        assertEquals(listOf("old", "middle", "new"), notified)
        assertEquals(notified, acked)
    }

    @Test
    fun sameScheduledAtUsesExecutionIdAsTieBreaker() = runBlocking {
        val outcomeIds = mutableListOf<String>()

        val outcome = drainCronDeliveries(
            items = listOf(
                pending("z", "2026-11-27T09:00:00Z"),
                pending("a", "2026-11-27T09:00:00Z"),
            ),
            notify = { outcomeIds += it.execution.id; true },
            ack = { true },
            onAck = {},
        )

        assertEquals(CronDrainOutcome.COMPLETE, outcome)
        assertEquals(listOf("a", "z"), outcomeIds)
    }

    @Test
    fun notificationFailureStopsBeforeAdvancingPastFailedItem() = runBlocking {
        val notified = mutableListOf<String>()
        val acked = mutableListOf<String>()

        val outcome = drainCronDeliveries(
            items = listOf(
                pending("new", "2026-11-27T09:02:00Z"),
                pending("old", "2026-11-27T09:00:00Z"),
                pending("middle", "2026-11-27T09:01:00Z"),
            ),
            notify = {
                notified += it.execution.id
                it.execution.id != "middle"
            },
            ack = { acked += it.execution.id; true },
            onAck = {},
        )

        assertEquals(CronDrainOutcome.NOTIFICATION_BLOCKED, outcome)
        assertEquals(listOf("old", "middle"), notified)
        assertEquals(listOf("old"), acked)
    }

    private fun pending(id: String, scheduledAt: String): CronPendingItem = CronPendingItem(
        execution = CronExecution(
            id = id,
            taskId = "task",
            scheduledAt = scheduledAt,
            status = "succeeded",
        ),
        task = CronResultTask(id = "task", name = "日报"),
        delivery = CronDelivery(id = "delivery-$id", executionId = id),
    )
}
