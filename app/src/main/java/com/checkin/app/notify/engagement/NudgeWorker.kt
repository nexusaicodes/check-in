package com.checkin.app.notify.engagement

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.checkin.app.CheckInApplication
import java.util.concurrent.TimeUnit

/**
 * Periodic evaluation pass. Waking hourly and deciding "not yet" is deliberate: it avoids an exact
 * alarm (and its permission) entirely, and matches the existing choice made for the presence-check
 * reminder. The cost is that a nudge fires at the next pass after it becomes eligible rather than on
 * the minute — acceptable for encouragement, which has no deadline.
 */
class NudgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CheckInApplication).container
        return runCatching {
            container.nudgeDispatcher.runOnce()
            container.engagementLog.prune(
                container.timeSource.nowMillis() - RETENTION_MS
            )
        }.fold(
            // Retrying a missed pass is pointless — the next one is an hour away and will re-evaluate
            // against fresher state anyway.
            onSuccess = { Result.success() },
            onFailure = { Result.failure() }
        )
    }

    companion object {
        private const val WORK_NAME = "engagement_nudge_pass"
        private const val INTERVAL_MINUTES = 60L
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(180)

        /**
         * Enqueued unconditionally at startup with [ExistingPeriodicWorkPolicy.KEEP], so it survives
         * reboots and app updates without resetting its schedule on every launch. The pass is cheap
         * and exits immediately when nudges are disabled, which is the default.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
