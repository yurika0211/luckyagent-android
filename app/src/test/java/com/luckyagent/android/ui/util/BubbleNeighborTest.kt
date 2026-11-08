package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleNeighborTest {
    @Test
    fun consecutiveSameRoleSharesEdges() {
        val rows = listOf(MessageRow("a"), MessageRow("b"), MessageRow("c"))
        val roles = mapOf("a" to "assistant", "b" to "assistant", "c" to "assistant")
        val edges = bubbleNeighborEdges(rows, roles)
        assertEquals(scanEdges(rows, roles), edges)
        assertFalse(edges.getValue("a").flatTop)
        assertTrue(edges.getValue("a").showAvatar)
        assertTrue(edges.getValue("a").flatBottom)
        assertTrue(edges.getValue("b").flatTop)
        assertFalse(edges.getValue("b").showAvatar)
        assertTrue(edges.getValue("b").flatBottom)
        assertTrue(edges.getValue("c").flatTop)
        assertFalse(edges.getValue("c").flatBottom)
        assertFalse(edges.getValue("c").showAvatar)
    }

    @Test
    fun dateSeparatorBreaksTheEdge() {
        val rows = listOf(
            MessageRow("a"),
            DateSeparatorRow("day", "今天"),
            MessageRow("b"),
        )
        val roles = mapOf("a" to "assistant", "b" to "assistant")
        val edges = bubbleNeighborEdges(rows, roles)
        assertEquals(scanEdges(rows, roles), edges)
        assertFalse(edges.getValue("a").flatBottom)
        assertFalse(edges.getValue("b").flatTop)
        assertTrue(edges.getValue("b").showAvatar)
        assertEquals("assistant", edges.getValue("b").previousRole)
        assertEquals("assistant", edges.getValue("a").nextRole)
    }

    @Test
    fun unreadSeparatorBreaksTheEdge() {
        val rows = listOf(
            MessageRow("a"),
            UnreadSeparatorRow("unread-b", 1, "b"),
            MessageRow("b"),
        )
        val roles = mapOf("a" to "user", "b" to "user")
        val edges = bubbleNeighborEdges(rows, roles)
        assertEquals(scanEdges(rows, roles), edges)
        assertFalse(edges.getValue("a").flatBottom)
        assertFalse(edges.getValue("b").flatTop)
        assertTrue(edges.getValue("b").showAvatar)
    }

    @Test
    fun roleSwitchDoesNotShareAnEdge() {
        val rows = listOf(MessageRow("a"), MessageRow("b"))
        val roles = mapOf("a" to "user", "b" to "assistant")
        val edges = bubbleNeighborEdges(rows, roles)
        assertEquals(scanEdges(rows, roles), edges)
        assertFalse(edges.getValue("a").flatBottom)
        assertFalse(edges.getValue("b").flatTop)
        assertTrue(edges.getValue("b").showAvatar)
        assertEquals("user", edges.getValue("b").previousRole)
        assertEquals("assistant", edges.getValue("a").nextRole)
    }

    @Test
    fun singleRowShowsAvatarAndNoSharedEdge() {
        val rows = listOf(MessageRow("a"))
        val roles = mapOf("a" to "assistant")
        val edges = bubbleNeighborEdges(rows, roles)
        assertEquals(scanEdges(rows, roles), edges)
        assertEquals(1, edges.size)
        assertFalse(edges.getValue("a").flatTop)
        assertFalse(edges.getValue("a").flatBottom)
        assertTrue(edges.getValue("a").showAvatar)
        assertNull(edges.getValue("a").previousRole)
        assertNull(edges.getValue("a").nextRole)
    }

    @Test
    fun processNeighborHasNoRoleSoTheNextBubbleDoesNotShare() {
        val rows = listOf(MessageRow("a"), MessageRow("proc"), MessageRow("b"))
        val roles = mapOf("a" to "assistant", "b" to "assistant")
        val edges = bubbleNeighborEdges(rows, roles)
        assertEquals(scanEdges(rows, roles), edges)
        assertFalse(edges.containsKey("proc"))
        assertNull(edges.getValue("a").nextRole)
        assertTrue(edges.getValue("a").flatBottom)
        assertNull(edges.getValue("b").previousRole)
        assertFalse(edges.getValue("b").flatTop)
        assertTrue(edges.getValue("b").showAvatar)
    }

    /** The old per-row scan. Neighbor edges must match this exactly. */
    private fun scanEdges(
        rows: List<TimelineRow>,
        roleByKey: Map<String, String>,
    ): Map<String, BubbleNeighborEdges> {
        return buildMap {
            for (row in rows) {
                val role = roleByKey[row.key] ?: continue
                val rowIndex = rows.indexOfFirst { it.key == row.key }
                val previousMessage = rows.take(rowIndex).lastOrNull { it is MessageRow }
                val nextMessage = rows.drop(rowIndex + 1).firstOrNull { it is MessageRow }
                val previousRole = previousMessage?.key?.let { roleByKey[it] }
                val nextRole = nextMessage?.key?.let { roleByKey[it] }
                val separatedAbove = separatorBetween(rows, row.key, previousMessage?.key)
                val separatedBelow = separatorBetween(rows, row.key, nextMessage?.key)
                val flatTop = bubbleSharesEdge(previousRole, role, separatedAbove)
                put(
                    row.key,
                    BubbleNeighborEdges(
                        previousRole = previousRole,
                        nextRole = nextRole,
                        flatTop = flatTop,
                        flatBottom = bubbleSharesEdge(role, nextRole.orEmpty(), separatedBelow),
                        showAvatar = !flatTop,
                    ),
                )
            }
        }
    }
}
