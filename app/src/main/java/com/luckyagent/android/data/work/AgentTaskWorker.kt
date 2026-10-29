package com.luckyagent.android.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.luckyagent.android.LuckyAgentApp
import com.luckyagent.android.MainActivity
import com.luckyagent.android.R
import com.luckyagent.android.data.api.TaskOrigin
import com.luckyagent.android.data.api.executionSession
import com.luckyagent.android.data.api.toTaskSummary
import com.luckyagent.android.data.notification.RuntimeNoticeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Tracks a remote task while Android may reclaim the app process.
 * The Go Agent remains on the server; this worker only polls and reports state.
 */
class AgentTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)?.trim().orEmpty()
        if (taskId.isBlank()) return Result.failure()
        val executionSessionId = inputData.getString(KEY_SESSION_ID)?.trim().orEmpty()
        val autonomy = inputData.getString(KEY_ORIGIN) == AgentTaskWork.ORIGIN_AUTONOMY
        val app = applicationContext as? LuckyAgentApp ?: return Result.failure()
        val notificationId = "watch:$autonomy:$taskId".hashCode()
        val initial = notification("LuckyAgent · Task running", "Watching task $taskId", taskId, executionSessionId, autonomy)
        setForeground(foregroundInfo(notificationId, initial))

        repeat(MAX_POLLS) {
            if (isStopped) return Result.failure()
            val result = if (autonomy) {
                app.container.api.getAutonomyTask(taskId).map { response ->
                    val task = response.task
                    TaskWatchState(
                        status = task.state,
                        title = task.title.ifBlank { task.id },
                        detail = task.blockReason?.takeIf(String::isNotBlank)
                            ?: task.error?.takeIf(String::isNotBlank)
                            ?: task.resultPreview.orEmpty(),
                        executionSessionId = task.executionSession() ?: executionSessionId,
                        terminal = task.state.equals("done", true) || task.state.equals("blocked", true),
                        successful = task.state.equals("done", true),
                        noticeKind = RuntimeNoticeKind.Background,
                    )
                }
            } else {
                app.container.api.getTaskRecord(taskId).map { task ->
                    val progress = task.metadata["progress"]?.toDoubleOrNull()
                        ?.times(100)?.toInt()?.coerceIn(0, 100)
                    TaskWatchState(
                        status = task.status,
                        title = task.description.ifBlank { taskId },
                        detail = progress?.let { "$it%" }.orEmpty(),
                        executionSessionId = task.toTaskSummary().executionSession() ?: executionSessionId,
                        terminal = task.status.lowercase() in TERMINAL,
                        successful = task.status.equals("completed", true),
                        noticeKind = if (task.source == "autonomy") RuntimeNoticeKind.Background else RuntimeNoticeKind.Subagent,
                    )
                }
            }
            // A failed request should use WorkManager's retry backoff. Returning
            // from repeat here used to hammer the server hundreds of times.
            val task = result.getOrElse {
                update(
                    notificationId,
                    "LuckyAgent · Task unavailable",
                    it.message ?: "Could not read task status",
                    taskId,
                    executionSessionId,
                    autonomy,
                )
                return Result.retry()
            }
            val title = "LuckyAgent · ${friendlyStatus(task.status.lowercase())}"
            val body = listOf(task.title, task.detail).filter(String::isNotBlank).joinToString(" · ")
            if (task.terminal) {
                // WorkManager removes its foreground notification on completion.
                // Post the durable result through the normal task channel first.
                app.container.notifications.notifyTask(
                    task.noticeKind,
                    taskId,
                    title,
                    body,
                    task.executionSessionId,
                    terminal = true,
                    background = autonomy,
                )
                return if (task.successful) Result.success() else Result.failure()
            }
            update(
                notificationId,
                title,
                body,
                taskId,
                task.executionSessionId,
                autonomy,
            )
            delay(POLL_INTERVAL_MS)
        }
        return Result.retry()
    }

    private fun update(id: Int, title: String, body: String, taskId: String, sessionId: String, autonomy: Boolean) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification(title, body, taskId, sessionId, autonomy))
    }

    private fun foregroundInfo(notificationId: Int, notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }

    private fun notification(title: String, body: String, taskId: String, sessionId: String, autonomy: Boolean): android.app.Notification {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openTaskIntent(taskId, sessionId, autonomy))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // The autonomy queue does not expose a cancel endpoint. Keep its
        // notification actionable through the task/session deep link.
        if (!autonomy) builder.addAction(R.drawable.ic_notification, "Stop task", cancelIntent(taskId))
        return builder.build()
    }

    private fun openTaskIntent(taskId: String, sessionId: String, autonomy: Boolean): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DESTINATION, if (autonomy) "background" else "tasks")
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            if (sessionId.isNotBlank()) putExtra(MainActivity.EXTRA_SESSION_ID, sessionId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            applicationContext,
            ("open:$autonomy:$taskId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(taskId: String): PendingIntent {
        val intent = Intent(applicationContext, AgentTaskCancelReceiver::class.java).apply {
            putExtra(AgentTaskCancelReceiver.EXTRA_WORK_ID, id.toString())
            putExtra(AgentTaskCancelReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            applicationContext,
            ("cancel:$taskId").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Agent tasks", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    private fun friendlyStatus(status: String): String = when (status) {
        "pending", "queued", "ready" -> "Queued"
        "running", "in_progress" -> "In progress"
        "blocked" -> "Needs attention"
        "completed", "done" -> "Completed"
        "failed", "timeout" -> "Failed"
        "cancelled" -> "Cancelled"
        else -> "Task update"
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_SESSION_ID = "execution_session_id"
        const val KEY_ORIGIN = "task_origin"
        const val CHANNEL_ID = "agent_task_work"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val MAX_POLLS = 720
        private val TERMINAL = setOf("completed", "failed", "timeout", "cancelled")
    }

    init {
        ensureChannel()
    }

    private data class TaskWatchState(
        val status: String,
        val title: String,
        val detail: String,
        val executionSessionId: String,
        val terminal: Boolean,
        val successful: Boolean,
        val noticeKind: RuntimeNoticeKind,
    )
}

class AgentTaskCancelReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra(EXTRA_WORK_ID).orEmpty()
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as? LuckyAgentApp
                if (app != null && taskId.isNotBlank()) {
                    // Do not hide a failed remote cancellation behind a local
                    // WorkManager cancellation; otherwise the server keeps
                    // running with no phone-side watcher or feedback path.
                    val remote = app.container.api.cancelTask(taskId, TaskOrigin.Unified)
                    if (remote.isFailure) return@runCatching
                }
                raw.takeIf { it.isNotBlank() }?.let { value ->
                    WorkManager.getInstance(context.applicationContext).cancelWorkById(UUID.fromString(value))
                }
            }
            pending.finish()
        }
    }

    companion object {
        const val EXTRA_WORK_ID = "work_id"
        const val EXTRA_TASK_ID = "task_id"
    }
}
