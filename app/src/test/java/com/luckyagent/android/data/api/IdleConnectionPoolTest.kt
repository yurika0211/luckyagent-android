package com.luckyagent.android.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleConnectionPoolTest {
    @Test
    fun keepsMostRecentlyUsedIdleConnections() {
        val idle = listOf("a" to 10L, "b" to 40L, "c" to 20L, "d" to 30L, "e" to 50L, "f" to 5L)
        val evict = LuckyAgentWsClient.idleConnectionsToEvict(idle, keep = 4)
        assertEquals(setOf("a", "f"), evict.toSet())
    }

    @Test
    fun evictsNothingUnderLimit() {
        val idle = listOf("a" to 1L, "b" to 2L)
        assertTrue(LuckyAgentWsClient.idleConnectionsToEvict(idle, LuckyAgentWsClient.MAX_IDLE_CONNECTIONS).isEmpty())
    }

    @Test
    fun zeroKeepEvictsAll() {
        val idle = listOf("a" to 1L, "b" to 2L)
        assertEquals(setOf("a", "b"), LuckyAgentWsClient.idleConnectionsToEvict(idle, keep = 0).toSet())
    }
}
