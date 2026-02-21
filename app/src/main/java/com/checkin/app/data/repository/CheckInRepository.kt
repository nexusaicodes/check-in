package com.checkin.app.data.repository

import com.checkin.app.data.local.AttendanceStatus
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.local.DailySummary
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CheckInRepository(private val dao: CheckInSessionDao) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

    suspend fun punchIn(selfiePath: String): Long {
        val now = System.currentTimeMillis()
        val dateKey = LocalDate.now().format(dateFormatter)
        val session = CheckInSession(
            startedAt = now,
            dateKey = dateKey,
            punchInSelfie = selfiePath
        )
        return dao.insertSession(session)
    }

    suspend fun punchOut(sessionId: Long, selfiePath: String) {
        val session = dao.getSessionById(sessionId) ?: return
        val now = System.currentTimeMillis()
        val duration = now - session.startedAt
        dao.updateSession(
            session.copy(
                stoppedAt = now,
                duration = duration,
                punchOutSelfie = selfiePath
            )
        )
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    suspend fun getDailySummaries(startDate: String, endDate: String): Map<String, DailySummary> {
        val aggregates = dao.getDailyAggregates(startDate, endDate)
        return aggregates.associate { it.dateKey to DailySummary.fromAggregate(it) }
    }

    /**
     * Calculates cumulative leave deficit from [startDate] up to yesterday (inclusive).
     * Each day: missing/no-sessions => 1.0, half-day => 0.5, present => 0.0
     */
    suspend fun calculateDeficit(startDate: LocalDate): Double {
        val yesterday = LocalDate.now().minusDays(1)
        if (startDate.isAfter(yesterday)) return 0.0

        val startStr = startDate.format(dateFormatter)
        val endStr = yesterday.format(dateFormatter)
        val summaries = getDailySummaries(startStr, endStr)

        var deficit = 0.0
        var current = startDate
        while (!current.isAfter(yesterday)) {
            val key = current.format(dateFormatter)
            val summary = summaries[key]
            deficit += when {
                summary == null -> 1.0 // no sessions at all
                summary.status == AttendanceStatus.FULL_DAY_LEAVE -> 1.0
                summary.status == AttendanceStatus.HALF_DAY_LEAVE -> 0.5
                else -> 0.0
            }
            current = current.plusDays(1)
        }
        return deficit
    }

    fun getTodaySessionsFlow(dateKey: String): Flow<List<CheckInSession>> {
        return dao.getSessionsByDateFlow(dateKey)
    }

    fun getTodayTotalDurationFlow(dateKey: String): Flow<Long> {
        return dao.getTotalDurationForDateFlow(dateKey)
    }

    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> {
        return dao.getSessionsByDate(dateKey)
    }

    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> {
        return dao.getDailyAggregates(startDate, endDate)
    }

    suspend fun getAllDateKeys(): List<String> {
        return dao.getAllDateKeys()
    }

    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> {
        return dao.getSessionsByDateRange(startDate, endDate)
    }
}
