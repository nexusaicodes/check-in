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
 * alarm and its permission entirely, matching the choice the session alarms make. The cost is that a
 * nudge fires at the next pass after it becomes eligible rather than on the minute — acceptable for
 * encouragement, which has no deadline.
 */
class NudgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CheckInApplication).container
        return runCatching {
            // Piggy-backed on the pass that already runs: a session whose service was killed gets a
            // chance to recover roughly hourly rather than waiting for the user to open the app. The
            // start may be refused here — background foreground-service starts are restricted, and a
            // worker is not among the exemptions — which is why this is the last of the three revive
            // points rather than the only one. A refusal is logged, not thrown.
            container.sessionWatchdog.reviveIfNeeded(source = "hourly pass")
            container.nudgeDispatcher.runOnce()
            container.engagementLog.prune(
                container.timeSource.nowMillis() - RETENTION_MS,
            )
        }.fold(
            onSuccess = { Result.success() },
            // A failed pass still reports success, because `Result.failure()` is terminal for
            // periodic work — one transient throw would cancel every future pass and silence nudges
            // until the next cold start. Retrying is pointless anyway: the next pass is an hour away
            // and re-evaluates against fresher state.
            onFailure = { Result.success() },
        )
    }

    companion object {
        private const val WORK_NAME = "engagement_nudge_pass"
        private const val INTERVAL_MINUTES = 60L
        private val RETENTION_MS = TimeUnit.DAYS.toMillis(180)

        /**
         * Enqueued unconditionally at startup with [ExistingPeriodicWorkPolicy.KEEP], so it survives
         * reboots and app updates without resetting its schedule on every launch. The pass is cheap,
         * and exits without posting when nudges are switched off.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NudgeWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
