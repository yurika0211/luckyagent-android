package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.AutonomyTaskSummary
import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.data.api.MemoryEntry
import com.luckyagent.android.data.api.ToolTraceRecord
import com.luckyagent.android.ui.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNavigationTest {
    @Test fun sessionCardKeepsOnlyTasksThatBelongToTheOpenChat() {
        val owner = task("owner", parentSessionId = "chat")
        val child = task("child", sessionId = "chat")
        val other = task("other", parentSessionId = "elsewhere")

        val visible = tasksForSession(listOf(other, owner, child), "chat")

        assertEquals(listOf("owner", "child"), visible.map { it.id })
    }

    @Test fun sessionCardPrefersAttentionThenTheRunningTask() {
        val done = task("done", status = "completed", lastActivityAt = "9")
        val running = task("running", status = "running", lastActivityAt = "2")
        val blocked = task("blocked", status = "blocked", lastActivityAt = "1")

        assertEquals("blocked", sessionNavigationTask(listOf(done, running, blocked))?.id)
        assertEquals("running", sessionNavigationTask(listOf(done, running))?.id)
        assertEquals("done", sessionNavigationTask(listOf(done))?.id)
        assertNull(sessionNavigationTask(emptyList()))
    }

    @Test fun sessionCardKeepsOnlyBackgroundItemsThatBelongToTheOpenChat() {
        val owner = AutonomyTaskSummary(id = "board", parentSessionId = "chat")
        val child = AutonomyTaskSummary(id = "worker", sessionId = "chat")
        val other = AutonomyTaskSummary(id = "elsewhere", parentSessionId = "other")

        val visible = backgroundForSession(listOf(other, owner, child), "chat")

        assertEquals(listOf("board", "worker"), visible.map { it.id })
        assertTrue(backgroundForSession(listOf(other), "").isEmpty())
    }

    @Test fun sessionCardPrefersBlockedBackgroundWork() {
        val done = AutonomyTaskSummary(id = "done", state = "done", lastActivityAt = "9")
        val running = AutonomyTaskSummary(id = "running", state = "in_progress", lastActivityAt = "2")
        val blocked = AutonomyTaskSummary(id = "blocked", state = "blocked", lastActivityAt = "1")

        assertEquals("blocked", sessionNavigationBackground(listOf(done, running, blocked))?.id)
        assertEquals("running", sessionNavigationBackground(listOf(done, running))?.id)
        assertNull(sessionNavigationBackground(emptyList()))
    }

    @Test fun hidingACardStaysInsideTheOpenSession() {
        val task = sessionCardKey("chat", SessionCardKind.Task, "task-1")
        val trace = sessionCardKey("chat", SessionCardKind.Trace, "call-1")
        val hidden = setOf(task, trace)

        assertTrue(hiddenSessionCard(hidden, "chat", SessionCardKind.Task, "task-1"))
        assertTrue(hiddenSessionCard(hidden, "chat", SessionCardKind.Trace, "call-1"))
        assertFalse(hiddenSessionCard(hidden, "other", SessionCardKind.Task, "task-1"))
        assertFalse(hiddenSessionCard(hidden, "chat", SessionCardKind.Memory, "call-1"))
        assertEquals("", sessionCardKey(" ", SessionCardKind.Task, "task-1"))
        assertFalse(hiddenSessionCard(hidden, "chat", SessionCardKind.Task, " "))
    }

    @Test fun hidingTheSessionPanelDoesNotHideAnotherChat() {
        val panel = sessionCardKey("chat", SessionCardKind.Panel, SESSION_PANEL_CARD_ID)
        val hidden = setOf(panel)

        assertTrue(hiddenSessionCard(hidden, "chat", SessionCardKind.Panel, SESSION_PANEL_CARD_ID))
        assertFalse(hiddenSessionCard(hidden, "other", SessionCardKind.Panel, SESSION_PANEL_CARD_ID))
        assertFalse(hiddenSessionCard(hidden, "chat", SessionCardKind.Task, SESSION_PANEL_CARD_ID))
    }

    @Test fun navigationKeepsSessionProgressOffTheModuleList() {
        assertEquals(
            listOf(
                AppDestination.Cron,
                AppDestination.Gateways,
                AppDestination.Plugins,
                AppDestination.Skills,
                AppDestination.RuntimeConfig,
            ),
            computerNavDestinations,
        )
        assertFalse(computerNavDestinations.contains(AppDestination.Tasks))
        assertFalse(computerNavDestinations.contains(AppDestination.Trajectory))
        assertFalse(computerNavDestinations.contains(AppDestination.Memory))
        assertFalse(computerNavDestinations.contains(AppDestination.Background))
        assertFalse(computerNavDestinations.contains(AppDestination.Chat))
    }

    @Test fun traceAndMemoryCardsPreferStableIds() {
        val traced = ToolTraceRecord(id = "call-9", name = "file_read")
        val nameless = ToolTraceRecord(name = "terminal", arguments = "ls")
        assertEquals("call-9", traceRecordKey(traced, 3, 20))
        assertEquals("page-23-terminal-${"ls".hashCode()}", traceRecordKey(nameless, 3, 20))

        val remembered = MemoryEntry(id = "note-1", content = "keep")
        val blank = MemoryEntry(content = "keep")
        assertEquals("note-1", memoryCardKey(remembered, 4))
        assertEquals("index-4-${"keep".hashCode()}", memoryCardKey(blank, 4))
    }

    private fun task(
        id: String,
        status: String = "running",
        parentSessionId: String? = null,
        sessionId: String? = null,
        lastActivityAt: String? = null,
    ) = TaskSummary(
        id = id,
        parentSessionId = parentSessionId,
        sessionId = sessionId,
        source = "tool",
        mode = "single",
        status = status,
        description = id,
        lastActivityAt = lastActivityAt,
    )
}
