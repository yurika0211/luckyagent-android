package com.luckyagent.android.ui

import com.luckyagent.android.data.api.RuntimeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGroupsTest {
    @Test
    fun roleDecidesTheFixedCategoryNotTheProjectOrTitle() {
        assertEquals(FixedSessionRole.Delegated, fixedSessionRole("subagent"))
        assertEquals(FixedSessionRole.Delegated, fixedSessionRole(" subagent:agent-9 "))
        assertEquals(FixedSessionRole.Worker, fixedSessionRole("worker"))
        assertEquals(FixedSessionRole.Worker, fixedSessionRole("worker-task"))
        assertEquals(FixedSessionRole.Worker, fixedSessionRole("worker-long-lived"))
        assertNull(fixedSessionRole(null))
        assertNull(fixedSessionRole(" "))
        assertNull(fixedSessionRole("subagents"))
        assertNull(fixedSessionRole("workers"))
        assertNull(fixedSessionRole("delegate-task"))
        assertNull(fixedSessionRole("background-task"))
    }

    @Test
    fun pinnedOrdinarySessionsStayAheadOfFixedRolesAndProjects() {
        val pinned = session("pinned", pinned = true, project = "alpha")
        val delegated = session("delegated", agentRole = "subagent:child", pinned = true, project = "alpha", title = "delegate-task")
        val worker = session("worker", agentRole = "worker-task", project = "alpha", title = "background-task")
        val alpha = session("alpha", project = "Alpha")
        val unsorted = session("plain")

        val groups = sessionGroups(listOf(unsorted, worker, alpha, delegated, pinned))

        assertEquals(
            listOf(SESSION_GROUP_PINNED, SESSION_GROUP_DELEGATED, SESSION_GROUP_WORKER, "Alpha", SESSION_GROUP_UNSORTED),
            groups.map { it.name },
        )
        assertEquals(listOf("pinned"), groups[0].sessions.map { it.id })
        assertEquals(listOf("delegated"), groups[1].sessions.map { it.id })
        assertEquals(listOf("worker"), groups[2].sessions.map { it.id })
        assertEquals(listOf("alpha"), groups[3].sessions.map { it.id })
        assertEquals(listOf("plain"), groups[4].sessions.map { it.id })
    }

    @Test
    fun emptyFixedGroupsAreHiddenAndEachSessionAppearsOnce() {
        val groups = sessionGroups(listOf(session("only", project = "notes")))

        assertEquals(listOf("notes"), groups.map { it.name })
        assertFalse(groups.any { it.name == SESSION_GROUP_DELEGATED || it.name == SESSION_GROUP_WORKER })
        val seen = sessionGroups(
            listOf(
                session("a", agentRole = "worker", pinned = true, project = "notes"),
                session("b", project = "notes"),
            ),
        ).flatMap { it.sessions }.map { it.id }
        assertEquals(listOf("a", "b"), seen)
    }

    @Test
    fun fixedRoleSessionsCannotBeMovedByProject() {
        val delegated = session("d", agentRole = "subagent", project = "notes")
        val worker = session("w", agentRole = "worker-", project = "notes")

        assertTrue(isFixedRoleSession(delegated))
        assertTrue(isFixedRoleSession(worker))
        assertFalse(isFixedRoleSession(session("user", project = "Delegated", title = "delegate-task")))
        assertEquals(
            listOf(SESSION_GROUP_DELEGATED, SESSION_GROUP_WORKER),
            sessionGroups(listOf(worker, delegated)).map { it.name },
        )
    }

    private fun session(
        id: String,
        agentRole: String? = null,
        pinned: Boolean = false,
        project: String? = null,
        title: String? = null,
    ) = RuntimeSession(id = id, title = title, agentRole = agentRole, pinned = pinned, project = project)
}
