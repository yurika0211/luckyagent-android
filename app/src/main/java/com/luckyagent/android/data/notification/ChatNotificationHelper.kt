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

class ChatNotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val delivered = context.getSharedPreferences("chat_notification_dedupe", Context.MODE_PRIVATE)

    @Synchronized
    fun notifyCompleted(sessionId: String, requestId: String?, content: String?) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val dedupeKey = "${sessionId}:${requestId.orEmpty()}"
        if (!requestId.isNullOrBlank() && delivered.getBoolean(dedupeKey, false)) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(context, sessionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = summarize(content)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.luckyagent.android.R.drawable.ic_notification)
            .setContentTitle("LuckyAgent · 对话完成")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val id = "chat:$sessionId:${requestId.orEmpty()}".hashCode()
        manager.notify(id, notification)
        if (!requestId.isNullOrBlank()) delivered.edit().putBoolean(dedupeKey, true).apply()
    }

    fun ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "对话完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "LuckyAgent 对话最终结果"
            })
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

    companion object { const val CHANNEL_ID = "chat_completed" }
}
