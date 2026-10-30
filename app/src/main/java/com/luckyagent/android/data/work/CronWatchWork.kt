package com.luckyagent.android.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.luckyagent.android.data.api.CronJob
import com.luckyagent.android.data.notification.CRON_WATCH_FALLBACK_MS
import com.luckyagent.android.data.notification.cronWatchDelayMs
import java.util.concurrent.TimeUnit

/** One delayed check for cron runs the phone may miss after it leaves the foreground. */
object CronWatchWork {
    const val UNIQUE_NAME = "luckyagent-cron-watch"

    fun enqueue(context: Context, jobs: List<CronJob>, nowMs: Long = System.currentTimeMillis()) {
        val next = jobs.mapNotNull { it.nextRun }.minByOrNull { cronWatchDelayMs(it, nowMs) }
        val delay = cronWatchDelayMs(next, nowMs).coerceIn(CRON_WATCH_MIN_MS, CRON_WATCH_FALLBACK_MS)
        val request = OneTimeWorkRequestBuilder<CronWatchWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private const val CRON_WATCH_MIN_MS = 15_000L
}
