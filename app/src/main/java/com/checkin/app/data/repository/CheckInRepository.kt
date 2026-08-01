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
    private val targetSchedule: () -> List<TargetSchedule.Entry> = { emptyList() },
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

    /**
     * Opens a session, or returns the one already open. Two gated paths reach a check-in — the
     * Check-In screen and a nudge tap — and either can resolve while the other's gate is still on
     * screen, so the one-open-session invariant is enforced here rather than at each call site. A
     * second open row would never be closed (`getActiveSession()` returns one) and its hours would
     * never reach `duration`.
     */
    suspend fun checkIn(): CheckInSession {
        dao.getActiveSession()?.let { return it }
        val session = CheckInSession(
            startedAt = timeSource.nowMillis(),
            dateKey = timeSource.today().format(dateFormatter),
        )
        return session.copy(id = dao.insertSession(session))
    }

    suspend fun checkOut(sessionId: Long) = checkOutAt(sessionId, timeSource.nowMillis())

    /**
     * Closes [sessionId] stamped at [atMillis] rather than now.
     *
     * The day-boundary alarm needs this: it is inexact and may land hours late, so the instant it
     * fires is not the instant the session ended. Duration is floored at zero — a stop before the
     * start means a changed system clock or a corrupt row, and a negative duration would poison
     * every total that sums it.
     */
    suspend fun checkOutAt(sessionId: Long, atMillis: Long) {
        val session = dao.getSessionById(sessionId) ?: return
        dao.updateSession(
            session.copy(
                stoppedAt = atMillis,
                duration = (atMillis - session.startedAt).coerceAtLeast(0L),
            ),
        )
    }

    /** Checks out whatever session is open, for callers that don't hold its id. False if none is. */
    suspend fun checkOutActiveSession(): Boolean {
        val active = dao.getActiveSession() ?: return false
        checkOut(active.id)
        return true
    }

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
            yesterday.format(dateFormatter),
        )
        return DeficitCalculator.computeDeficit(summaries, startDate, yesterday)
    }

    fun sessionsForDateFlow(dateKey: String): Flow<List<CheckInSession>> = dao.getSessionsByDateFlow(dateKey)

    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> = dao.getSessionsByDate(dateKey)

    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
        dao.getDailyAggregates(startDate, endDate)

    suspend fun getAllDateKeys(): List<String> = dao.getAllDateKeys()

    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> =
        dao.getSessionsByDateRange(startDate, endDate)
}
