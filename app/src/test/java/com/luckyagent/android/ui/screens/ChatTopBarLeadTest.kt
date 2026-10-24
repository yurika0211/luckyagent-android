package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.SocketState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTopBarLeadTest {
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
        assertEquals(false, showsLuckyComposer("standalone"))
        assertEquals(true, showsLuckyComposer("runtime"))
        assertEquals(true, showsLuckyComposer(""))
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
}
