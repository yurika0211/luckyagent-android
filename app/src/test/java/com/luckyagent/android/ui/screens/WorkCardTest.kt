package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.TaskSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkCardTest {
    @Test fun chatCardKeepsOnlyWorkThatBelongsToTheOpenSession() {
        val mine = task("mine", parentSessionId = "chat", status = "running")
        val other = task("other", parentSessionId = "elsewhere", status = "running")
        val board = AutonomyTaskSummary(id = "board", title = "后台", parentSessionId = "chat", state = "in_progress")

        val cards = workCardsForSession(listOf(other, mine), listOf(board), "chat")

        assertEquals(listOf("mine", "board"), cards.map { it.id })
        assertTrue(workCardsForSession(listOf(mine), emptyList(), "").isEmpty())
    }

    @Test fun featuredCardPrefersTheRunningItemThenTheFinishedOne() {
        val done = task("done", status = "completed")
        val running = task("running", status = "running")

        assertEquals("running", featuredWorkCard(listOf(done.toCard(), running.toCard()))?.id)
        assertEquals("done", featuredWorkCard(listOf(done.toCard()))?.id)
        assertNull(featuredWorkCard(emptyList()))
        assertTrue(workCardCanCancel(running.toCard()))
        assertFalse(workCardCanCancel(done.toCard()))
    }

    @Test fun focusedCardStaysVisibleEvenWhenItBelongsToAnotherSession() {
        val running = task("running", parentSessionId = "chat", status = "running")
        val focused = task("elsewhere", parentSessionId = "other", status = "completed")

        val card = visibleWorkCard("elsewhere", false, listOf(running, focused), emptyList(), "chat")

        assertEquals("elsewhere", card?.id)
        assertEquals("已完成", card?.status?.let(::workCardStatusLabel))
    }

    @Test fun focusFallsBackWhenTheChosenListDoesNotContainTheTask() {
        val task = task("chat-task", status = "running")

        val card = visibleWorkCard("chat-task", true, listOf(task), emptyList(), "other")

        assertEquals("chat-task", card?.id)
        assertEquals(WorkCardKind.Task, card?.kind)
        assertFalse(card?.loading == true)
    }

    @Test fun missingFocusStillShowsAReturnCard() {
        val card = visibleWorkCard("missing", true, emptyList(), emptyList(), "chat")

        assertEquals("missing", card?.id)
        assertEquals(WorkCardKind.Background, card?.kind)
        assertTrue(card?.loading == true)
        assertFalse(workCardCanCancel(card!!))
    }

    @Test fun closingAFinishedCardKeepsTheNextRunningOne() {
        val done = task("done", status = "completed")
        val running = task("running", status = "running")
        val cards = listOf(done.toCard(), running.toCard())

        assertEquals("running", featuredWorkCard(cards, setOf("done"))?.id)
        assertNull(featuredWorkCard(cards, setOf("done", "running")))
        assertEquals(setOf("kept", "done"), dismissedWorkCardIds(setOf("kept"), "done"))
    }

    @Test fun aDismissedCardStaysHiddenUntilANotificationFocusesIt() {
        val done = task("done", status = "completed")

        assertNull(visibleWorkCard(null, false, listOf(done), emptyList(), "chat", setOf("done")))
        assertEquals(
            "done",
            visibleWorkCard("done", false, listOf(done), emptyList(), "chat", setOf("done"))?.id,
        )
    }

    @Test fun aChildTaskDoesNotReplaceTheRootCard() {
        val root = task("root", status = "running", lastActivityAt = "2026-10-23T01:00:00Z")
        val child = task("child", parentId = "root", status = "running", lastActivityAt = "2026-10-23T03:00:00Z")

        val cards = workCardsForSession(listOf(child, root), emptyList(), "chat")

        assertEquals(listOf("root"), cards.map { it.id })
    }

    @Test fun notificationFocusStaysOnlyWhenTheOpenedSessionOwnsTheCard() {
        val pinned = task("pinned", sessionId = "session-b", executionSessionId = "session-b", status = "running")
        val other = task("other", sessionId = "session-c", executionSessionId = "session-c", status = "running")

        assertTrue(keepFocusedWorkCard("pinned", false, "session-b", listOf(pinned, other), emptyList()))
        assertFalse(keepFocusedWorkCard("pinned", false, "session-c", listOf(pinned, other), emptyList()))
        assertFalse(keepFocusedWorkCard("missing", false, "session-c", listOf(other), emptyList()))
    }

    @Test fun finishedCardUsesTheLatestActivityInsteadOfTitleOrder() {
        val older = task("older", status = "completed", lastActivityAt = "2026-10-23T01:00:00Z")
        val newer = task("newer", status = "completed", lastActivityAt = "2026-10-23T02:00:00Z")

        val card = featuredWorkCard(listOf(older.toCard(), newer.toCard()))

        assertEquals("newer", card?.id)
    }

    private fun task(
        id: String,
        parentId: String? = null,
        parentSessionId: String? = "chat",
        sessionId: String? = null,
        executionSessionId: String? = null,
        status: String = "running",
        lastActivityAt: String? = null,
    ) = TaskSummary(
        id = id,
        parentId = parentId,
        parentSessionId = parentSessionId,
        sessionId = sessionId,
        executionSessionId = executionSessionId,
        source = "tool",
        mode = "single",
        status = status,
        description = id,
        lastActivityAt = lastActivityAt,
    )

    private fun TaskSummary.toCard() = workCardsForSession(listOf(this), emptyList(), parentSessionId.orEmpty()).single()
}
