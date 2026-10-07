package com.luckyagent.android.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.luckyagent.android.MainActivity

enum class RuntimeNoticeKind(val channelId: String, val channelName: String, val titlePrefix: String) {
    Chat("chat_completed", "对话完成", "对话完成"),
    Cron("cron_message", "定时消息", "定时消息"),
    Subagent("subagent_progress", "子代理进度", "子代理"),
    Background("background_progress", "后台任务", "后台任务"),
}

class ChatNotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val delivered = context.getSharedPreferences("chat_notification_dedupe", Context.MODE_PRIVATE)

    @Synchronized
    fun notifyCompleted(sessionId: String, requestId: String?, content: String?) {
        post(
            kind = RuntimeNoticeKind.Chat,
            dedupeKey = "chat:${sessionId}:${requestId.orEmpty()}",
            notificationId = "chat:$sessionId:${requestId.orEmpty()}",
            title = "LuckyAgent · 对话完成",
            body = summarize(content),
            sessionId = sessionId,
            destination = "chat",
            ongoing = false,
            remember = !requestId.isNullOrBlank(),
        )
    }

    @Synchronized
    fun notifyCron(sessionId: String, eventId: String?, content: String?) {
        post(
            kind = RuntimeNoticeKind.Cron,
            dedupeKey = "cron:${sessionId}:${eventId.orEmpty()}:${content.orEmpty().hashCode()}",
            notificationId = "cron:$sessionId:${eventId.orEmpty()}",
            title = "LuckyAgent · 定时消息",
            body = summarize(content),
            sessionId = sessionId,
            destination = "chat",
            ongoing = false,
            remember = true,
        )
    }

    @Synchronized
    fun notifyTask(
        kind: RuntimeNoticeKind,
        taskId: String,
        title: String,
        body: String,
        sessionId: String = "",
        terminal: Boolean = false,
    ) {
        post(
            kind = kind,
            dedupeKey = "",
            notificationId = "${kind.channelId}:$taskId",
            title = title.ifBlank { "LuckyAgent · ${kind.titlePrefix}" },
            body = body.ifBlank { kind.titlePrefix },
            sessionId = sessionId,
            destination = if (kind == RuntimeNoticeKind.Background) "background" else "tasks",
            ongoing = !terminal,
            remember = false,
        )
    }

    fun cancelTask(kind: RuntimeNoticeKind, taskId: String) {
        manager.cancel("${kind.channelId}:$taskId".hashCode())
    }

    private fun post(
        kind: RuntimeNoticeKind,
        dedupeKey: String,
        notificationId: String,
        title: String,
        body: String,
        sessionId: String,
        destination: String,
        ongoing: Boolean,
        remember: Boolean,
    ) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (remember && dedupeKey.isNotBlank() && delivered.getBoolean(dedupeKey, false)) return
        ensureChannel(kind)
        val intent = Intent(context, MainActivity::class.java).apply {
            if (sessionId.isNotBlank()) putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(MainActivity.EXTRA_DESTINATION, destination)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(com.luckyagent.android.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(ongoing)
            .setCategory(if (kind == RuntimeNoticeKind.Chat || kind == RuntimeNoticeKind.Cron) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(notificationId.hashCode(), notification)
        if (remember && dedupeKey.isNotBlank()) delivered.edit().putBoolean(dedupeKey, true).apply()
    }

    fun ensureChannel() {
        RuntimeNoticeKind.entries.forEach(::ensureChannel)
    }

    private fun ensureChannel(kind: RuntimeNoticeKind) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(kind.channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(kind.channelId, kind.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "LuckyAgent ${kind.channelName}"
                },
            )
        }
    }

    private fun summarize(value: String?): String {
        val cleaned = value.orEmpty()
            .replace(Regex("```[\\s\\S]*?```"), "[代码]")
            .replace(Regex("[`*_>#|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when {
            cleaned.isBlank() -> "回复已完成"
            cleaned.length > 110 -> cleaned.take(110) + "…"
            else -> cleaned
        }
    }

    companion object {
        const val CHANNEL_ID = "chat_completed"
    }
}
