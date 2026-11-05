package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.samePolledList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTopBarLeadTest {
    @Test
    fun sameTaskListSkipsTheNextUiWrite() {
        val tasks = listOf("task-a", "task-b")
        assertTrue(samePolledList(tasks, listOf("task-a", "task-b"), previousLoading = false, nextLoading = false, previousError = null, nextError = null))
        assertTrue(samePolledList(tasks, tasks, previousLoading = false, nextLoading = false, previousError = null, nextError = null))
        assertFalse(samePolledList(tasks, listOf("task-a"), previousLoading = false, nextLoading = false, previousError = null, nextError = null))
        assertFalse(samePolledList(listOf("task-a", "task-b"), listOf("task-a", "task-b"), previousLoading = true, nextLoading = false, previousError = null, nextError = null))
        assertFalse(samePolledList(listOf("task-a", "task-b"), listOf("task-a", "task-b"), previousLoading = false, nextLoading = false, previousError = null, nextError = "tasks: down"))
    }

    @Test
    fun runtimeLeadsWithConnectingUntilTheSocketIsLive() {
        assertEquals(ChatTopBarLead.Connect, chatTopBarLead("runtime", SocketState.Idle))
        assertEquals(ChatTopBarLead.Connect, chatTopBarLead("runtime", SocketState.Connecting))
        assertEquals(ChatTopBarLead.Connect, chatTopBarLead("runtime", SocketState.Reconnecting))
        assertEquals(ChatTopBarLead.Connect, chatTopBarLead("runtime", SocketState.Error))
        assertEquals(ChatTopBarLead.Connect, chatTopBarLead("runtime", SocketState.Closed))
    }

    @Test
    fun liveSocketKeepsTheModelSwitch() {
        assertEquals(ChatTopBarLead.Model, chatTopBarLead("runtime", SocketState.Connected))
        assertEquals(ChatTopBarLead.Model, chatTopBarLead("runtime", SocketState.Running))
    }

    @Test
    fun standaloneHasNoRuntimeSocketToConnect() {
        // Standalone chat talks to a model URL directly; its model switch must stay.
        assertEquals(ChatTopBarLead.Model, chatTopBarLead("standalone", SocketState.Idle))
        assertEquals(ChatTopBarLead.Model, chatTopBarLead("standalone", SocketState.Connected))
    }

    @Test
    fun contextRingNeedsALiveRuntimeSocket() {
        assertEquals(false, showsContextRing("standalone", SocketState.Connected))
        assertEquals(false, showsContextRing("standalone", SocketState.Idle))
        assertEquals(false, showsContextRing("runtime", SocketState.Idle))
        assertEquals(false, showsContextRing("runtime", SocketState.Connecting))
        assertEquals(false, showsContextRing("runtime", SocketState.Error))
        assertEquals(true, showsContextRing("runtime", SocketState.Connected))
        assertEquals(true, showsContextRing("runtime", SocketState.Running))
    }

    @Test
    fun pinnedApprovalsNameTheOnesBelowTheFold() {
        assertEquals(0, approvalsStillHidden(0, 2))
        assertEquals(0, approvalsStillHidden(2, 2))
        assertEquals(2, approvalsStillHidden(4, 2))
    }

    @Test
    fun standaloneDoesNotOfferLuckyCommands() {
        assertEquals(false, showsLuckyComposer("standalone", SocketState.Connected))
        assertEquals(false, showsLuckyComposer("runtime", SocketState.Idle))
        assertEquals(false, showsLuckyComposer("runtime", SocketState.Error))
        assertEquals(true, showsLuckyComposer("runtime", SocketState.Connected))
        assertEquals(true, showsLuckyComposer("runtime", SocketState.Running))
    }

    @Test
    fun luckyCommandLeavesTheUnsentDraft() {
        assertEquals("还没发的草稿", composerAfterLuckyCommand("还没发的草稿"))
        assertEquals("", composerAfterLuckyCommand(""))
    }

    @Test
    fun collectingLabelNamesTheCount() {
        assertEquals("正在收集 · 已 3 条", luckyCollectingLabel(3))
        assertEquals("正在收集 · 已 0 条", luckyCollectingLabel(0))
        assertEquals("正在收集 · 已 0 条", luckyCollectingLabel(-1))
    }

    @Test
    fun idleLuckyRowStaysOffTheComposer() {
        assertEquals(false, showsLuckyComposerRow(luckyActive = false, pendingCount = 0))
        assertEquals(true, showsLuckyComposerRow(luckyActive = true, pendingCount = 0))
        assertEquals(true, showsLuckyComposerRow(luckyActive = false, pendingCount = 1))
    }

    @Test
    fun idleLuckyOffersOnlyStart() {
        assertEquals(listOf("/lucky on" to "开始收集"), luckyComposerActions(active = false))
    }

    @Test
    fun collectingLuckyOffersViewSubmitCancel() {
        assertEquals(
            listOf(
                "/lucky status" to "查看",
                "/lucky off" to "提交",
                "/lucky cancel" to "放弃",
            ),
            luckyComposerActions(active = true),
        )
    }
}
