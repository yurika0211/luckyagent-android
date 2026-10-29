package com.luckyagent.android.data.notification

/**
 * Which runtime changes are worth a phone alert.
 *
 * Progress ticks stay quiet unless the user opts in. A finished, failed,
 * blocked, or errored task is the default alert. The same cron run is
 * reported once even when later polls rewrite its error text.
 */
internal object RuntimeNoticePolicy {
    fun taskWorthy(
        status: String,
        blocked: Boolean = false,
        includeProgress: Boolean = false,
    ): Boolean {
        if (blocked || status.needsAttention()) return true
        if (status.isQuietTerminal()) return true
        return includeProgress && status.isActiveProgress()
    }

    fun backgroundWorthy(
        state: String,
        includeProgress: Boolean = false,
    ): Boolean = taskWorthy(state, includeProgress = includeProgress)

    fun eventWorthy(
        status: String,
        type: String,
        includeProgress: Boolean,
    ): Boolean {
        val terminal = status.isQuietTerminal() ||
            type.endsWith("completed") ||
            type.endsWith("failed") ||
            type.endsWith("cancelled")
        if (terminal || status.needsAttention()) return true
        return includeProgress
    }

    /**
     * One alert per cron execution. [runKey] is that execution, not the
     * polling fingerprint, so a rewritten error does not alert again.
     */
    fun cronRunWorthy(previousRunKey: String?, runKey: String): Boolean =
        previousRunKey != null && previousRunKey != runKey && runKey.isNotBlank()

    fun taskHeadline(kindLabel: String, name: String, status: String): String {
        val label = status.outcomeLabel()
        val who = name.ifBlank { kindLabel }
        return "LuckyAgent · $label · $who"
    }

    fun taskBody(status: String, detail: String, progress: Double? = null, children: String? = null): String {
        val outcome = status.outcomeLabel()
        val extra = detail.trim().takeIf { it.isNotBlank() && !it.equals(status, true) }
        return buildList {
            add(outcome)
            progress?.takeIf { it > 0.0 && status.isActiveProgress() }?.let {
                add("${(it * 100).toInt().coerceIn(0, 100)}%")
            }
            children?.takeIf { it.isNotBlank() }?.let { add(it) }
            extra?.let { add(it.take(80)) }
        }.joinToString(" · ")
    }

    fun cronHeadline(name: String, failed: Boolean): String =
        "LuckyAgent · ${if (failed) "定时失败" else "定时完成"} · ${name.ifBlank { "定时任务" }}"

    fun cronBody(name: String, failed: Boolean, detail: String): String {
        val what = name.ifBlank { "定时任务" }
        val reason = detail.trim()
        return when {
            failed && reason.isNotBlank() -> "$what 这次没跑成：$reason".take(120)
            failed -> "$what 这次没跑成"
            reason.isNotBlank() -> "$what 跑完了。$reason".take(120)
            else -> "$what 跑完了"
        }
    }

    private fun String.isQuietTerminal(): Boolean = when (lowercase()) {
        "completed", "done", "succeeded", "success", "failed", "cancelled", "canceled", "timeout" -> true
        else -> false
    }

    private fun String.needsAttention(): Boolean = when (lowercase()) {
        "blocked", "error" -> true
        else -> false
    }

    private fun String.isActiveProgress(): Boolean = when (lowercase()) {
        "pending", "queued", "ready", "running", "in_progress", "in-progress" -> true
        else -> false
    }

    private fun String.outcomeLabel(): String = when (lowercase()) {
        "completed", "done", "succeeded", "success" -> "完成了"
        "failed", "timeout" -> "失败了"
        "cancelled", "canceled" -> "取消了"
        "blocked" -> "卡住了"
        "error" -> "出错了"
        "pending", "queued", "ready" -> "还在排队"
        "running", "in_progress", "in-progress" -> "还在进行"
        else -> ifBlank { "有变化" }
    }
}
