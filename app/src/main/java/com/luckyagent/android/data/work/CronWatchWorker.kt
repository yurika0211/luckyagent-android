package com.luckyagent.android.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luckyagent.android.LuckyAgentApp
import com.luckyagent.android.data.api.CronDeviceRegistration
import com.luckyagent.android.data.api.CronPendingItem
import com.luckyagent.android.data.notification.RuntimeNoticePolicy
import java.time.Instant
import java.util.UUID

/**
 * Polls account-owned cron deliveries after the app leaves the foreground.
 * FCM can wake this same path later; polling remains the required fallback.
 */
class CronWatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? LuckyAgentApp ?: return Result.failure()
        if (!app.container.settingsRepository.snapshot().notifyOnCron) return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
        app.container.notifications.ensureChannel()
        val registered = prefs.getBoolean(KEY_REGISTERED, false)
        val capability = app.container.notifications.cronNotificationCapability()
        val cursorAt = prefs.getString(KEY_CURSOR_AT, null)
        val cursorExecutionId = prefs.getString(KEY_CURSOR_EXECUTION_ID, null)
        val registration = CronDeviceRegistration(
            id = deviceId,
            channel = "android",
            notificationsEnabled = capability.notificationsEnabled,
            channelEnabled = capability.channelEnabled,
            cursorAt = cursorAt ?: if (registered) null else Instant.now().toString(),
            cursorExecutionId = cursorExecutionId,
        )
        val serverDevice = app.container.api.registerCronDevice(registration).getOrElse { return Result.retry() }
        if (!registered) prefs.edit().putBoolean(KEY_REGISTERED, true).apply()
        if (!cursorAt.isNullOrBlank() && !cursorExecutionId.isNullOrBlank()) {
            // Keep the local tuple for a backend reset or a fresh process. The
            // server remains authoritative when the device already exists.
            prefs.edit()
                .putString(KEY_CURSOR_AT, serverDevice.cursorAt ?: cursorAt)
                .putString(KEY_CURSOR_EXECUTION_ID, serverDevice.cursorExecutionId ?: cursorExecutionId)
                .apply()
        }

        // A disabled permission/channel means no notification was shown. Keep
        // the account delivery pending so a later capability change can show it.
        if (!capability.notificationsEnabled || !capability.channelEnabled) {
            val jobs = app.container.api.listCronJobs().getOrElse { return Result.retry() }.jobs
            CronWatchWork.enqueue(applicationContext, jobs)
            return Result.success()
        }

        // Drain the queue, rather than only acknowledging the newest row. The
        // backend cursor is a (scheduled_at, execution_id) tuple, so several
        // executions can legitimately share the same scheduled_at.
        while (true) {
            val pending = app.container.api.pendingCronResults(deviceId, limit = 10).getOrElse { return Result.retry() }
            if (pending.items.isEmpty()) break
            when (drainCronDeliveries(
                items = pending.items,
                notify = { item ->
                    val failed = item.execution.status.equals("failed", ignoreCase = true)
                    val detail = item.execution.error.ifBlank { item.execution.result }
                    val name = item.task.name.ifBlank { item.task.id }
                    app.container.notifications.notifyCron(
                        sessionId = "",
                        eventId = item.execution.id,
                        content = RuntimeNoticePolicy.cronBody(name, failed, detail),
                        failed = failed,
                        taskId = item.task.id,
                        executionId = item.execution.id,
                    )
                },
                ack = { item -> app.container.api.ackCronDelivery(item.delivery.id, deviceId).isSuccess },
                onAck = { item ->
                    prefs.edit()
                        .putString(KEY_CURSOR_AT, item.execution.scheduledAt)
                        .putString(KEY_CURSOR_EXECUTION_ID, item.execution.id)
                        .apply()
                },
            )) {
                CronDrainOutcome.COMPLETE -> Unit
                CronDrainOutcome.NOTIFICATION_BLOCKED -> break
                CronDrainOutcome.ACK_FAILED -> return Result.retry()
            }
        }
        val jobs = app.container.api.listCronJobs().getOrElse { return Result.retry() }.jobs
        CronWatchWork.enqueue(applicationContext, jobs)
        return Result.success()
    }

    companion object {
        const val PREFS = "cron_watch"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_REGISTERED = "device_registered"
        const val KEY_CURSOR_AT = "cursor_at"
        const val KEY_CURSOR_EXECUTION_ID = "cursor_execution_id"
    }
}

internal enum class CronDrainOutcome {
    COMPLETE,
    NOTIFICATION_BLOCKED,
    ACK_FAILED,
}

/**
 * Processes one pending page from oldest to newest. Advancing a cursor past a
 * newer row before an older row is delivered would make that older row
 * invisible to the next poll after a notification failure.
 */
internal suspend fun drainCronDeliveries(
    items: List<CronPendingItem>,
    notify: suspend (CronPendingItem) -> Boolean,
    ack: suspend (CronPendingItem) -> Boolean,
    onAck: (CronPendingItem) -> Unit,
): CronDrainOutcome {
    val ordered = items.sortedWith(
        compareBy<CronPendingItem> {
            runCatching { Instant.parse(it.execution.scheduledAt) }.getOrElse { Instant.MIN }
        }.thenBy { it.execution.id },
    )
    for (item in ordered) {
        if (!notify(item)) return CronDrainOutcome.NOTIFICATION_BLOCKED
        if (!ack(item)) return CronDrainOutcome.ACK_FAILED
        onAck(item)
    }
    return CronDrainOutcome.COMPLETE
}

/**
 * Newest row on one pending page. The foreground path shows this one and
 * leaves older rows for the background worker. An empty page returns null.
 */
internal fun newestPending(items: List<CronPendingItem>): CronPendingItem? =
    items.maxWithOrNull(
        compareBy<CronPendingItem> {
            runCatching { Instant.parse(it.execution.scheduledAt) }.getOrElse { Instant.MIN }
        }.thenBy { it.execution.id },
    )
