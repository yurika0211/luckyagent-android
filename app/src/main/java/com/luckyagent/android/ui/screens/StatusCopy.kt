package com.luckyagent.android.ui.screens

import com.luckyagent.android.data.api.SocketState

/**
 * One Chinese sentence for a connection. Navigation, settings, and the chat
 * activity line share these words so a state is not described three ways.
 */
internal fun connectionStatusLabel(
    socket: SocketState,
    responding: Boolean = false,
    addressMissing: Boolean = false,
): String = when {
    addressMissing -> "未连接"
    responding -> "正在回复"
    socket == SocketState.Error || socket == SocketState.Idle || socket == SocketState.Closed -> "未连接"
    socket == SocketState.Connecting || socket == SocketState.Reconnecting -> "连接中"
    socket == SocketState.Connected || socket == SocketState.Running -> "已连接"
    else -> "未连接"
}

/** Titles that navigation and the matching page must share. */
internal val pageTitlesMatchingNavigation = listOf(
    "聊天",
    "任务",
    "后台黑板",
    "定时任务",
    "执行轨迹",
    "技能",
    "插件",
    "记忆",
    "网关",
    "服务器配置",
    "基础设置",
)

internal fun connectionNeedsAction(socket: SocketState, addressMissing: Boolean = false): Boolean =
    addressMissing || socket == SocketState.Error || socket == SocketState.Idle || socket == SocketState.Closed

/** Activity row for a failed connection. The next step is named in the sentence. */
internal fun connectionFailureLine(addressMissing: Boolean): String =
    if (addressMissing) "未连接。打开设置填写服务地址。" else "未连接。重新连接后再试。"

/**
 * Turns runtime event names into a sentence the activity row can show.
 * Raw names such as complete, cancelled, and tool done never stay as the line.
 */
internal fun activityStatusLine(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (text) {
        "Preparing response" -> "正在回复"
        "Thinking through the request" -> "正在思考"
        "Working on your request" -> "正在执行"
        "Reviewing tool results" -> "正在整理工具结果"
        "Analyzing the request" -> "正在分析请求"
        "Request failed" -> "请求失败"
        "complete", "completed" -> "已回复"
        "cancelled", "canceled", "cancel requested" -> "已取消。可以重新发送这一条。"
        "health ok" -> "已连接"
        "health failed" -> "未连接。重新连接后再试。"
        "loading history…", "loading history" -> "正在读取这轮对话"
        "Next message queued · continuing" -> "正在回复"
        "Agent running · history loaded" -> "正在回复"
        else -> mappedActivityLine(text)
    }
}

private fun mappedActivityLine(text: String): String {
    toolActivity(text)?.let { return it }
    when {
        text.startsWith("history:") -> return "没能读到这轮对话。回到聊天后重试这一条。"
        text.startsWith("error ·") -> return "回复失败。回到聊天后重试这一条。"
        text.startsWith("WebSocket event ignored") -> return "没能读到这条状态。重新连接后再试。"
        text.startsWith("skills ·") -> return "已读到技能"
        text.startsWith("gateways ·") -> return "已读到渠道"
        text.startsWith("trajectory ·") -> return "已读到本轮轨迹"
        text.endsWith("· started") -> return "渠道已启动"
        text.endsWith("· stopped") -> return "渠道已停止"
        text.endsWith("· start failed") -> return "渠道没能启动。重试这一条。"
        text.endsWith("· stop failed") -> return "渠道没能停止。重试这一条。"
        text.startsWith("new session ·") -> return "已新建会话"
        text.startsWith("renamed ·") -> return "已重命名"
        text.startsWith("rename:") -> return "没能重命名。重试这一条。"
        text.startsWith("create session:") -> return "没能新建会话。重试这一条。"
        text.startsWith("deleted ·") -> return "已删除会话"
        text.startsWith("delete:") -> return "没能删除会话。重试这一条。"
        text.startsWith("pinned ·") -> return "已置顶"
        text.startsWith("unpinned ·") -> return "已取消置顶"
        text.startsWith("pin:") -> return "没能改置顶。重试这一条。"
        text.startsWith("project cleared ·") -> return "已清除项目分类"
        text.startsWith("project ·") -> return "已保存项目分类"
        text.startsWith("project:") -> return "没能保存项目分类。重试这一条。"
        text.startsWith("Running /") -> return "正在执行 /${text.removePrefix("Running /")}"
        text.endsWith(" completed") && text.startsWith("/") -> return "已完成 ${text.removeSuffix(" completed")}"
        text.endsWith(" failed") && text.startsWith("/") -> return "${text.removeSuffix(" failed")} 失败。重试这一条。"
        text == "API base URL is required" -> return "未连接。打开设置填写服务地址。"
        text == "Keep at least one runtime endpoint" -> return "至少保留一台电脑。"
        text.startsWith("loaded ") && text.endsWith(" messages") -> return "已读到这轮对话"
    }
    return text
}

/**
 * Whether an activity sentence is a failure. Callers color from this, not from
 * a substring of the Chinese copy.
 */
internal fun activityStatusFailed(raw: String?, shown: String? = activityStatusLine(raw)): Boolean {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() && shown.isNullOrBlank()) return false
    if (
        text == "health failed" ||
        text == "Request failed" ||
        text.startsWith("history:") ||
        text.startsWith("error ·") ||
        text.startsWith("WebSocket event ignored") ||
        text.startsWith("rename:") ||
        text.startsWith("create session:") ||
        text.startsWith("delete:") ||
        text.startsWith("pin:") ||
        text.startsWith("project:") ||
        text.endsWith("· start failed") ||
        text.endsWith("· stop failed") ||
        text.startsWith("tool failed ·") ||
        (text.startsWith("/") && text.endsWith(" failed"))
    ) {
        return true
    }
    val line = shown.orEmpty()
    return line.contains("失败") || line.contains("未连接") || line.contains("没能")
}

private fun toolActivity(text: String): String? {
    val name = text.substringAfter(" · ", "").trim()
    return when {
        text.startsWith("tool done ·") -> "已完成${name.ifBlank { "这一步" }}"
        text.startsWith("tool failed ·") -> "${name.ifBlank { "这一步" }}失败。回到聊天后重试这一条。"
        text.startsWith("tool ·") -> "正在使用${name.ifBlank { "工具" }}"
        else -> null
    }
}
