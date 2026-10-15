package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.TaskSummary
import com.luckyagent.android.ui.ChatBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineTest {
    @Test
    fun duplicateAndBlankIdsGetUniqueKeysWithoutDroppingMessages() {
        val bubbles = listOf("same", "same", "same-1", "", "", "timeline", "chat-tail", "pending-approvals")
            .map { ChatBubble(id = it, role = "assistant", content = it) }

        val timeline = buildTimeline(bubbles)

        assertEquals(bubbles, timeline.map { (it as ChatTimelineItem.Message).bubble })
        assertEquals(timeline.size, timeline.map { it.key }.toSet().size)
        assertTrue(timeline.all { it.key.isNotBlank() })
        assertTrue(timeline.none { it.key == "chat-tail" || it.key == "pending-approvals" })
        assertEquals(timeline.map { it.key }, buildTimeline(bubbles).map { it.key })
    }

    @Test
    fun processAndMessageIdsCannotCollide() {
        val steps = listOf(
            ChatBubble(id = "replayed", role = "reasoning", content = "reason"),
            ChatBubble(id = "replayed", role = "tool", content = "result"),
        )
        val answer = ChatBubble(id = "replayed", role = "assistant", content = "answer")
        val timeline = buildTimeline(steps + answer + steps)

        assertEquals(3, timeline.size)
        assertEquals(steps, (timeline[0] as ChatTimelineItem.Process).steps)
        assertEquals(answer, (timeline[1] as ChatTimelineItem.Message).bubble)
        assertEquals(steps, (timeline[2] as ChatTimelineItem.Process).steps)
        assertEquals(3, timeline.map { it.key }.toSet().size)
    }
}

    @Test
    fun collabFoldLabelUsesModeAndFinishedState() {
        val running = TaskSummary(id = "p", source = "tool", mode = "parallel", status = "running", description = "split", childCount = 3, completedChildren = 2)
        assertEquals("并行 · 2/3", collabFoldLabel(listOf(running), responding = true))
        val pipeline = TaskSummary(id = "s", source = "tool", mode = "pipeline", status = "running", description = "chain", childCount = 4, completedChildren = 1)
        assertEquals("流水线 · 第 1 步", collabFoldLabel(listOf(pipeline), responding = true))
        val debate = TaskSummary(id = "d", source = "tool", mode = "debate", status = "running", description = "argue", childCount = 3, completedChildren = 1, progress = 0.66)
        assertEquals("辩论 · 第 2 轮", collabFoldLabel(listOf(debate), responding = true))
        val board = TaskSummary(id = "b", source = "autonomy", mode = "autonomy_queue", status = "running", description = "board", childCount = 3)
        assertEquals("黑板 · 3 人", collabFoldLabel(listOf(board), responding = true))
        val done = running.copy(status = "completed", completedChildren = 3)
        assertEquals("并行 · 3/3 · 完成", collabFoldLabel(listOf(done), responding = false))
        assertEquals(null, collabFoldLabel(emptyList(), responding = true))
    }
