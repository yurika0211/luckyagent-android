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
    fun collectingLabelNamesTheCount() {
        assertEquals("正在收集 · 已 3 条", luckyCollectingLabel(3))
        assertEquals("正在收集 · 已 0 条", luckyCollectingLabel(0))
        assertEquals("正在收集 · 已 0 条", luckyCollectingLabel(-1))
    }
}
