package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.SocketState
import com.luckyagent.android.ui.components.StatusChipTone
import com.luckyagent.android.ui.components.statusChipLabel
import com.luckyagent.android.ui.components.statusChipTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCopyTest {
    @Test fun connectionUsesOneSentenceAcrossSurfaces() {
        assertEquals("未连接", connectionStatusLabel(SocketState.Idle))
        assertEquals("未连接", connectionStatusLabel(SocketState.Closed))
        assertEquals("未连接", connectionStatusLabel(SocketState.Error))
        assertEquals("未连接", connectionStatusLabel(SocketState.Connected, addressMissing = true))
        assertEquals("连接中", connectionStatusLabel(SocketState.Connecting))
        assertEquals("连接中", connectionStatusLabel(SocketState.Reconnecting))
        assertEquals("已连接", connectionStatusLabel(SocketState.Connected))
        assertEquals("已连接", connectionStatusLabel(SocketState.Running))
        assertEquals("正在回复", connectionStatusLabel(SocketState.Running, responding = true))
        assertTrue(connectionNeedsAction(SocketState.Error))
        assertFalse(connectionNeedsAction(SocketState.Connected))
    }

    @Test fun activityLineHidesEventNamesAndNamesTheNextStep() {
        assertEquals("已回复", activityStatusLine("complete"))
        assertEquals("已取消。可以重新发送这一条。", activityStatusLine("cancelled"))
        assertEquals("已取消。可以重新发送这一条。", activityStatusLine("cancel requested"))
        assertEquals("已连接", activityStatusLine("health ok"))
        assertEquals("未连接。重新连接后再试。", activityStatusLine("health failed"))
        assertEquals("正在读取这轮对话", activityStatusLine("loading history…"))
        assertEquals("正在回复", activityStatusLine("Next message queued · continuing"))
        assertEquals("正在回复", activityStatusLine("Agent running · history loaded"))
        assertEquals("已完成搜索", activityStatusLine("tool done · 搜索"))
        assertEquals("搜索失败。回到聊天后重试这一条。", activityStatusLine("tool failed · 搜索"))
        assertEquals("回复失败。回到聊天后重试这一条。", activityStatusLine("error · boom"))
        assertEquals("没能读到这轮对话。回到聊天后重试这一条。", activityStatusLine("history: timeout"))
        assertNull(activityStatusLine("  "))
        assertFalse(activityStatusLine("complete")!!.contains("complete"))
        assertFalse(activityStatusLine("tool done · 搜索")!!.contains("tool done"))
        assertEquals("正在思考", activityStatusLine("Thinking through the request"))
        assertEquals("正在执行", activityStatusLine("Working on your request"))
        assertEquals("正在整理工具结果", activityStatusLine("Reviewing tool results"))
        assertEquals("正在分析请求", activityStatusLine("Analyzing the request"))
        assertEquals("正在回复", activityStatusLine("Preparing response"))
    }

    @Test fun failureColorDoesNotDependOnTheChineseWordFailed() {
        assertTrue(activityStatusFailed("history: timeout"))
        assertTrue(activityStatusFailed("rename: no"))
        assertTrue(activityStatusFailed("create session: no"))
        assertTrue(activityStatusFailed("WebSocket event ignored · boom"))
        assertTrue(activityStatusFailed("telegram · start failed"))
        assertFalse(activityStatusFailed("renamed · chat"))
        assertFalse(activityStatusFailed("new session · chat"))
        assertFalse(activityStatusFailed("Thinking through the request"))
    }

    @Test fun statusChipUsesChineseIncludingUnknown() {
        assertEquals("已完成", statusChipLabel("success"))
        assertEquals("已完成", statusChipLabel("completed"))
        assertEquals("失败", statusChipLabel("failure"))
        assertEquals("未知", statusChipLabel("unknown"))
        assertEquals("未知", statusChipLabel("  "))
        assertEquals("进行中", statusChipLabel("running"))
        assertEquals("已停止", statusChipLabel("stopped"))
        assertEquals("已完成", friendlyTaskStatus("completed"))
        assertEquals(statusChipLabel("failed"), friendlyTaskStatus("failed"))
    }

    @Test fun statusChipKeepsColorWhenTheCallerAlreadyTranslated() {
        assertEquals(StatusChipTone.Error, statusChipTone("失败"))
        assertEquals(StatusChipTone.Error, statusChipTone("需要处理"))
        assertEquals(StatusChipTone.Positive, statusChipTone("已完成"))
        assertEquals(StatusChipTone.Positive, statusChipTone("运行中"))
        assertEquals(StatusChipTone.Positive, statusChipTone("running", "进行中"))
        assertEquals(StatusChipTone.Neutral, statusChipTone("stopped", "已停止"))
        assertEquals(StatusChipTone.Error, statusChipTone("failed", friendlyTaskStatus("failed")))
        assertEquals(StatusChipTone.Positive, statusChipTone("completed", friendlyTaskStatus("completed")))
    }

    @Test fun pageTitlesMatchNavigation() {
        assertEquals(
            listOf("聊天", "任务", "后台黑板", "定时任务", "执行轨迹", "技能", "插件", "记忆", "网关", "服务器配置", "基础设置"),
            pageTitlesMatchingNavigation,
        )
    }
}
