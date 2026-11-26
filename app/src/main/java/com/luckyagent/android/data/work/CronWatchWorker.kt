package com.luckyagent.android.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luckyagent.android.LuckyAgentApp
import com.luckyagent.android.data.api.CronDeviceRegistration
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

        val pending = app.container.api.pendingCronResults(deviceId, limit = 1).getOrElse { return Result.retry() }
        for (item in pending.items) {
            val failed = item.execution.status.equals("failed", ignoreCase = true)
            val detail = item.execution.error.ifBlank { item.execution.result }
            val name = item.task.name.ifBlank { item.task.id }
            val posted = app.container.notifications.notifyCron(
                sessionId = "",
                eventId = item.execution.id,
                content = RuntimeNoticePolicy.cronBody(name, failed, detail),
                failed = failed,
                taskId = item.task.id,
                executionId = item.execution.id,
            )
            if (posted) {
                if (app.container.api.ackCronDelivery(item.delivery.id, deviceId).isFailure) return Result.retry()
                prefs.edit()
                    .putString(KEY_CURSOR_AT, item.execution.scheduledAt)
                    .putString(KEY_CURSOR_EXECUTION_ID, item.execution.id)
                    .apply()
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
