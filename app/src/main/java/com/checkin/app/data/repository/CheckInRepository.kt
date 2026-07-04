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

    suspend fun punchIn(): Long {
        val session = CheckInSession(
            startedAt = timeSource.nowMillis(),
            dateKey = timeSource.today().format(dateFormatter)
        )
        return dao.insertSession(session)
    }

    suspend fun punchOut(sessionId: Long) {
        val session = dao.getSessionById(sessionId) ?: return
        val now = timeSource.nowMillis()
        dao.updateSession(
            session.copy(stoppedAt = now, duration = now - session.startedAt)
        )
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    suspend fun getDailySummaries(startDate: String, endDate: String): Map<String, DailySummary> {
        val schedule = targetSchedule()
        return dao.getDailyAggregates(startDate, endDate).associate { aggregate ->
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

    fun getTodaySessionsFlow(dateKey: String): Flow<List<CheckInSession>> =
        dao.getSessionsByDateFlow(dateKey)

    fun getTodayTotalDurationFlow(dateKey: String): Flow<Long> =
        dao.getTotalDurationForDateFlow(dateKey)

    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> =
        dao.getSessionsByDate(dateKey)

    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
        dao.getDailyAggregates(startDate, endDate)

    suspend fun getAllDateKeys(): List<String> = dao.getAllDateKeys()

    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> =
        dao.getSessionsByDateRange(startDate, endDate)
}
