package com.checkin.app.data.repository

import com.checkin.app.data.DeficitCalculator
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.local.DailySummary
import com.checkin.app.data.local.TargetSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * @param targetSchedule supplies the date-ordered target log so each day is classified against the
 *   target in effect on that date. Defaults to empty (constant [TargetSchedule.DEFAULT_TARGET_HOURS]).
 */
class CheckInRepository(
    private val dao: CheckInSessionDao,
    private val timeSource: TimeSource = SystemTimeSource,
    private val targetSchedule: () -> List<TargetSchedule.Entry> = { emptyList() }
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

    suspend fun checkIn(): CheckInSession {
        val session = CheckInSession(
            startedAt = timeSource.nowMillis(),
            dateKey = timeSource.today().format(dateFormatter)
        )
        return session.copy(id = dao.insertSession(session))
    }

    suspend fun checkOut(sessionId: Long) {
        val session = dao.getSessionById(sessionId) ?: return
        val now = timeSource.nowMillis()
        // Fold any still-open pause into the total, then net all paused time out of the wall-clock span.
        val totalPaused = session.pausedMs + openPauseGap(session, now)
        dao.updateSession(
            session.copy(
                stoppedAt = now,
                duration = (now - session.startedAt - totalPaused).coerceAtLeast(0L),
                pausedMs = totalPaused,
                pauseStartedAt = null
            )
        )
    }

    /** Gated check-out initiated off the Check-In screen (notification action). Returns false if nothing is active. */
    suspend fun checkOutActiveSession(): Boolean {
        val active = dao.getActiveSession() ?: return false
        checkOut(active.id)
        return true
    }

    /**
     * Opens a pause window on the active session at [atMillis] — a presence check fired and is not yet
     * acknowledged, so the clock stops accruing time. No-op if already paused or nothing is active.
     */
    suspend fun beginPause(atMillis: Long = timeSource.nowMillis()) {
        val active = dao.getActiveSession() ?: return
        if (active.pauseStartedAt != null) return
        dao.updateSession(active.copy(pauseStartedAt = atMillis))
    }

    /** Closes an open pause window, folding the unverified gap into paused time. No-op if not paused. */
    suspend fun resumeFromPause() {
        val active = dao.getActiveSession() ?: return
        val pauseStart = active.pauseStartedAt ?: return
        dao.updateSession(
            active.copy(
                pausedMs = active.pausedMs + (timeSource.nowMillis() - pauseStart).coerceAtLeast(0L),
                pauseStartedAt = null
            )
        )
    }

    private fun openPauseGap(session: CheckInSession, now: Long): Long =
        session.pauseStartedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    fun activeSessionFlow(): Flow<CheckInSession?> = dao.getActiveSessionFlow()

    fun dailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        dao.getDailyAggregatesFlow(startDate, endDate)

    suspend fun getDailySummaries(startDate: String, endDate: String): Map<String, DailySummary> =
        summariesFrom(dao.getDailyAggregates(startDate, endDate))

    /** Pure mapping of aggregates → summaries, each classified against the target in effect that day. */
    fun summariesFrom(aggregates: List<DailyAggregate>): Map<String, DailySummary> {
        val schedule = targetSchedule()
        return aggregates.associate { aggregate ->
            val date = LocalDate.parse(aggregate.dateKey, dateFormatter)
            val targetMs = TargetSchedule.effectiveTargetMs(schedule, date)
            aggregate.dateKey to DailySummary.classify(aggregate, targetMs)
        }
    }

    /** Cumulative leave deficit from [startDate] up to yesterday (inclusive). */
    suspend fun calculateDeficit(startDate: LocalDate): Double {
        val yesterday = timeSource.today().minusDays(1)
        if (startDate.isAfter(yesterday)) return 0.0
        val summaries = getDailySummaries(
            startDate.format(dateFormatter),
            yesterday.format(dateFormatter)
        )
        return DeficitCalculator.computeDeficit(summaries, startDate, yesterday)
    }

    fun sessionsForDateFlow(dateKey: String): Flow<List<CheckInSession>> =
        dao.getSessionsByDateFlow(dateKey)

    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> =
        dao.getSessionsByDate(dateKey)

    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
        dao.getDailyAggregates(startDate, endDate)

    suspend fun getAllDateKeys(): List<String> = dao.getAllDateKeys()

    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> =
        dao.getSessionsByDateRange(startDate, endDate)
}
