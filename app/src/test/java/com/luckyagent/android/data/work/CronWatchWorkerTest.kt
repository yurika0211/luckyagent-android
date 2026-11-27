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

    @Test
    fun ackFailureDoesNotPostTheSameExecutionAgain() = runBlocking {
        val notified = mutableListOf<String>()
        val acked = mutableListOf<String>()
        val posted = mutableSetOf<String>()

        val first = drainCronDeliveries(
            items = listOf(pending("exec", "2026-11-27T09:00:00Z")),
            alreadyPosted = posted,
            notify = { notified += it.execution.id; true },
            ack = { false },
            onPosted = { posted += it.execution.id },
            onAck = { acked += it.execution.id },
        )
        val retry = drainCronDeliveries(
            items = listOf(pending("exec", "2026-11-27T09:00:00Z")),
            alreadyPosted = posted,
            notify = { notified += it.execution.id; true },
            ack = { acked += it.execution.id; true },
            onPosted = { posted += it.execution.id },
            onAck = {},
        )

        assertEquals(CronDrainOutcome.ACK_FAILED, first)
        assertEquals(CronDrainOutcome.COMPLETE, retry)
        assertEquals(listOf("exec"), notified)
        assertEquals(listOf("exec"), acked)
    }

    @Test
    fun postedSetKeepsTheNewestIds() {
        var posted = PostedCronNotifications.decode(null)
        repeat(PostedCronNotifications.MAX + 3) { posted = posted.remember("exec-$it") }
        assertEquals(false, posted.contains("exec-0"))
        assertEquals(true, posted.contains("exec-${PostedCronNotifications.MAX + 2}"))
        assertEquals(PostedCronNotifications.MAX, PostedCronNotifications.decode(posted.encoded()).encoded().lineSequence().count())
    }

    @Test
    fun newestPendingKeepsOlderRowsUntouched() {
        val newest = pending("new", "2026-11-27T09:02:00Z")
        val chosen = newestPending(listOf(
            newest,
            pending("old", "2026-11-27T09:00:00Z"),
            pending("tie-z", "2026-11-27T09:02:00Z"),
        ))
        assertEquals("tie-z", chosen?.execution?.id)
        assertEquals(null, newestPending(emptyList()))
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
